package com.clinware.tools;

import com.google.gson.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("NewsToolExecutor.parseAndFormat")
class NewsToolExecutorTest {

    private NewsToolExecutor executor;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        
        executor = new NewsToolExecutor(null, 30);
    }


    @Test
    @DisplayName("Shape A: single text item returns found=true with that text")
    void shapeA_singleTextItem() {
        JsonObject input = gson.fromJson(
                "{\"content\":[{\"type\":\"text\",\"text\":\"AI in healthcare is growing.\"}]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "AI healthcare");

        assertTrue(result.found);
        assertTrue(result.content.contains("AI in healthcare is growing."));
        assertEquals(1, result.articleCount);
    }

    @Test
    @DisplayName("Shape A: multiple text items are concatenated")
    void shapeA_multipleTextItems() {
        JsonObject input = gson.fromJson(
                "{\"content\":["
                + "{\"type\":\"text\",\"text\":\"Article one.\"},"
                + "{\"type\":\"text\",\"text\":\"Article two.\"}"
                + "]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertTrue(result.found);
        assertTrue(result.content.contains("Article one."));
        assertTrue(result.content.contains("Article two."));
        assertEquals(2, result.articleCount);
    }

    @Test
    @DisplayName("Shape A: non-text type items are ignored")
    void shapeA_nonTextItemsFiltered() {
        JsonObject input = gson.fromJson(
                "{\"content\":["
                + "{\"type\":\"image\",\"data\":\"base64...\"},"
                + "{\"type\":\"text\",\"text\":\"Keep this.\"},"
                + "{\"type\":\"resource\",\"uri\":\"file:///x\"}"
                + "]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertTrue(result.found);
        assertEquals(1, result.articleCount);
        assertTrue(result.content.contains("Keep this."));
        assertFalse(result.content.contains("base64"));
    }

    @Test
    @DisplayName("Shape A: empty content array returns NO_RESULTS")
    void shapeA_emptyArray_returnsNoResults() {
        JsonObject input = gson.fromJson("{\"content\":[]}", JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertFalse(result.found);
        assertEquals("NO_RESULTS", result.content);
    }

    @Test
    @DisplayName("Shape A: content array with only non-text items returns NO_RESULTS")
    void shapeA_onlyNonTextItems_returnsNoResults() {
        JsonObject input = gson.fromJson(
                "{\"content\":[{\"type\":\"image\",\"data\":\"abc\"}]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertFalse(result.found);
        assertEquals("NO_RESULTS", result.content);
    }

    @Test
    @DisplayName("Shape B: full article object is formatted with all fields")
    void shapeB_fullArticle_allFieldsPresent() {
        JsonObject input = gson.fromJson(
                "{\"articles\":[{"
                + "\"title\":\"Clinware Raises Series B\","
                + "\"source\":\"TechCrunch\","
                + "\"date\":\"2025-01-15\","
                + "\"url\":\"https://techcrunch.com/clinware\","
                + "\"summary\":\"Clinware announced a $30M Series B round.\""
                + "}]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "Clinware");

        assertTrue(result.found);
        assertEquals(1, result.articleCount);
        String c = result.content;
        assertTrue(c.contains("Clinware Raises Series B"), "title missing");
        assertTrue(c.contains("TechCrunch"),               "source missing");
        assertTrue(c.contains("2025-01-15"),               "date missing");
        assertTrue(c.contains("https://techcrunch.com/clinware"), "url missing");
        assertTrue(c.contains("$30M Series B"),            "summary missing");
    }

    @Test
    @DisplayName("Shape B: missing optional fields fall back to placeholder text")
    void shapeB_missingOptionalFields_usePlaceholders() {

        JsonObject input = gson.fromJson(
                "{\"articles\":[{\"title\":\"Minimal Article\"}]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertTrue(result.found);
        assertEquals(1, result.articleCount);
        String c = result.content;
        assertTrue(c.contains("Minimal Article"));
        assertTrue(c.contains("Unknown source") || c.contains("Unknown date")
                || c.contains("No summary"),
                "Expected at least one placeholder for missing fields");
    }

    @Test
    @DisplayName("Shape B: description field used as summary fallback")
    void shapeB_descriptionFallbackForSummary() {
        JsonObject input = gson.fromJson(
                "{\"articles\":[{"
                + "\"title\":\"Test\","
                + "\"description\":\"Fallback description text.\""
                + "}]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertTrue(result.found);
        assertTrue(result.content.contains("Fallback description text."));
    }

    @Test
    @DisplayName("Shape B: multiple articles counted correctly")
    void shapeB_multipleArticles_correctCount() {
        JsonObject input = gson.fromJson(
                "{\"articles\":["
                + "{\"title\":\"Article A\",\"summary\":\"Summary A\"},"
                + "{\"title\":\"Article B\",\"summary\":\"Summary B\"},"
                + "{\"title\":\"Article C\",\"summary\":\"Summary C\"}"
                + "]}",
                JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertTrue(result.found);
        assertEquals(3, result.articleCount);
    }

    @Test
    @DisplayName("Shape B: empty articles array returns NO_RESULTS")
    void shapeB_emptyArray_returnsNoResults() {
        JsonObject input = gson.fromJson("{\"articles\":[]}", JsonObject.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertFalse(result.found);
        assertEquals("NO_RESULTS", result.content);
    }


    @Test
    @DisplayName("Shape C: raw non-blank string returns found=true")
    void shapeC_rawString_returnsFound() {
        JsonPrimitive input = new JsonPrimitive("Raw news content about Clinware.");

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "Clinware");

        assertTrue(result.found);
        assertTrue(result.content.contains("Raw news content about Clinware."));
        assertEquals(1, result.articleCount);
    }

    @Test
    @DisplayName("Shape C: blank string returns NO_RESULTS")
    void shapeC_blankString_returnsNoResults() {
        JsonPrimitive input = new JsonPrimitive("   ");

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertFalse(result.found);
        assertEquals("NO_RESULTS", result.content);
    }


    @Test
    @DisplayName("null input returns NO_RESULTS")
    void nullInput_returnsNoResults() {
        NewsToolExecutor.ToolResult result = executor.parseAndFormat(null, "test");

        assertFalse(result.found);
        assertEquals("NO_RESULTS", result.content);
    }

    @Test
    @DisplayName("JsonNull input returns NO_RESULTS")
    void jsonNullInput_returnsNoResults() {
        NewsToolExecutor.ToolResult result = executor.parseAndFormat(JsonNull.INSTANCE, "test");

        assertFalse(result.found);
        assertEquals("NO_RESULTS", result.content);
    }

    @Test
    @DisplayName("Raw JSON array is treated as content array")
    void rawJsonArray_treatedAsContentArray() {
        JsonArray input = gson.fromJson(
                "[{\"type\":\"text\",\"text\":\"Array item text.\"}]",
                JsonArray.class);

        NewsToolExecutor.ToolResult result = executor.parseAndFormat(input, "test");

        assertTrue(result.found);
        assertTrue(result.content.contains("Array item text."));
    }
}
