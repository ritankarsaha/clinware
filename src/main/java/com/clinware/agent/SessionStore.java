package com.clinware.agent;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public final class SessionStore {

    private static final Logger log  = LoggerFactory.getLogger(SessionStore.class);
    private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().create();

    private SessionStore() {}

    //  Default path

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".clinware", "history.json");
    }

    public static Path sessionsDir() {
        return Path.of(System.getProperty("user.home"), ".clinware", "sessions");
    }

    public static final class SessionMeta {
        public final Path   path;
        public final String label;       
        public final int    turnCount;
        public final String preview;    

        SessionMeta(Path path, String label, int turnCount, String preview) {
            this.path      = path;
            this.label     = label;
            this.turnCount = turnCount;
            this.preview   = preview;
        }

        @Override public String toString() { return label; }
    }

    /**
     * Saves the current history as a timestamped archive file in
     * {@link #sessionsDir()}.  
     */
    public static void archiveCurrent(List<Content> history) {
        if (history.isEmpty()) return;
        String name = "session_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".json";
        save(history, sessionsDir().resolve(name));
    }

    public static List<SessionMeta> listSessions() {
        Path dir = sessionsDir();
        if (!Files.exists(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .map(SessionStore::parseMeta)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Could not list sessions: {}", e.getMessage());
            return List.of();
        }
    }

    private static SessionMeta parseMeta(Path path) {
        try {
            List<Content> turns = load(path);
            String preview = turns.stream()
                    .filter(c -> "user".equalsIgnoreCase(extractRole(c)))
                    .map(SessionStore::extractText)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse("(empty)");
            if (preview.length() > 65) preview = preview.substring(0, 65) + "…";

            // Parse friendly label from filename
            String fname = path.getFileName().toString();
            String label;
            if (fname.matches("session_\\d{8}_\\d{6}\\.json")) {
                String d = fname.substring(8, 16);
                String t = fname.substring(17, 23);
                label = d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8)
                      + "  " + t.substring(0, 2) + ":" + t.substring(2, 4);
            } else {
                label = fname.replace(".json", "");
            }
            label += "   (" + turns.size() + " turns)";

            return new SessionMeta(path, label, turns.size(), preview);
        } catch (Exception e) {
            return null;
        }
    }

    // Save
    public static void save(List<Content> history, Path path) {
        JsonArray arr = new JsonArray();

        for (Content c : history) {
            String role = extractRole(c);
            String text = extractText(c);
            if (text == null || text.isBlank()) continue;

            JsonObject obj = new JsonObject();
            obj.addProperty("role", role);
            obj.addProperty("text", text);
            arr.add(obj);
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(arr));
            log.info("Session saved → {} ({} text turns)", path, arr.size());
        } catch (IOException e) {
            log.warn("Could not save session history: {}", e.getMessage());
        }
    }

    // Load

    public static List<Content> load(Path path) {
        if (!Files.exists(path)) return new ArrayList<>();

        try {
            String    json = Files.readString(path);
            JsonArray arr  = JsonParser.parseString(json).getAsJsonArray();

            List<Content> result = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj  = el.getAsJsonObject();
                String     role = obj.has("role") ? obj.get("role").getAsString() : "user";
                String     text = obj.has("text") ? obj.get("text").getAsString() : "";
                if (text.isBlank()) continue;

                result.add(Content.builder()
                        .role(role)
                        .parts(Part.fromText(text))
                        .build());
            }
            log.info("Session loaded ← {} ({} turns)", path, result.size());
            return result;
        } catch (Exception e) {
            log.warn("Could not load session history (starting fresh): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // Helpers

    private static String extractRole(Content c) {
        try { return c.role().orElse("user"); }
        catch (Exception e) { return "user"; }
    }

    private static String extractText(Content c) {
        try {
            JsonElement el = GSON.toJsonTree(c);
            if (!el.isJsonObject()) return null;

            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("parts")) return null;

            JsonElement partsEl = obj.get("parts");

            if (partsEl.isJsonArray()) {
                for (JsonElement part : partsEl.getAsJsonArray()) {
                    String t = textFromPartJson(part);
                    if (t != null) return t;
                }
            } else if (partsEl.isJsonObject()) {
                return textFromPartJson(partsEl);
            }
        } catch (Exception e) {
            log.debug("Could not extract text from Content: {}", e.getMessage());
        }
        return null;
    }

    private static String textFromPartJson(JsonElement part) {
        if (!part.isJsonObject()) return null;
        JsonObject obj = part.getAsJsonObject();
        if (obj.has("text")) {
            String t = obj.get("text").getAsString();
            return t.isBlank() ? null : t;
        }
        return null;
    }
}
