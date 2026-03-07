package com.wellnet.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMigrator.class);
    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\s*\\[[^\\]]+\\]\\s*$");

    private ConfigMigrator() {
    }

    public static void migrateIfNeeded() {
        try {
            Path configDir = resolveConfigDir();
            if (configDir == null) {
                return;
            }

            Path configFile = configDir.resolve("wellnet-client.toml");
            if (!Files.isRegularFile(configFile, new LinkOption[0])) {
                return;
            }

            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            if (looksLegacy(content)) {
                moveLegacyConfigAside(configFile);
                return;
            }

            backfillModernKeys(configFile, content);
        } catch (Throwable throwable) {
            LOGGER.debug("WellNet config migration skipped after exception", throwable);
        }
    }

    private static void moveLegacyConfigAside(Path configFile) throws IOException {
        Path configDir = configFile.getParent();
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path backupFile = configDir.resolve("wellnet-client.toml.bak-" + timestamp);
        int suffix = 1;
        while (Files.exists(backupFile, new LinkOption[0])) {
            backupFile = configDir.resolve("wellnet-client.toml.bak-" + timestamp + "-" + suffix++);
        }
        Files.move(configFile, backupFile, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void backfillModernKeys(Path configFile, String content) throws IOException {
        List<String> lines = new ArrayList<>(List.of(content.replace("\r\n", "\n").split("\n", -1)));
        boolean changed = false;

        LinkedHashMap<String, String> bpsThresholdDefaults = new LinkedHashMap<>();
        bpsThresholdDefaults.put("severeBurstinessPercent", "320");
        changed |= ensureSectionKeys(lines, "bpsThresholds", bpsThresholdDefaults);

        LinkedHashMap<String, String> safeguardDefaults = new LinkedHashMap<>();
        safeguardDefaults.put("joinWarmupMs", "15000");
        safeguardDefaults.put("joinWarmupRenderDistanceCap", "12");
        safeguardDefaults.put("burstGuardMs", "20000");
        safeguardDefaults.put("burstGuardRenderDistanceCap", "10");
        safeguardDefaults.put("integratedHostCap", "16");
        safeguardDefaults.put("recoveryStableTicks", "4");
        safeguardDefaults.put("hostRecoveryStableTicks", "30");
        changed |= ensureSectionKeys(lines, "safeguards", safeguardDefaults);

        if (!changed) {
            return;
        }

        Files.writeString(
            configFile,
            String.join(System.lineSeparator(), lines),
            StandardCharsets.UTF_8
        );
    }

    private static boolean ensureSectionKeys(List<String> lines, String sectionName, LinkedHashMap<String, String> defaults) {
        int sectionStart = findSection(lines, sectionName);
        if (sectionStart < 0) {
            appendSection(lines, sectionName, defaults);
            return true;
        }

        int sectionEnd = findSectionEnd(lines, sectionStart + 1);
        boolean changed = false;
        int insertIndex = sectionEnd;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (containsKey(lines, sectionStart + 1, sectionEnd, entry.getKey())) {
                continue;
            }
            lines.add(insertIndex++, entry.getKey() + " = " + entry.getValue());
            changed = true;
        }
        return changed;
    }

    private static int findSection(List<String> lines, String sectionName) {
        String header = "[" + sectionName + "]";
        for (int i = 0; i < lines.size(); i++) {
            if (header.equals(lines.get(i).trim())) {
                return i;
            }
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int startIndex) {
        for (int i = startIndex; i < lines.size(); i++) {
            if (SECTION_PATTERN.matcher(lines.get(i)).matches()) {
                return i;
            }
        }
        return lines.size();
    }

    private static boolean containsKey(List<String> lines, int startIndex, int endIndex, String key) {
        String prefix = key + " ";
        String equalsPrefix = key + "=";
        for (int i = startIndex; i < endIndex; i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith(prefix) || trimmed.startsWith(equalsPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static void appendSection(List<String> lines, String sectionName, LinkedHashMap<String, String> defaults) {
        int lastNonEmpty = lines.size() - 1;
        while (lastNonEmpty >= 0 && lines.get(lastNonEmpty).isBlank()) {
            lastNonEmpty--;
        }
        if (lastNonEmpty >= 0) {
            lines.add("");
        }
        lines.add("[" + sectionName + "]");
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            lines.add(entry.getKey() + " = " + entry.getValue());
        }
    }

    private static boolean looksLegacy(String content) {
        if (content == null) {
            return false;
        }
        String normalized = content.toLowerCase();
        return normalized.contains("\nwellnet")
            || normalized.contains("[wellnet]")
            || normalized.contains("preset=")
            || normalized.contains("perserverprofiles")
            || normalized.contains("sampleintervalms")
            || normalized.contains("enablenettytuning");
    }

    private static Path resolveConfigDir() {
        try {
            Class<?> clazz = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object object = clazz.getField("CONFIGDIR").get(null);
            try {
                return (Path) object.getClass().getMethod("get").invoke(object);
            } catch (NoSuchMethodException noSuchMethodException) {
                return (Path) clazz.getMethod("getOrCreateGameRelativePath", String.class).invoke(null, "config");
            }
        } catch (Throwable throwable) {
            try {
                String userDir = System.getProperty("user.dir");
                if (userDir == null) {
                    return null;
                }
                return Paths.get(userDir).resolve("config");
            } catch (Throwable nestedThrowable) {
                return null;
            }
        }
    }
}
