package io.github.hello09x.fakeplayer.core.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FakeplayerFeatureUtils {

    public static boolean isInvulnerable(@NotNull Player player) {
        return player.isInvulnerable() || getAbilitiesInvulnerable(player);
    }

    public static void setInvulnerable(@NotNull Player player, boolean invulnerable) {
        player.setInvulnerable(invulnerable);
        setHandleInvulnerable(player, invulnerable);
        setAbilitiesInvulnerable(player, invulnerable);
        if (!invulnerable) {
            player.setNoDamageTicks(0);
            setInvulnerableTime(player, 0);
        }
    }

    private static void setHandleInvulnerable(@NotNull Player player, boolean invulnerable) {
        try {
            var handle = getHandle(player);
            if (handle != null) {
                invokeMethod(handle, "setInvulnerable", new Class[]{boolean.class}, invulnerable);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
        }
    }

    private static void setInvulnerableTime(@NotNull Player player, int ticks) {
        try {
            var handle = getHandle(player);
            if (handle != null) {
                setIntField(handle, "invulnerableTime", ticks);
            }
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }
    }

    private static boolean getAbilitiesInvulnerable(@NotNull Player player) {
        try {
            var abilities = getAbilities(player);
            return abilities != null && getBooleanField(abilities, "invulnerable");
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
            return false;
        }
    }

    private static void setAbilitiesInvulnerable(@NotNull Player player, boolean invulnerable) {
        try {
            var handle = getHandle(player);
            var abilities = getAbilities(handle);
            if (abilities == null) {
                return;
            }

            setBooleanField(abilities, "invulnerable", invulnerable);
            invokeMethod(handle, "onUpdateAbilities", new Class[0]);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchFieldException | NoSuchMethodException ignored) {
        }
    }

    private static Object getAbilities(@NotNull Player player) {
        return getAbilities(getHandle(player));
    }

    private static Object getAbilities(Object handle) {
        if (handle == null) {
            return null;
        }

        try {
            return invokeMethod(handle, "getAbilities", new Class[0]);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object getHandle(@NotNull Player player) {
        try {
            return player.getClass().getMethod("getHandle").invoke(player);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static @NotNull Field findField(@NotNull Class<?> type, @NotNull String name) throws NoSuchFieldException {
        var current = type;
        while (current != null) {
            try {
                var field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static @NotNull Method findMethod(@NotNull Class<?> type, @NotNull String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        var current = type;
        while (current != null) {
            try {
                var method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object invokeMethod(@NotNull Object target, @NotNull String name, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return findMethod(target.getClass(), name, parameterTypes).invoke(target, args);
    }

    private static boolean getBooleanField(@NotNull Object target, @NotNull String name) throws NoSuchFieldException, IllegalAccessException {
        return findField(target.getClass(), name).getBoolean(target);
    }

    private static void setBooleanField(@NotNull Object target, @NotNull String name, boolean value) throws NoSuchFieldException, IllegalAccessException {
        findField(target.getClass(), name).setBoolean(target, value);
    }

    private static void setIntField(@NotNull Object target, @NotNull String name, int value) throws NoSuchFieldException, IllegalAccessException {
        findField(target.getClass(), name).setInt(target, value);
    }
}
