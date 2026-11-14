package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.Bukkit;

/** Keep configuration API additions out of the shared runtime linkage on older servers. */
public final class ProxySupport {
    private ProxySupport() {}

    public static boolean isEnabled() {
        try {
            var type = Class.forName("io.papermc.paper.configuration.ServerConfiguration");
            var configuration = Bukkit.getServer().getClass().getMethod("getServerConfig").invoke(Bukkit.getServer());
            return (boolean) type.getMethod("isProxyEnabled").invoke(configuration);
        } catch (ClassNotFoundException | NoSuchMethodException unavailable) {
            try {
                var bungee = Class.forName("org.spigotmc.SpigotConfig").getField("bungee").getBoolean(null);
                var type = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
                var configuration = type.getMethod("get").invoke(null);
                var proxies = type.getField("proxies").get(configuration);
                var velocity = proxies.getClass().getField("velocity").get(proxies);
                return bungee || velocity.getClass().getField("enabled").getBoolean(velocity);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot determine proxy configuration", error);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read proxy configuration", error);
        }
    }
}
