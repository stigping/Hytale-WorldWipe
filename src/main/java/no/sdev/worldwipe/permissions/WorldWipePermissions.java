package no.sdev.worldwipe.permissions;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.Locale;
import java.util.UUID;

public final class WorldWipePermissions {

    public static final String ADMIN = "worldwipe.admin";
    public static final String HELP = "worldwipe.help";
    public static final String SCHEDULE = "worldwipe.schedule";
    public static final String SCHEDULE_WORLD_PREFIX = "worldwipe.schedule.";
    public static final String COMMAND_PREFIX = "worldwipe.commands.";

    private WorldWipePermissions() {
    }

    public static boolean hasAdmin(PermissionHolder holder) {
        return holder != null && holder.hasPermission(ADMIN);
    }

    public static boolean hasHelp(PermissionHolder holder) {
        return holder != null && holder.hasPermission(HELP);
    }

    public static boolean hasScheduleView(PermissionHolder holder) {
        return holder != null && holder.hasPermission(SCHEDULE);
    }

    public static boolean canViewSchedule(PermissionHolder holder, String worldName) {
        if (holder == null) {
            return false;
        }
        if (holder.hasPermission(ADMIN)) {
            return true;
        }
        if (!holder.hasPermission(SCHEDULE)) {
            return false;
        }
        return worldName != null && holder.hasPermission(scheduleWorld(worldName));
    }

    public static boolean hasAdmin(UUID uuid) {
        return hasPermission(uuid, ADMIN);
    }

    public static boolean hasHelp(UUID uuid) {
        return hasPermission(uuid, HELP);
    }

    public static boolean hasScheduleView(UUID uuid) {
        return hasPermission(uuid, SCHEDULE);
    }

    public static boolean canViewSchedule(UUID uuid, String worldName) {
        if (uuid == null) {
            return false;
        }
        if (hasPermission(uuid, ADMIN)) {
            return true;
        }
        if (!hasPermission(uuid, SCHEDULE)) {
            return false;
        }
        return worldName != null && hasPermission(uuid, scheduleWorld(worldName));
    }

    public static String scheduleWorld(String worldName) {
        String sanitized = worldName == null ? "" : worldName.trim().toLowerCase(Locale.ROOT);
        return SCHEDULE_WORLD_PREFIX + sanitized;
    }

    public static boolean hasCommand(PermissionHolder holder, String commandName) {
        return holder != null && holder.hasPermission(command(commandName));
    }

    public static boolean hasCommand(UUID uuid, String commandName) {
        return hasPermission(uuid, command(commandName));
    }

    public static String command(String commandName) {
        String sanitized = commandName == null ? "" : commandName.trim().toLowerCase(Locale.ROOT);
        return COMMAND_PREFIX + sanitized;
    }

    private static boolean hasPermission(UUID uuid, String permission) {
        PermissionsModule module = PermissionsModule.get();
        if (module == null || uuid == null || permission == null || permission.isBlank()) {
            return false;
        }
        return module.hasPermission(uuid, permission);
    }
}
