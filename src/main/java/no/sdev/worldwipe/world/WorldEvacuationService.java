package no.sdev.worldwipe.world;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public final class WorldEvacuationService {

    public static final String PLAYER_MOVE_MESSAGE_TEMPLATE =
            "You were teleported from '%s' to '%s' because '%s' is resetting.";
    private static final String CHAT_TITLE = "[WorldWipe]";
    private static final String COLOR_ACCENT = "#F59E0B";
    private static final String COLOR_DIM = "#A3A3A3";

    private static final PlayerTransferService TRANSFER_SERVICE =
            new HytalePlayerTransferService();

    private WorldEvacuationService() {
    }

    public static EvacuationResult evacuate(
            World fromWorld,
            World toWorld
    ) {
        Objects.requireNonNull(fromWorld, "fromWorld");
        Objects.requireNonNull(toWorld, "toWorld");

        Collection<PlayerRef> players = fromWorld.getPlayerRefs();

        if (players == null || players.isEmpty()) {
            return new EvacuationResult(
                    fromWorld.getName(),
                    toWorld.getName(),
                    0,
                    true,
                    "No players to evacuate"
            );
        }

        int moved = TRANSFER_SERVICE.transferPlayers(
                fromWorld,
                toWorld,
                players
        );

        for (PlayerRef playerRef : players) {
            if (playerRef == null) {
                continue;
            }

            String body = String.format(
                    PLAYER_MOVE_MESSAGE_TEMPLATE,
                    fromWorld.getName(),
                    toWorld.getName(),
                    fromWorld.getName()
            );

            sendStyledTeleportMessage(playerRef, fromWorld.getName(), toWorld.getName());
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw("WorldWipe"),
                    Message.raw(body),
                    NotificationStyle.Warning
            );
        }

        return new EvacuationResult(
                fromWorld.getName(),
                toWorld.getName(),
                moved,
                true,
                "Players evacuated successfully"
        );
    }

    public record EvacuationResult(
            String resettingWorld,
            String destinationWorld,
            int playersAffected,
            boolean success,
            String reason
    ) {
    }
    public static boolean unloadWorld(World world) {
        if (world == null) {
            return false;
        }

        try {
            return Universe.get().removeWorld(world.getName());
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean deleteWorldFromDisk(World world) {
        if (world == null) {
            return false;
        }

        return deleteWorldFromDisk(world.getName());
    }

    public static boolean deleteWorldFromDisk(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        Path worldRoot = resolveWorldRoot(worldName);
        if (worldRoot == null) {
            return true;
        }
        if (Files.notExists(worldRoot)) {
            return true;
        }

        try (var stream = Files.walk(worldRoot)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path resolveWorldRoot(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        Path base = Universe.getWorldGenPath();
        if (base != null) {
            Path candidate = base.resolve(worldName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path candidateWithWorlds = base.resolve("worlds").resolve(worldName);
            if (Files.exists(candidateWithWorlds)) {
                return candidateWithWorlds;
            }
        }

        Path[] candidates = new Path[] {
                Paths.get("universe", "worlds", worldName),
                Paths.get("server", "universe", "worlds", worldName),
                Paths.get(worldName)
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        return null;
    }
    public static EvacuationResult evacuateDryRun(
            String resettingWorld,
            String destinationWorld
    ) {
        return new EvacuationResult(
                resettingWorld,
                destinationWorld,
                0,
                true,
                "Dry-run only (no players moved)"
        );
    }

    private static void sendStyledTeleportMessage(
            PlayerRef playerRef,
            String fromWorldName,
            String toWorldName
    ) {
        if (playerRef == null) {
            return;
        }

        playerRef.sendMessage(colored(CHAT_TITLE, COLOR_ACCENT));
        playerRef.sendMessage(colored(
                "Teleport: " + fromWorldName + " -> " + toWorldName,
                COLOR_DIM
        ));
        playerRef.sendMessage(colored(
                "Reason: " + fromWorldName + " is resetting.",
                COLOR_ACCENT
        ));
    }

    private static Message colored(String text, String colorHex) {
        return Message.raw(text).color(colorHex);
    }
}
