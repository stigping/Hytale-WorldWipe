package no.sdev.worldwipe.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import no.sdev.worldwipe.WorldWipePlugin;
import no.sdev.worldwipe.permissions.WorldWipePermissions;
import no.sdev.worldwipe.world.WorldEvacuationService;

import javax.annotation.Nonnull;
import java.util.List;

public class NowSubCommand extends CommandBase {

    private final FlagArg dryFlag;

    public NowSubCommand() {
        super("now", "Trigger a manual world wipe (use --dry to preview)");
        this.setPermissionGroup(null);
        this.dryFlag = this.withFlagArg("dry", "Run as a dry-run (no players moved)");
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
            if (!WorldWipePermissions.hasCommand(context.sender(), "now")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("now") + ")"));
                return;
            }
            if (!WorldWipePermissions.hasAdmin(context.sender())) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.ADMIN + ")"));
                return;
            }
        }
        WorldWipePlugin plugin = WorldWipePlugin.getInstance();

        List<String> resettingWorlds = plugin.getResetWorlds();
        if (resettingWorlds.isEmpty()) {
            context.sendMessage(Message.raw("No reset worlds configured."));
            return;
        }

        List<String> protectedWorlds = plugin.getProtectedWorlds();
        String destinationWorldName = plugin.getPrimaryProtectedWorld();
        String defaultWorldName = destinationWorldName != null ? destinationWorldName : "default";

        boolean dryRun = dryFlag.provided(context);

        if (dryRun) {
            context.sendMessage(Message.raw("=== WorldWipe DRY-RUN ==="));
            context.sendMessage(Message.raw("Destination world: " + defaultWorldName));

            for (String resettingWorldName : resettingWorlds) {
                if (resettingWorldName == null || resettingWorldName.isBlank()) {
                    continue;
                }
                if (isProtectedWorld(resettingWorldName, protectedWorlds)) {
                    context.sendMessage(Message.raw(
                            "Skipping protected world: " + resettingWorldName
                    ));
                    continue;
                }

                WorldEvacuationService.EvacuationResult result =
                        WorldEvacuationService.evacuateDryRun(
                                resettingWorldName,
                                defaultWorldName
                        );

                context.sendMessage(Message.raw("Resetting world: " + result.resettingWorld()));
                context.sendMessage(Message.raw("Players affected: " + result.playersAffected()));
                context.sendMessage(Message.raw("Result: " + result.reason()));
            }
            return;
        }

        context.sendMessage(Message.raw("Starting world wipe for: " + String.join(", ", resettingWorlds) + "..."));

        for (String resettingWorldName : resettingWorlds) {
            if (resettingWorldName == null || resettingWorldName.isBlank()) {
                continue;
            }
            if (isProtectedWorld(resettingWorldName, protectedWorlds)) {
                context.sendMessage(Message.raw(
                        "Skipping protected world: " + resettingWorldName
                ));
                continue;
            }

            WorldWipePlugin.WipeResult result = plugin.wipeWorldNow(resettingWorldName);
            context.sendMessage(Message.raw(result.message()));
        }

        context.sendMessage(Message.raw("World wipe complete."));
    }

    private boolean isProtectedWorld(String worldName, List<String> protectedWorlds) {
        if (worldName == null || protectedWorlds == null || protectedWorlds.isEmpty()) {
            return false;
        }
        for (String protectedWorld : protectedWorlds) {
            if (protectedWorld != null && worldName.equalsIgnoreCase(protectedWorld)) {
                return true;
            }
        }
        return false;
    }
}
