package com.clinware.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("PromptLibrary — system instruction and response constants")
class PromptLibraryTest {

    private static final String SI = PromptLibrary.SYSTEM_INSTRUCTION;


    @Test
    @DisplayName("System instruction declares Clinware AI identity")
    void systemInstruction_declaresClinwareIdentity() {
        assertTrue(SI.contains("Clinware AI"),
                "System instruction must assert Clinware AI identity");
    }

    @Test
    @DisplayName("System instruction forbids claiming to be Gemini or Google")
    void systemInstruction_forbidsGeminiIdentity() {
        assertTrue(SI.contains("Never say you are Gemini"),
                "Must explicitly forbid saying the agent is Gemini");
    }


    @Test
    @DisplayName("System instruction mandates searchNews tool for Clinware queries")
    void systemInstruction_mandatesSearchNewsTool() {
        assertTrue(SI.contains("ALWAYS call the searchNews tool"),
                "Must instruct agent to always call searchNews for Clinware queries");
    }

    @Test
    @DisplayName("System instruction covers key Clinware themes")
    void systemInstruction_coversClinwareThemes() {
        assertTrue(SI.contains("funding"),      "Should mention funding rounds");
        assertTrue(SI.contains("product"),      "Should mention product launches");
        assertTrue(SI.contains("post-acute"),   "Should mention post-acute care context");
    }

   

    @Test
    @DisplayName("System instruction contains SCOPE RESTRICTION block")
    void systemInstruction_containsScopeRestrictionBlock() {
        assertTrue(SI.contains("SCOPE RESTRICTION"),
                "System instruction must contain a SCOPE RESTRICTION section");
    }

    @Test
    @DisplayName("Scope restriction explicitly lists permitted topics")
    void scopeRestriction_listsPermittedTopics() {
        assertTrue(SI.contains("Clinware"),    "Permitted: Clinware");
        assertTrue(SI.contains("Healthcare") || SI.contains("healthcare"),
                "Permitted: Healthcare");
        assertTrue(SI.contains("post-acute care"),
                "Permitted: post-acute care market");
    }

    @Test
    @DisplayName("Scope restriction explicitly names off-topic categories to refuse")
    void scopeRestriction_namesOffTopicCategories() {
        String lower = SI.toLowerCase();
        assertTrue(lower.contains("politic"),      "Must name politics as off-topic");
        assertTrue(lower.contains("sport"),        "Must name sports as off-topic");
        assertTrue(lower.contains("entertainment"),"Must name entertainment as off-topic");
    }

    @Test
    @DisplayName("Scope restriction contains the exact redirect response text")
    void scopeRestriction_containsRedirectResponse() {
        assertTrue(SI.contains("I'm Clinware AI, focused exclusively"),
                "Must contain the exact off-topic redirect message");
    }

    @Test
    @DisplayName("Scope restriction says MUST respond (not 'should' or 'try to')")
    void scopeRestriction_usesMandatoryLanguage() {
        assertTrue(SI.contains("you MUST respond"),
                "Scope restriction must use 'MUST' to make it non-negotiable");
    }

    @Test
    @DisplayName("Scope restriction forbids exceptions regardless of framing")
    void scopeRestriction_forbidsExceptions() {
        assertTrue(SI.contains("Never make exceptions"),
                "Must explicitly forbid exceptions to the scope rule");
    }


    @Test
    @DisplayName("MCP_TIMEOUT_RESPONSE is non-blank")
    void mcpTimeoutResponse_isNonBlank() {
        assertNotNull(PromptLibrary.MCP_TIMEOUT_RESPONSE);
        assertFalse(PromptLibrary.MCP_TIMEOUT_RESPONSE.isBlank(),
                "MCP_TIMEOUT_RESPONSE must not be blank");
    }

    @Test
    @DisplayName("MCP_TIMEOUT_RESPONSE mentions retrying")
    void mcpTimeoutResponse_mentionsRetry() {
        assertTrue(PromptLibrary.MCP_TIMEOUT_RESPONSE.toLowerCase().contains("try again"),
                "Timeout response should advise user to try again");
    }

    @Test
    @DisplayName("NO_NEWS_RESPONSE is non-blank")
    void noNewsResponse_isNonBlank() {
        assertNotNull(PromptLibrary.NO_NEWS_RESPONSE);
        assertFalse(PromptLibrary.NO_NEWS_RESPONSE.isBlank(),
                "NO_NEWS_RESPONSE must not be blank");
    }

    @Test
    @DisplayName("NO_NEWS_RESPONSE offers to answer from built-in knowledge")
    void noNewsResponse_offersBuiltInKnowledge() {
        assertTrue(PromptLibrary.NO_NEWS_RESPONSE.contains("built-in knowledge"),
                "No-news response should offer built-in knowledge as a fallback");
    }

    @Test
    @DisplayName("SERVICE_UNAVAILABLE_RESPONSE is non-blank")
    void serviceUnavailableResponse_isNonBlank() {
        assertNotNull(PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE);
        assertFalse(PromptLibrary.SERVICE_UNAVAILABLE_RESPONSE.isBlank(),
                "SERVICE_UNAVAILABLE_RESPONSE must not be blank");
    }


    @Test
    @DisplayName("System instruction is at least 500 characters (not accidentally truncated)")
    void systemInstruction_isSubstantial() {
        assertTrue(SI.length() > 500,
                "System instruction seems too short — may have been accidentally truncated");
    }

    @Test
    @DisplayName("System instruction contains both domain areas")
    void systemInstruction_coversDoubleDomain() {
        assertTrue(SI.contains("CLINWARE COMPANY INTELLIGENCE"),
                "Must cover Clinware company intelligence domain");
        assertTrue(SI.contains("DISEASES") || SI.contains("HEALTHCARE RESEARCH"),
                "Must cover diseases / healthcare research domain");
    }
}
