package no.sdev.worldwipe.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import no.sdev.worldwipe.permissions.WorldWipePermissions;
import no.sdev.worldwipe.ui.WorldWipeDashboardUI;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class WorldWipePluginCommand extends AbstractPlayerCommand {

    private static final ThreadLocal<Boolean> HELP_PERMISSION_BYPASS =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public WorldWipePluginCommand() {
        super("wipe", "WorldWipe plugin commands");
        this.requirePermission(WorldWipePermissions.HELP);

        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
        this.addSubCommand(new StatusSubCommand());
        this.addSubCommand(new NowSubCommand());
        this.addSubCommand(new ScheduleSubCommand());

    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (Boolean.TRUE.equals(HELP_PERMISSION_BYPASS.get())) {
            return true;
        }
        return super.hasPermission(sender);
    }

    @Override
    public CompletableFuture<Void> acceptCall(
            CommandSender sender,
            ParserContext parserContext,
            ParseResult parseResult
    ) {
        if (sender != null && !sender.hasPermission(WorldWipePermissions.HELP)) {
            HELP_PERMISSION_BYPASS.set(Boolean.TRUE);
            try {
                return super.acceptCall(sender, parserContext, parseResult);
            } finally {
                HELP_PERMISSION_BYPASS.remove();
            }
        }
        return super.acceptCall(sender, parserContext, parseResult);
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
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        try {
            if (context.isPlayer()
                    && !WorldWipePermissions.hasCommand(context.sender(), "wipe")) {
                context.sendMessage(Message.raw("You don't have permission. ("
                        + WorldWipePermissions.command("wipe") + ")"));
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Error: Could not get Player component."));
                return;
            }

            WorldWipeDashboardUI dashboardPage = new WorldWipeDashboardUI(playerRef);
            player.getPageManager().openCustomPage(ref, store, dashboardPage);
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error opening dashboard: " + e.getMessage()));
        }
    }
}
