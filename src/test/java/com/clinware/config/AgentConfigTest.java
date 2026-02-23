package com.clinware.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("AgentConfig configuration resolution")
class AgentConfigTest {


    @Test
    @DisplayName("Throws when GOOGLE_API_KEY absent from both env and YAML")
    void missingApiKey_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> new AgentConfig(Map.of(), Map.of()),
                "Expected IllegalStateException for missing API key");
    }

    @Test
    @DisplayName("Throws when API key is blank in both sources")
    void blankApiKey_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> new AgentConfig(Map.of("GOOGLE_API_KEY", "  "), Map.of()));
    }

    @Test
    @DisplayName("API key read from environment variable")
    void apiKey_fromEnv() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "env-key-123"), Map.of());

        assertEquals("env-key-123", config.getGoogleApiKey());
    }

    @Test
    @DisplayName("API key read from YAML when env variable absent")
    void apiKey_fromYaml_whenEnvMissing() {
        AgentConfig config = new AgentConfig(
                Map.of(),
                Map.of("google_api_key", "yaml-key-456"));

        assertEquals("yaml-key-456", config.getGoogleApiKey());
    }

    @Test
    @DisplayName("Env API key takes precedence over YAML API key")
    void apiKey_envWinsOverYaml() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "env-key"),
                Map.of("google_api_key", "yaml-key"));

        assertEquals("env-key", config.getGoogleApiKey());
    }

    @Test
    @DisplayName("Default values applied when only API key is provided")
    void defaults_appliedForOptionalKeys() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key"), Map.of());

        assertEquals("gemini-2.5-flash", config.getGeminiModel());
        assertEquals(15_000L,            config.getMcpTimeoutMs());
        assertEquals(30,                 config.getNewsSearchDays());
    }

    @Test
    @DisplayName("All env vars override defaults")
    void allEnvVars_overrideDefaults() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY",    "my-key",
                       "GEMINI_MODEL",      "gemini-custom",
                       "MCP_TIMEOUT_MS",    "5000",
                       "NEWS_SEARCH_DAYS",  "7"),
                Map.of());

        assertEquals("my-key",        config.getGoogleApiKey());
        assertEquals("gemini-custom", config.getGeminiModel());
        assertEquals(5_000L,          config.getMcpTimeoutMs());
        assertEquals(7,               config.getNewsSearchDays());
    }

    @Test
    @DisplayName("YAML values override built-in defaults")
    void yaml_overridesDefaults() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key"),
                Map.of("gemini_model",     "gemini-yaml",
                       "mcp_timeout_ms",   "8000",
                       "news_search_days", "14"));

        assertEquals("gemini-yaml", config.getGeminiModel());
        assertEquals(8_000L,        config.getMcpTimeoutMs());
        assertEquals(14,            config.getNewsSearchDays());
    }

    @Test
    @DisplayName("Env model takes precedence over YAML model")
    void env_winsOverYaml_model() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key", "GEMINI_MODEL", "env-model"),
                Map.of("gemini_model", "yaml-model"));

        assertEquals("env-model", config.getGeminiModel());
    }

    @Test
    @DisplayName("Env timeout takes precedence over YAML timeout")
    void env_winsOverYaml_timeout() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key", "MCP_TIMEOUT_MS", "3000"),
                Map.of("mcp_timeout_ms", "9000"));

        assertEquals(3_000L, config.getMcpTimeoutMs());
    }

    @Test
    @DisplayName("Env days takes precedence over YAML days")
    void env_winsOverYaml_days() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key", "NEWS_SEARCH_DAYS", "3"),
                Map.of("news_search_days", "90"));

        assertEquals(3, config.getNewsSearchDays());
    }

    @Test
    @DisplayName("Invalid timeout string falls back to default 15000")
    void invalidTimeoutString_usesDefault() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key", "MCP_TIMEOUT_MS", "not-a-number"),
                Map.of());

        assertEquals(15_000L, config.getMcpTimeoutMs());
    }

    @Test
    @DisplayName("Invalid days string falls back to default 30")
    void invalidDaysString_usesDefault() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY", "key", "NEWS_SEARCH_DAYS", "xyz"),
                Map.of());

        assertEquals(30, config.getNewsSearchDays());
    }

    @Test
    @DisplayName("Leading/trailing whitespace is stripped from env values")
    void whitespace_stripped() {
        AgentConfig config = new AgentConfig(
                Map.of("GOOGLE_API_KEY",   "  spaced-key  ",
                       "MCP_TIMEOUT_MS",   " 4000 ",
                       "NEWS_SEARCH_DAYS", " 10 "),
                Map.of());

        assertEquals("spaced-key", config.getGoogleApiKey());
        assertEquals(4_000L,       config.getMcpTimeoutMs());
        assertEquals(10,           config.getNewsSearchDays());
    }
}
