package io.github.hello09x.fakeplayer.core.config;


import com.google.common.annotations.Beta;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.devtools.core.config.ConfigUtils;
import io.github.hello09x.devtools.core.config.PluginConfig;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import lombok.Getter;
import lombok.ToString;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.translatable;

@ToString
@Singleton
public class FakeplayerConfig extends PluginConfig {

    private final static Logger log = Main.getInstance().getLogger();
    private final static String defaultNameChars = "^[a-zA-Z0-9_]+$";
    private volatile Snapshot snapshot;

    public record Snapshot(
            int playerLimit,
            int serverLimit,
            String nameTemplate,
            String namePrefix,
            NamedTextColor nameStyleColor,
            List<TextDecoration> nameStyleDecorations,
            boolean followQuiting,
            boolean detectIp,
            int kaleTps,
            List<String> preSpawnCommands,
            List<String> postSpawnCommands,
            List<String> afterSpawnCommands,
            List<String> postQuitCommands,
            List<String> afterQuitCommands,
            List<String> selfCommands,
            boolean dropInventoryOnQuiting,
            boolean persistData,
            boolean kickOnDead,
            Pattern namePattern,
            boolean checkForUpdates,
            Set<String> allowCommands,
            Duration lifespan,
            boolean debug,
            PreventKicking preventKicking,
            InvseeImplement invseeImplement,
            boolean defaultOnlineSkin,
            Map<Feature, String> defaultFeatures
    ) {
        public Snapshot {
            nameStyleDecorations = List.copyOf(nameStyleDecorations);
            preSpawnCommands = List.copyOf(preSpawnCommands);
            postSpawnCommands = List.copyOf(postSpawnCommands);
            afterSpawnCommands = List.copyOf(afterSpawnCommands);
            postQuitCommands = List.copyOf(postQuitCommands);
            afterQuitCommands = List.copyOf(afterQuitCommands);
            selfCommands = List.copyOf(selfCommands);
            allowCommands = Set.copyOf(allowCommands);
            defaultFeatures = Map.copyOf(defaultFeatures);
        }
    }

    private static final class Values {
        int playerLimit;
        int serverLimit;
        String nameTemplate;
        String namePrefix;
        NamedTextColor nameStyleColor;
        List<TextDecoration> nameStyleDecorations;
        boolean followQuiting;
        boolean detectIp;
        int kaleTps;
        List<String> preSpawnCommands;
        List<String> postSpawnCommands;
        List<String> afterSpawnCommands;
        List<String> postQuitCommands;
        List<String> afterQuitCommands;
        List<String> selfCommands;
        boolean dropInventoryOnQuiting;
        boolean persistData;
        boolean kickOnDead;
        Pattern namePattern;
        boolean checkForUpdates;
        Set<String> allowCommands;
        Duration lifespan;
        boolean debug;
        PreventKicking preventKicking;
        InvseeImplement invseeImplement;
        boolean defaultOnlineSkin;
        Map<Feature, String> defaultFeatures;
    }

    public Snapshot snapshot() { return snapshot; }

    public int getPlayerLimit() { return snapshot.playerLimit(); }
    public int getServerLimit() { return snapshot.serverLimit(); }
    public String getNameTemplate() { return snapshot.nameTemplate(); }
    public String getNamePrefix() { return snapshot.namePrefix(); }
    public NamedTextColor getNameStyleColor() { return snapshot.nameStyleColor(); }
    public List<TextDecoration> getNameStyleDecorations() { return snapshot.nameStyleDecorations(); }
    public boolean isFollowQuiting() { return snapshot.followQuiting(); }
    public boolean isDetectIp() { return snapshot.detectIp(); }
    public int getKaleTps() { return snapshot.kaleTps(); }
    public List<String> getPreSpawnCommands() { return snapshot.preSpawnCommands(); }
    public List<String> getPostSpawnCommands() { return snapshot.postSpawnCommands(); }
    public List<String> getAfterSpawnCommands() { return snapshot.afterSpawnCommands(); }
    public List<String> getPostQuitCommands() { return snapshot.postQuitCommands(); }
    public List<String> getAfterQuitCommands() { return snapshot.afterQuitCommands(); }
    public List<String> getSelfCommands() { return snapshot.selfCommands(); }
    public boolean isDropInventoryOnQuiting() { return snapshot.dropInventoryOnQuiting(); }
    public boolean isPersistData() { return snapshot.persistData(); }
    public boolean isKickOnDead() { return snapshot.kickOnDead(); }
    public Pattern getNamePattern() { return snapshot.namePattern(); }
    public boolean isCheckForUpdates() { return snapshot.checkForUpdates(); }
    public Set<String> getAllowCommands() { return snapshot.allowCommands(); }
    public Duration getLifespan() { return snapshot.lifespan(); }
    public boolean isDebug() { return snapshot.debug(); }
    public PreventKicking getPreventKicking() { return snapshot.preventKicking(); }
    public InvseeImplement getInvseeImplement() { return snapshot.invseeImplement(); }
    public boolean isDefaultOnlineSkin() { return snapshot.defaultOnlineSkin(); }
    public Map<Feature, String> getDefaultFeatures() { return snapshot.defaultFeatures(); }

    @Inject
    public FakeplayerConfig() {
        super(Main.getInstance());
    }

    private static int maxIfZero(int value) {
        return value <= 0 ? Integer.MAX_VALUE : value;
    }

    @Override
    protected synchronized void reload(@NotNull FileConfiguration file) {
        var values = new Values();
        values.playerLimit = maxIfZero(file.getInt("player-limit", 1));
        values.serverLimit = maxIfZero(file.getInt("server-limit", 1000));
        values.followQuiting = file.getBoolean("follow-quiting", true);
        values.detectIp = file.getBoolean("detect-ip", false);
        values.kaleTps = file.getInt("kale-tps", 0);
        values.selfCommands = file.getStringList("self-commands");
        values.preSpawnCommands = file.getStringList("pre-spawn-commands");
        values.postSpawnCommands = file.getStringList("post-spawn-commands");
        values.afterSpawnCommands = file.getStringList("after-spawn-commands");
        values.postQuitCommands = file.getStringList("post-quit-commands");
        values.afterQuitCommands = file.getStringList("after-quit-commands");
        values.nameTemplate = file.getString("name-template", "");
        values.dropInventoryOnQuiting = file.getBoolean("drop-inventory-on-quiting", true);
        values.persistData = file.getBoolean("persist-data", true);
        values.kickOnDead = file.getBoolean("kick-on-dead", true);
        values.checkForUpdates = file.getBoolean("check-for-updates", true);
        values.namePattern = getNamePattern(file);
        values.preventKicking = this.getPreventKicking(file);
        values.nameTemplate = getNameTemplate(file);
        values.namePrefix = file.getString("name-prefix", "");
        values.lifespan = getLifespan(file);
        values.allowCommands = file.getStringList("allow-commands")
                .stream()
                .map(c -> c.startsWith("/") ? c.substring(1) : c)
                .filter(c -> !c.isBlank())
                .collect(Collectors.toSet());

        values.defaultOnlineSkin = file.getBoolean("default-online-skin", false);
        values.defaultFeatures = Arrays.stream(Feature.values())
                .collect(Collectors.toMap(Function.identity(), key -> file.getString("default-features." + key.name(), key.getDefaultOption())));
        values.invseeImplement = ConfigUtils.getEnum(file, "invsee-implement", InvseeImplement.class, InvseeImplement.AUTO);
        values.debug = file.getBoolean("debug", false);
        values.nameStyleColor = this.getNameStyleColor(file);
        values.nameStyleDecorations = this.getNameStyleDecorations(file);

        if (this.isConfigFileOutOfDate()) {
            Main.getScheduler().globalLater(() -> {
                if (Main.getInstance().isEnabled()) {
                    Main.getInstance().getComponentLogger().warn(translatable("fakeplayer.configuration.out-of-date"));
                }
            }, 1);
        }

        var preparingCommands = file.getStringList("preparing-commands");
        if (!preparingCommands.isEmpty()) {
            log.warning("preparing-commands is deprecated, use post-spawn-commands instead.");
            values.postSpawnCommands.addAll(preparingCommands);
        }

        var destroyCommands = file.getStringList("destroy-commands");
        if (!destroyCommands.isEmpty()) {
            log.warning("destroy-commands is deprecated, use post-quit-commands instead.");
            values.postQuitCommands.addAll(destroyCommands);
        }

        snapshot = new Snapshot(
                values.playerLimit,
                values.serverLimit,
                values.nameTemplate,
                values.namePrefix,
                values.nameStyleColor,
                List.copyOf(values.nameStyleDecorations),
                values.followQuiting,
                values.detectIp,
                values.kaleTps,
                List.copyOf(values.preSpawnCommands),
                List.copyOf(values.postSpawnCommands),
                List.copyOf(values.afterSpawnCommands),
                List.copyOf(values.postQuitCommands),
                List.copyOf(values.afterQuitCommands),
                List.copyOf(values.selfCommands),
                values.dropInventoryOnQuiting,
                values.persistData,
                values.kickOnDead,
                values.namePattern,
                values.checkForUpdates,
                Set.copyOf(values.allowCommands),
                values.lifespan,
                values.debug,
                values.preventKicking,
                values.invseeImplement,
                values.defaultOnlineSkin,
                Map.copyOf(values.defaultFeatures)
        );
    }

    private @Nullable Duration getLifespan(@NotNull FileConfiguration file) {
        var minutes = file.getLong("lifespan");
        if (minutes <= 0) {
            return null;
        }
        return Duration.ofMinutes(minutes);
    }


    private @NotNull Pattern getNamePattern(@NotNull FileConfiguration file) {
        try {
            return Pattern.compile(file.getString("name-pattern", defaultNameChars));
        } catch (PatternSyntaxException e) {
            log.warning("Invalid name-pattern: " + file.getString("name-chars"));
            return Pattern.compile(defaultNameChars);
        }
    }

    private @NotNull String getNameTemplate(@NotNull FileConfiguration file) {
        var tmpl = file.getString("name-template", "");
        if (tmpl.startsWith("-") || tmpl.startsWith("@")) {
            log.warning("Invalid name template: " + tmpl);
            return "";
        }
        return tmpl;
    }

    private @NotNull PreventKicking getPreventKicking(@NotNull FileConfiguration file) {
        if (file.getBoolean("prevent-kicked-on-spawning", false)) {
            log.warning("prevent-kicked-on-spawning is deprecated which will be removed at 0.4.0, use prevent-kick instead");
            return PreventKicking.ON_SPAWNING;
        }

        return ConfigUtils.getEnum(file, "prevent-kicking", PreventKicking.class, PreventKicking.ON_SPAWNING);
    }

    private @NotNull NamedTextColor getNameStyleColor(@NotNull FileConfiguration file) {
        var styles = Objects.requireNonNullElse(file.getString("name-style"), "").split(",\\s*");
        var color = NamedTextColor.WHITE;
        for (var style : styles) {
            var c = NamedTextColor.NAMES.value(style);
            if (c != null) {
                color = c;
            }
        }
        return color;
    }

    private @NotNull List<TextDecoration> getNameStyleDecorations(@NotNull FileConfiguration file) {
        var styles = Objects.requireNonNullElse(file.getString("name-style"), "").split(",\\s*");
        var decorations = new ArrayList<TextDecoration>();
        for (var style : styles) {
            var decoration = TextDecoration.NAMES.value(style);
            if (decoration != null) {
                decorations.add(decoration);
            }
        }
        return decorations;
    }

}
