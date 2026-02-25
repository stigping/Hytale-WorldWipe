package no.sdev.worldwipe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorldWipeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DumperOptions YAML_OPTIONS = new DumperOptions();
    private static final Yaml YAML_DUMPER;
    private static final Yaml YAML_LOADER;
    private static final int BANNER_WIDTH = 76;
    private static final String CONFIG_HEADER;

    static {
        YAML_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        YAML_OPTIONS.setPrettyFlow(true);
        YAML_OPTIONS.setIndent(2);
        YAML_OPTIONS.setExplicitStart(false);
        YAML_DUMPER = new Yaml(YAML_OPTIONS);
        YAML_LOADER = new Yaml(new SafeConstructor(new LoaderOptions()));
        CONFIG_HEADER = buildHeader();
    }

    public List<String> protectedWorlds;
    public Boolean schedulingEnabled;
    public Boolean regenerateOnWipe;
    public Boolean offlineTrackingEnabled;
    public Boolean offlineTrackingSaveFile;
    public Integer offlineTrackingMaxDays;
    public String offlineTrackingMode;
    public Map<String, WorldEntry> worlds;

    public static WorldWipeConfig loadOrCreate(Path path) {
        WorldWipeConfig config = null;

        if (path != null && Files.exists(path)) {
            try {
                config = loadYaml(path);
                ensureHeader(path);
            } catch (Exception ignored) {
            }
        }

        if (config == null) {
            config = new WorldWipeConfig();
        }

        config.ensureDefaults();

        if (path != null && Files.notExists(path)) {
            try {
                Files.createDirectories(path.getParent());
                writeYaml(path, config);
            } catch (IOException ignored) {
            }
        }

        return config;
    }

    public static WorldWipeConfig loadFromJson(Path path) {
        if (path == null || Files.notExists(path)) {
            return null;
        }
        try {
            String json = Files.readString(path);
            if (json == null || json.isBlank()) {
                return null;
            }
            Object data = GSON.fromJson(json, Object.class);
            if (!(data instanceof Map)) {
                return null;
            }
            return fromMap((Map<?, ?>) data);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureDefaults() {
        if (protectedWorlds == null || protectedWorlds.isEmpty()) {
            protectedWorlds = List.of("default");
        }
        if (schedulingEnabled == null) {
            schedulingEnabled = false;
        }
        if (regenerateOnWipe == null) {
            regenerateOnWipe = false;
        }
        if (offlineTrackingEnabled == null) {
            offlineTrackingEnabled = true;
        }
        if (offlineTrackingSaveFile == null) {
            offlineTrackingSaveFile = true;
        }
        if (offlineTrackingMaxDays == null) {
            offlineTrackingMaxDays = 90;
        }
        if (offlineTrackingMode == null || offlineTrackingMode.isBlank()) {
            offlineTrackingMode = "ALL";
        }
        if (worlds == null) {
            worlds = new HashMap<>();
        }
    }

    private static WorldWipeConfig loadYaml(Path path) {
        if (path == null || Files.notExists(path)) {
            return null;
        }

        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            return null;
        }

        if (content == null || content.isBlank()) {
            return null;
        }

        Object data = YAML_LOADER.load(content);
        if (!(data instanceof Map)) {
            return null;
        }

        return fromMap((Map<?, ?>) data);
    }

    private static void ensureHeader(Path path) {
        if (path == null || Files.notExists(path)) {
            return;
        }

        String raw;
        try {
            raw = Files.readString(path);
        } catch (IOException e) {
            return;
        }

        if (raw == null) {
            return;
        }

        String normalized = raw.replace("\r\n", "\n");
        if (normalized.startsWith(CONFIG_HEADER)) {
            return;
        }

        int offset = findYamlBodyOffset(normalized);
        String body = offset >= 0 ? normalized.substring(offset) : "";
        body = body.stripLeading();

        try {
            Files.writeString(path, CONFIG_HEADER + body);
        } catch (IOException ignored) {
        }
    }

    private static int findYamlBodyOffset(String content) {
        if (content == null || content.isBlank()) {
            return -1;
        }

        int offset = 0;
        String[] lines = content.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return offset;
            }
            offset += line.length() + 1;
        }
        return -1;
    }

    public static void writeYaml(Path path, WorldWipeConfig config) throws IOException {
        if (path == null || config == null) {
            return;
        }
        Files.createDirectories(path.getParent());
        String yaml = YAML_DUMPER.dump(buildYamlMap(config));
        Files.writeString(path, CONFIG_HEADER + yaml);
    }

    private static Map<String, Object> buildYamlMap(WorldWipeConfig config) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schedulingEnabled", config.schedulingEnabled != null ? config.schedulingEnabled : Boolean.FALSE);
        root.put("regenerateOnWipe", config.regenerateOnWipe != null ? config.regenerateOnWipe : Boolean.FALSE);
        root.put(
                "offlineTrackingEnabled",
                config.offlineTrackingEnabled != null ? config.offlineTrackingEnabled : Boolean.TRUE
        );
        root.put(
                "offlineTrackingSaveFile",
                config.offlineTrackingSaveFile != null ? config.offlineTrackingSaveFile : Boolean.TRUE
        );
        root.put(
                "offlineTrackingMaxDays",
                config.offlineTrackingMaxDays != null ? config.offlineTrackingMaxDays : 90
        );
        root.put(
                "offlineTrackingMode",
                config.offlineTrackingMode != null ? config.offlineTrackingMode : "ALL"
        );
        List<String> protectedWorlds = config.protectedWorlds;
        if (protectedWorlds == null || protectedWorlds.isEmpty()) {
            root.put("protectedWorld", "default");
        } else if (protectedWorlds.size() == 1) {
            root.put("protectedWorld", protectedWorlds.get(0));
        } else {
            root.put("protectedWorld", protectedWorlds);
        }

        Map<String, Object> worldsMap = new LinkedHashMap<>();
        if (config.worlds != null) {
            for (Map.Entry<String, WorldEntry> entry : config.worlds.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                WorldEntry worldEntry = entry.getValue() != null ? entry.getValue() : new WorldEntry();
                Map<String, Object> worldValue = new LinkedHashMap<>();
                worldValue.put("schedule", buildScheduleMap(worldEntry.schedule));
                if (worldEntry.regenerateOnWipe != null) {
                    worldValue.put("regenerateOnWipe", worldEntry.regenerateOnWipe);
                }
                if (worldEntry.lastWipe != null && !worldEntry.lastWipe.isBlank()) {
                    worldValue.put("lastWipe", worldEntry.lastWipe);
                }
                worldsMap.put(entry.getKey(), worldValue);
            }
        }
        root.put("worlds", worldsMap);

        return root;
    }

    private static Map<String, Object> buildScheduleMap(Schedule schedule) {
        Schedule resolved = schedule != null ? schedule : new Schedule();
        Map<String, Object> scheduleMap = new LinkedHashMap<>();
        scheduleMap.put("mode", resolved.mode);
        scheduleMap.put("day", resolved.day);
        scheduleMap.put("dayOfMonth", resolved.dayOfMonth);
        scheduleMap.put("time", resolved.time);
        scheduleMap.put("zone", resolved.zone);
        return scheduleMap;
    }

    public static final class Schedule {
        public String mode = "WEEKLY";
        public String day = "MONDAY";
        public Integer dayOfMonth = 1;
        public String time = "06:00";
        public String zone = "system";
    }

    public static final class WorldEntry {
        public Schedule schedule = new Schedule();
        public Boolean regenerateOnWipe;
        public String lastWipe;
    }

    public record EffectiveConfig(
            List<String> protectedWorlds,
            boolean schedulingEnabled,
            boolean regenerateOnWipe,
            boolean offlineTrackingEnabled,
            boolean offlineTrackingSaveFile,
            int offlineTrackingMaxDays,
            String offlineTrackingMode,
            Map<String, WorldEntry> worlds
    ) {
    }

    private static String buildHeader() {
        StringBuilder builder = new StringBuilder();
        String border = "# +" + "-".repeat(BANNER_WIDTH) + "+\n";
        builder.append(border);

        String[] bannerLines = new String[] {
                "",
                "   _____ _____  ________      __",
                "  / ____|  __ \\|  ____\\ \\    / /",
                " | (___ | |  | | |__   \\ \\  / /",
                "  \\___ \\| |  | |  __|   \\ \\/ /",
                "  ____) | |__| | |____   \\  /",
                " |_____/|_____/|______|   \\/",
                "",
                "  https://sdev.no/",
                "  Discord: https://discord.sdev.no/",
                "  Bug Reports: https://github.com/stigping/Hytale-WorldWipe/issues",
                "  Author: StigPing",
                ""
        };

        for (String line : bannerLines) {
            builder.append("# | ")
                    .append(padRight(line, BANNER_WIDTH - 1))
                    .append("|\n");
        }

        builder.append(border);
        builder.append("\n");

        return builder.toString();
    }

    private static String padRight(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    public EffectiveConfig resolve() {
        List<String> resolvedProtected = protectedWorlds != null ? protectedWorlds : List.of("default");
        Map<String, WorldEntry> resolvedWorlds = worlds != null ? new HashMap<>(worlds) : new HashMap<>();
        boolean enabled = schedulingEnabled != null && schedulingEnabled;
        boolean regenerate = regenerateOnWipe != null && regenerateOnWipe;
        boolean offlineEnabled = offlineTrackingEnabled == null || offlineTrackingEnabled;
        boolean offlineSaveFile = offlineTrackingSaveFile == null || offlineTrackingSaveFile;
        int maxDays = offlineTrackingMaxDays != null ? offlineTrackingMaxDays : 90;
        String mode = offlineTrackingMode != null ? offlineTrackingMode : "ALL";
        return new EffectiveConfig(
                resolvedProtected,
                enabled,
                regenerate,
                offlineEnabled,
                offlineSaveFile,
                maxDays,
                mode,
                resolvedWorlds
        );
    }

    private static WorldWipeConfig fromMap(Map<?, ?> root) {
        if (root == null) {
            return null;
        }

        WorldWipeConfig config = new WorldWipeConfig();

        Object protectedValue = root.containsKey("protectedWorlds")
                ? root.get("protectedWorlds")
                : root.get("protectedWorld");
        config.protectedWorlds = parseStringList(protectedValue);
        config.schedulingEnabled = parseBoolean(root.get("schedulingEnabled"), root.get("enabled"));
        config.regenerateOnWipe = parseBoolean(root.get("regenerateOnWipe"), null);
        config.offlineTrackingEnabled = parseBoolean(root.get("offlineTrackingEnabled"), null);
        config.offlineTrackingSaveFile = parseBoolean(root.get("offlineTrackingSaveFile"), null);
        config.offlineTrackingMaxDays = parseInt(root.get("offlineTrackingMaxDays"));
        Object trackingMode = root.get("offlineTrackingMode");
        if (trackingMode != null) {
            config.offlineTrackingMode = trackingMode.toString();
        }

        Object worldsValue = root.get("worlds");
        if (worldsValue instanceof Map) {
            Map<String, WorldEntry> worldEntries = new LinkedHashMap<>();
            Map<?, ?> rawWorlds = (Map<?, ?>) worldsValue;
            for (Map.Entry<?, ?> entry : rawWorlds.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String worldName = entry.getKey().toString();
                if (worldName.isBlank()) {
                    continue;
                }
                WorldEntry worldEntry = parseWorldEntry(entry.getValue());
                worldEntries.put(worldName, worldEntry);
            }
            config.worlds = worldEntries;
        }

        config.ensureDefaults();
        return config;
    }

    private static WorldEntry parseWorldEntry(Object value) {
        WorldEntry entry = new WorldEntry();
        if (!(value instanceof Map)) {
            return entry;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        Object scheduleValue = map.get("schedule");
        entry.schedule = parseSchedule(scheduleValue);
        entry.regenerateOnWipe = parseBooleanValue(map.get("regenerateOnWipe"));
        Object lastWipe = map.get("lastWipe");
        if (lastWipe != null) {
            String text = lastWipe.toString().trim();
            entry.lastWipe = text.isBlank() ? null : text;
        }
        return entry;
    }

    private static Schedule parseSchedule(Object value) {
        Schedule schedule = new Schedule();
        if (!(value instanceof Map)) {
            return schedule;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        Object day = map.get("day");
        Object dayOfMonth = map.get("dayOfMonth");
        Object mode = map.get("mode");
        Object time = map.get("time");
        Object zone = map.get("zone");
        if (mode != null) {
            schedule.mode = mode.toString();
        }
        if (day != null) {
            schedule.day = day.toString();
        }
        if (dayOfMonth instanceof Number) {
            schedule.dayOfMonth = ((Number) dayOfMonth).intValue();
        } else if (dayOfMonth != null) {
            try {
                schedule.dayOfMonth = Integer.parseInt(dayOfMonth.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (time != null) {
            schedule.time = time.toString();
        }
        if (zone != null) {
            schedule.zone = zone.toString();
        }
        return schedule;
    }

    private static List<String> parseStringList(Object value) {
        if (value == null) {
            return null;
        }
        Set<String> values = new LinkedHashSet<>();
        if (value instanceof Iterable) {
            for (Object entry : (Iterable<?>) value) {
                if (entry == null) {
                    continue;
                }
                String text = entry.toString().trim();
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
        } else {
            String text = value.toString().trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values.isEmpty() ? null : List.copyOf(values);
    }

    private static Boolean parseBoolean(Object primary, Object fallback) {
        Boolean result = parseBooleanValue(primary);
        if (result != null) {
            return result;
        }
        return parseBooleanValue(fallback);
    }

    private static Boolean parseBooleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value.toString().trim();
        if (text.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = value.toString().trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
