package com.clinware.ui;


public final class TerminalOutput implements AgentOutput {

    @Override public void thinkingFirst()                      { Terminal.thinkingFirst(); }
    @Override public void thinkingSynth()                      { Terminal.thinkingSynth(); }
    @Override public void warn(String text)                    { Terminal.warn(text); }
    @Override public void error(String text)                   { Terminal.error(text); }
    @Override public void agentAnswer(String text)             { Terminal.agentAnswer(text); }
    @Override public void toolCalling(String name, String kw)  { Terminal.toolCalling(name, kw); }
    @Override public void toolResult(boolean found, int count) { Terminal.toolResult(found, count); }
    @Override public void toolFallback(String newKeyword)      { Terminal.toolFallback(newKeyword); }
}
