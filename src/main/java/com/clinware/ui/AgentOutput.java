package com.clinware.ui;

public interface AgentOutput {

    /** Shown before the first Gemini call  */
    void thinkingFirst();

    /** Shown before Gemini calls that follow a tool-result injection. */
    void thinkingSynth();

    /** Non-fatal warning */
    void warn(String text);

    /** Non-fatal error  */
    void error(String text);

    /** Prints / displays the agent's final Markdown answer. */
    void agentAnswer(String text);

    /** Shown when a tool call is about to be executed. */
    void toolCalling(String toolName, String keyword);

    /** Shown after a tool call returns. */
    void toolResult(boolean found, int count);

    /** Shown when a keyword fallback is triggered. */
    void toolFallback(String newKeyword);
}
