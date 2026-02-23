package com.clinware.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    // Field names 

    private static final String ENV_API_KEY      = "GOOGLE_API_KEY";
    private static final String ENV_MODEL        = "GEMINI_MODEL";
    private static final String ENV_TIMEOUT      = "MCP_TIMEOUT_MS";
    private static final String ENV_DAYS         = "NEWS_SEARCH_DAYS";
    private static final String ENV_MODE         = "AGENT_MODE";

    private static final String YAML_API_KEY     = "google_api_key";
    private static final String YAML_MODEL       = "gemini_model";
    private static final String YAML_TIMEOUT     = "mcp_timeout_ms";
    private static final String YAML_DAYS        = "news_search_days";
    private static final String YAML_MODE        = "agent_mode";

    //  Defaults

    private static final String DEFAULT_MODEL    = "gemini-2.5-flash";
    private static final long   DEFAULT_TIMEOUT  = 15_000L;
    private static final int    DEFAULT_DAYS     = 30;
    private static final String DEFAULT_MODE     = "hybrid";

    //  Fields
    private final String googleApiKey;
    private final String geminiModel;
    private final long   mcpTimeoutMs;
    private final int    newsSearchDays;
    private final String agentModeRaw;

    
    public AgentConfig() {
        this(System.getenv(), loadYamlFiles());
    }

    AgentConfig(Map<String, String> env, Map<String, String> fileConfig) {
        String key = resolve(env, fileConfig, ENV_API_KEY, YAML_API_KEY, null);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Required key '" + ENV_API_KEY + "' is not configured.\n"
                    + "  Set via environment:  export " + ENV_API_KEY + "=<your-key>\n"
                    + "  Or add to .clinware.yml:  " + YAML_API_KEY + ": \"<your-key>\"");
        }
        this.googleApiKey   = key.trim();
        this.geminiModel    = resolve(env, fileConfig, ENV_MODEL,   YAML_MODEL,   DEFAULT_MODEL);
        this.mcpTimeoutMs   = parseLong(resolve(env, fileConfig, ENV_TIMEOUT, YAML_TIMEOUT, null), DEFAULT_TIMEOUT);
        this.newsSearchDays = parseInt(resolve(env, fileConfig, ENV_DAYS,    YAML_DAYS,    null), DEFAULT_DAYS);
        this.agentModeRaw   = resolve(env, fileConfig, ENV_MODE,    YAML_MODE,    DEFAULT_MODE);
    }

    // Accessors 

    public String getGoogleApiKey()   { return googleApiKey; }
    public String getGeminiModel()    { return geminiModel; }
    public long   getMcpTimeoutMs()   { return mcpTimeoutMs; }
    public int    getNewsSearchDays() { return newsSearchDays; }
    public String getAgentModeRaw()   { return agentModeRaw; }

    //  Resolution logic

    /**
     * Resolves a configuration value with priority.
     *
     * @param env          
     * @param fileConfig   
     * @param envKey     
     * @param yamlKey     
     * @param defaultValue 
     */
    private static String resolve(Map<String, String> env, Map<String, String> fileConfig,
                                  String envKey, String yamlKey, String defaultValue) {
        String v = env.get(envKey);
        if (v != null && !v.isBlank()) return v.trim();

        v = fileConfig.get(yamlKey);
        if (v != null && !v.isBlank()) return v.trim();

        return defaultValue;
    }

    // YAML file loading

    private static Map<String, String> loadYamlFiles() {
        Map<String, String> merged = new HashMap<>();
        mergeYaml(Path.of(System.getProperty("user.home"), ".clinware.yml"), merged);
        mergeYaml(Path.of(".clinware.yml"), merged);
        return merged.isEmpty() ? Collections.emptyMap() : merged;
    }

    private static void mergeYaml(Path path, Map<String, String> target) {
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
           
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> data = yaml.load(in);
            if (data != null) {
                data.forEach((k, v) -> {
                    if (k != null && v != null) {
                        target.put(k.toString(), v.toString());
                    }
                });
                log.info("Loaded config from {}", path.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not load config file '{}': {}", path, e.getMessage());
        }
    }

    //  Numeric parsing helpers

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException e) {
            log.warn("Invalid long value '{}', using default {}", value, fallback);
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) {
            log.warn("Invalid int value '{}', using default {}", value, fallback);
            return fallback;
        }
    }
}
