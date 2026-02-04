package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;

public class ScheduleRemoveSubCommand extends CommandBase {

    private final RequiredArg<String> worldArg;

    public ScheduleRemoveSubCommand() {
        super("remove", "Remove a scheduled wipe for a world");
        this.setPermissionGroup(null);

        this.worldArg = this.withRequiredArg("world", "World name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    public Message getUsageString(CommandSender sender) {
        if (sender != null && !sender.hasPermission(WorldWipePermissions.HELP)) {
            return Message.raw("Unknown command.");
        }
        return super.getUsageString(sender);
    }

    @Override
    public Message getUsageShort(CommandSender sender, boolean includeArgs) {
        if (sender != null && !sender.hasPermission(WorldWipePermissions.HELP)) {
            return Message.raw("Unknown command.");
        }
        return super.getUsageShort(sender, includeArgs);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();
        String world = context.get(worldArg);

        if (context.isPlayer()) {
            if (!WorldWipePermissions.hasCommand(context.sender(), "schedule.remove")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("schedule.remove") + ")"));
                return;
            }
            if (!WorldWipePermissions.hasAdmin(context.sender())) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.ADMIN + ")"));
                return;
            }
        }
        if (world == null || world.isBlank()) {
            context.sendMessage(Message.raw("World name is required."));
            return;
        }

        boolean removed = plugin.removeWorldSchedule(world);
        if (!removed) {
            context.sendMessage(Message.raw("No schedule found for world: " + world));
            return;
        }

        context.sendMessage(Message.raw("Removed schedule for world: " + world));
    }
}
