package com.wellnet.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

public final class WellNetGuideRepository {
    private static final Gson GSON = new Gson();
    private static final Map<String, GuideBook> CACHE = new ConcurrentHashMap<>();
    private static final GuideBook EMPTY_BOOK = new GuideBook(Collections.emptyMap(), Collections.emptyMap());

    private WellNetGuideRepository() {
    }

    public static List<String> topicLines(Minecraft minecraft, String topic) {
        String languageCode = selectedLanguage(minecraft);
        GuideBook guideBook = load(languageCode);
        List<String> lines = guideBook.topics().get(topic);
        if (lines != null && !lines.isEmpty()) {
            return lines;
        }
        if (!"en_us".equals(languageCode)) {
            return load("en_us").topics().getOrDefault(topic, Collections.emptyList());
        }
        return Collections.emptyList();
    }

    public static String tip(Minecraft minecraft, String tipKey) {
        String languageCode = selectedLanguage(minecraft);
        GuideBook guideBook = load(languageCode);
        String tip = guideBook.tips().get(tipKey);
        if (tip != null && !tip.isBlank()) {
            return tip;
        }
        if (!"en_us".equals(languageCode)) {
            return load("en_us").tips().getOrDefault(tipKey, "");
        }
        return "";
    }

    private static GuideBook load(String languageCode) {
        return CACHE.computeIfAbsent(languageCode, WellNetGuideRepository::loadGuideBook);
    }

    private static GuideBook loadGuideBook(String languageCode) {
        GuideBook exact = loadFromResource(languageCode);
        if (exact != null) {
            return exact;
        }
        GuideBook fallback = loadFromResource("en_us");
        return fallback == null ? EMPTY_BOOK : fallback;
    }

    private static GuideBook loadFromResource(String languageCode) {
        String resourcePath = "assets/wellnet/guides/" + languageCode + ".json";
        try (InputStream inputStream = WellNetGuideRepository.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                GuideBook guideBook = GSON.fromJson(reader, GuideBook.class);
                return guideBook == null ? EMPTY_BOOK : guideBook.normalize();
            }
        } catch (JsonParseException | IllegalStateException exception) {
            return EMPTY_BOOK;
        } catch (Exception exception) {
            return EMPTY_BOOK;
        }
    }

    private static String selectedLanguage(Minecraft minecraft) {
        if (minecraft == null || minecraft.getLanguageManager() == null) {
            return "en_us";
        }
        try {
            String selected = minecraft.getLanguageManager().getSelected();
            return selected == null || selected.isBlank() ? "en_us" : selected.toLowerCase();
        } catch (Throwable throwable) {
            return "en_us";
        }
    }

    private record GuideBook(Map<String, List<String>> topics, Map<String, String> tips) {
        private GuideBook normalize() {
            Map<String, List<String>> normalizedTopics = this.topics == null ? Collections.emptyMap() : this.topics;
            Map<String, String> normalizedTips = this.tips == null ? Collections.emptyMap() : this.tips;
            return new GuideBook(normalizedTopics, normalizedTips);
        }
    }
}
