package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.permissions.WorldWipePermissions;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class HelpSubCommand extends CommandBase {

    public HelpSubCommand() {
        super("help", "Show available commands");
        this.setPermissionGroup(null);
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
        if (context.isPlayer()) {
            if (!WorldWipePermissions.hasCommand(context.sender(), "help")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("help") + ")"));
                return;
            }
        }

        boolean isPlayer = context.isPlayer();
        boolean isAdmin = !isPlayer || WorldWipePermissions.hasAdmin(context.sender());
        boolean canScheduleView = !isPlayer || WorldWipePermissions.hasScheduleView(context.sender());

        List<String> lines = new ArrayList<>();

        if (!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "wipe")) {
            lines.add("/wipe - Open the dashboard UI");
        }
        if (!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "help")) {
            lines.add("/wipe help - Show this help message");
        }
        if (!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "info")) {
            lines.add("/wipe info - Show plugin information");
        }
        if (!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "status")) {
            lines.add("/wipe status - Show world wipe status");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "schedule.list"))
                && (isAdmin || canScheduleView)) {
            lines.add("/wipe schedule list - List scheduled wipes");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "schedule.set"))
                && isAdmin) {
            lines.add("/wipe schedule set <world> [mode] [day/date] [time] [zone] - Set a world schedule");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "schedule.remove"))
                && isAdmin) {
            lines.add("/wipe schedule remove <world> - Remove a world schedule");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "schedule.enable"))
                && isAdmin) {
            lines.add("/wipe schedule enable - Enable scheduled wipes");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "schedule.disable"))
                && isAdmin) {
            lines.add("/wipe schedule disable - Disable scheduled wipes");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "reload"))
                && isAdmin) {
            lines.add("/wipe reload - Reload configuration");
        }
        if (!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "ui")) {
            lines.add("/wipe ui - Open the dashboard UI");
        }
        if ((!isPlayer || WorldWipePermissions.hasCommand(context.sender(), "now"))
                && isAdmin) {
            lines.add("/wipe now [--dry] - Trigger a manual world wipe");
        }

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== WorldWipe Commands ==="));
        if (lines.isEmpty()) {
            context.sendMessage(Message.raw("No commands available."));
            context.sendMessage(Message.raw("========================"));
            return;
        }
        for (String line : lines) {
            context.sendMessage(Message.raw(line));
        }
        context.sendMessage(Message.raw("========================"));
    }
}
