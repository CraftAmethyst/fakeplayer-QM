package io.github.hello09x.fakeplayer.core;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.github.hello09x.devtools.command.CommandModule;
import io.github.hello09x.devtools.core.translation.TranslatorUtils;
import io.github.hello09x.devtools.core.utils.Exceptions;
import io.github.hello09x.devtools.database.DatabaseModule;
import io.github.hello09x.fakeplayer.core.command.CommandRegistry;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.listener.FakeplayerLifecycleListener;
import io.github.hello09x.fakeplayer.core.listener.FakeplayerListener;
import io.github.hello09x.fakeplayer.core.listener.PlayerListener;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerAutofishManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerAutosleepManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerReplenishManager;
import io.github.hello09x.fakeplayer.core.manager.PersistentFakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.WildFakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.invsee.InvseeManager;
import io.github.hello09x.fakeplayer.core.placeholder.FakeplayerPlaceholderExpansion;
import io.github.hello09x.fakeplayer.core.repository.UsedIdRepository;
import io.github.hello09x.fakeplayer.core.translation.FakeplayerTranslationModule;
import io.github.hello09x.fakeplayer.core.util.update.UpdateChecker;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import io.github.hello09x.fakeplayer.core.platform.PlatformScheduler;
import io.github.hello09x.fakeplayer.core.platform.ServerPlatform;

import java.util.concurrent.CompletableFuture;

public final class Main extends JavaPlugin {

    @Getter
    private static Main instance;

    private Injector injector;

    private PlatformScheduler scheduler;

    private io.github.hello09x.fakeplayer.core.platform.ChunkLeases chunkLeases;

    public static @NotNull io.github.hello09x.fakeplayer.core.platform.ChunkLeases getChunkLeases() {
        return instance.chunkLeases;
    }

    public static @NotNull PlatformScheduler getScheduler() {
        return instance.scheduler;
    }

    private long loadAt;

    public static @NotNull Injector getInjector() {
        return instance.injector;
    }

    @Override
    public void onLoad() {
        loadAt = System.currentTimeMillis();
        instance = this;
        scheduler = new PlatformScheduler(this, ServerPlatform.detect());
        chunkLeases = new io.github.hello09x.fakeplayer.core.platform.ChunkLeases(this, scheduler);
    }

    @Override
    public void onEnable() {
        if (scheduler.isFolia()) {
            try {
                io.github.hello09x.fakeplayer.core.platform.FoliaCompatibility.verifyRuntime(
                        io.github.hello09x.fakeplayer.core.util.VersionUtils.getMinecraftVersion(), getClass().getClassLoader());
                io.github.hello09x.fakeplayer.core.platform.FoliaWorldPaths.prepare(getServer());
            } catch (IllegalStateException error) {
                getLogger().log(java.util.logging.Level.SEVERE, "Folia compatibility check failed before manager initialization", error);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        injector = Guice.createInjector(
                new FakeplayerModule(),
                new CommandModule(),
                new DatabaseModule(),
                new FakeplayerTranslationModule(
                        "message/message",
                        TranslatorUtils.getDefaultLocale(Main.getInstance()))
        );

        injector.getInstance(CommandRegistry.class).register();
        {
            var messenger = getServer().getMessenger();
            messenger.registerIncomingPluginChannel(this, "BungeeCord", injector.getInstance(WildFakeplayerManager.class));
            messenger.registerOutgoingPluginChannel(this, "BungeeCord");
        }

        {
            var manager = getServer().getPluginManager();
            manager.registerEvents(injector.getInstance(PlayerListener.class), this);
            manager.registerEvents(injector.getInstance(FakeplayerLifecycleListener.class), this);
            manager.registerEvents(injector.getInstance(FakeplayerListener.class), this);
            manager.registerEvents(injector.getInstance(FakeplayerAutofishManager.class), this);
            manager.registerEvents(injector.getInstance(FakeplayerAutosleepManager.class), this);
            manager.registerEvents(injector.getInstance(FakeplayerReplenishManager.class), this);
            manager.registerEvents(injector.getInstance(InvseeManager.class), this);
            manager.registerEvents(injector.getInstance(io.github.hello09x.fakeplayer.core.manager.invsee.InventoryEditor.class), this);
            manager.registerEvents(injector.getInstance(io.github.hello09x.fakeplayer.core.manager.TransferMailbox.class), this);
        }

        // Start autosleep scheduler
        injector.getInstance(FakeplayerAutosleepManager.class).startScheduler();
        injector.getInstance(PersistentFakeplayerManager.class).restore();
        injector.getInstance(PersistentFakeplayerManager.class).start();

        {
            var placeholderExpansion = injector.getInstance(FakeplayerPlaceholderExpansion.class);
            if (placeholderExpansion != null) {
                if (placeholderExpansion.register()) {
                    getServer().getPluginManager().registerEvents(placeholderExpansion, this);
                    getLogger().info("Successfully registered PlaceholderExpansion");
                }
            }
        }

        if (injector.getInstance(FakeplayerConfig.class).isCheckForUpdates()) {
            checkForUpdatesAsync();
        }

        getLogger().info("Enabled in %d ms".formatted(System.currentTimeMillis() - loadAt));
    }

    public void checkForUpdatesAsync() {
        CompletableFuture.runAsync(() -> {
            var meta = this.getPluginMeta();
            var checker = new UpdateChecker("tanyaofei", "minecraft-fakeplayer");
            try {
                var release = checker.getLastRelease();

                var current = meta.getVersion();
                var other = release.getTagName();
                if (other.charAt(0) == 'v') {
                    other = other.substring(1);
                }

                if (UpdateChecker.isNew(current, other)) {
                    var log = getLogger();
                    log.info("New version: " + release.getTagName());
                    log.info("Address: " + meta.getWebsite());
                    log.info("Update Log");
                    for (var line : release.getBody().split("\n")) {
                        log.info("\t" + line);
                    }
                }

            } catch (Throwable e) {
                getLogger().warning("Error on checking for updates: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        if (injector != null) {
            Exceptions.suppress(this, () -> injector.getInstance(io.github.hello09x.fakeplayer.core.manager.invsee.InventoryEditor.class).close());
            if (scheduler != null) scheduler.close();
            Exceptions.suppress(this, () -> injector.getInstance(PersistentFakeplayerManager.class).save());
            Exceptions.suppress(this, () -> injector.getInstance(FakeplayerAutosleepManager.class).onDisable());
            Exceptions.suppress(this, () -> injector.getInstance(FakeplayerManager.class).onDisable());
            Exceptions.suppress(this, () -> injector.getInstance(io.github.hello09x.fakeplayer.core.manager.TransferMailbox.class).close());
            Exceptions.suppress(this, () -> injector.getInstance(UsedIdRepository.class).onDisable());
        }

        {
            Exceptions.suppress(this, () -> {
                var messenger = getServer().getMessenger();
                messenger.unregisterIncomingPluginChannel(this);
                messenger.unregisterOutgoingPluginChannel(this);
            });
        }

        if (scheduler != null) scheduler.close();
    }

}
