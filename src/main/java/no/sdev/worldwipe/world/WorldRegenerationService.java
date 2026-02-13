package no.sdev.worldwipe.world;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import org.bson.BsonDocument;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WorldRegenerationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private WorldRegenerationService() {
    }

    public static CompletableFuture<World> regenerateWorld(String worldName) {
        return regenerateWorld(worldName, null);
    }

    public static CompletableFuture<World> regenerateWorld(String worldName, WorldConfig templateConfig) {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be null or empty");
        }

        Universe universe = Universe.get();
        if (templateConfig == null) {
            return universe.addWorld(worldName, "default", "default");
        }

        Path savePath = universe.getPath().resolve("worlds").resolve(worldName);
        WorldConfig copiedConfig = copyWorldConfig(templateConfig);
        if (copiedConfig == null) {
            return universe.addWorld(worldName, "default", "default");
        }

        CompletableFuture<World> result = new CompletableFuture<>();
        universe.makeWorld(worldName, savePath, copiedConfig).whenComplete((world, error) -> {
            if (error == null) {
                result.complete(world);
                return;
            }

            LOGGER.atWarning()
                    .withCause(error)
                    .log("[WorldWipe] Failed to regenerate world '%s' from template. Falling back to default.", worldName);
            universe.addWorld(worldName, "default", "default")
                    .whenComplete((fallbackWorld, fallbackError) -> {
                        if (fallbackError != null) {
                            result.completeExceptionally(fallbackError);
                            return;
                        }
                        result.complete(fallbackWorld);
                    });
        });

        return result;
    }

    private static WorldConfig copyWorldConfig(WorldConfig source) {
        if (source == null) {
            return null;
        }
        try {
            BsonDocument encoded = WorldConfig.CODEC.encode(source, new ExtraInfo());
            WorldConfig copied = WorldConfig.CODEC.decode(encoded, new ExtraInfo());
            copied.setUuid(UUID.randomUUID());
            copied.markChanged();
            return copied;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[WorldWipe] Failed to copy world config template.");
            return null;
        }
    }
}
