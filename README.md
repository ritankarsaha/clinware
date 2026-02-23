# Clinware Intelligence Agent

A **Java-based AI agent** that acts as a Market Intelligence Researcher for Clinware. It accepts natural-language queries, fetches live news via an MCP news server, and returns grounded, summarized responses powered by Google Gemini. Available in both a terminal REPL and a full Swing GUI.

---

## What It Does

- Answers questions about **Clinware** — products, funding, partnerships, market position, competitors
- Covers **any disease, drug, or healthcare topic** — mechanisms, treatments, FDA approvals, clinical trials, drug pipelines
- Fetches **live news** via the Verge News MCP server and injects it into Gemini's context
- Maintains **full multi-turn conversation memory** — follow-up questions work naturally
- **Auto-saves sessions** and lets you browse, load, and continue past conversations

---

## Architecture

```
User query
    │
    ▼
ClinwareAgent  (Gemini tool-calling loop + multi-turn memory)
    │
    ├─── Google Gemini 2.5 Flash  ──  reasoning, synthesis, tool decisions
    │
    └─── NewsToolExecutor  ──  keyword fallback expansion, result formatting
              │
              └─── McpStdioClient  ──stdio──►  node verge-news-mcp/build/index.js
                        │
                        └─── JSON-RPC 2.0 handshake + callTool()
```


## Prerequisites

| Requirement | Version | Install |
|---|---|---|
| Java JDK | 17+ | `brew install openjdk@17` (macOS) |
| Maven | 3.8+ | `brew install maven` |
| Node.js | LTS | `brew install node` (macOS) |
| Google API Key | — | [aistudio.google.com](https://aistudio.google.com/app/apikey) |

Verify:
```bash
java  -version   # must show 17+
mvn   -version
node  -version
```

---

## Setup

### 1. Clone the repository
```bash
git clone <repo-url>
cd clinware
```

### 2. Clone and build the MCP news server
```bash
cd ~/coding
git clone https://github.com/manimohans/verge-news-mcp
cd verge-news-mcp
npm install
npm run build
```

### 3. Create your environment file
```bash
cp .env.example .env
```

Edit `.env` and uncomment / set your Google API key:
```bash
export GOOGLE_API_KEY=AIza...your-key-here...
```

> **Never commit `.env` to git.** It is already listed in `.gitignore`.

**Alternative: YAML config file**

Instead of a `.env` file you can copy the YAML template:
```bash
cp .clinware.yml.example .clinware.yml
```
Then edit `.clinware.yml` and set `google_api_key`. Both approaches work; environment variables take priority over the YAML file.

### 4. Build the fat JAR
```bash
cd /path/to/clinware
mvn clean package -q
```

Produces `target/clinware-intelligence-agent-1.0.0.jar` with all dependencies bundled.

---

## Running

Always load your environment file first:
```bash
source .env
```

### Terminal mode (default)
```bash
java -jar target/clinware-intelligence-agent-1.0.0.jar
```

### GUI mode
```bash
source .env && java -jar target/clinware-intelligence-agent-1.0.0.jar --gui
```

### Non-interactive / CI mode
```bash
java -jar target/clinware-intelligence-agent-1.0.0.jar --no-interactive
```
Runs the demo query and exits immediately.

### Startup flags

| Flag | Effect |
|---|---|
| `--gui` | Launch the Swing GUI window instead of the terminal REPL |
| `--demo` | Force the demo query even when a previous session is restored |
| `--no-interactive` | Run demo query only, then exit (no REPL) |

---

## Terminal REPL

On startup the agent:
1. Loads configuration and prints a summary
2. Spawns the MCP news server and confirms the JSON-RPC handshake
3. Restores the previous session automatically (or runs a demo query on first launch)
4. Drops you into an interactive REPL

### Commands

| Input | Effect |
|---|---|
| Any text | Send query to the agent |
| `/help` | Print the full feature guide |
| `/history` | Show all conversation turns and the save-file path |
| `/save` | Flush the current session to disk immediately |
| `/reset` | Wipe conversation memory and start fresh |
| `/clear` | Clear the terminal screen (ANSI) |
| `exit` / `quit` | Shut down cleanly (session is auto-saved) |

### Keyboard shortcuts (raw-terminal mode)

| Key | Action |
|---|---|
| `↑` / `↓` | Cycle through past queries in this session |
| `Backspace` | Delete last character |
| `Ctrl-U` | Clear the entire current input line |
| `Ctrl-C` | Exit immediately (session auto-saved) |
| `Ctrl-D` | EOF — exits on an empty line |

---

## GUI Mode

Launch with `--gui`. The window is divided into a **chat pane** (left) and a **sidebar** (right).

```
┌─ Clinware Intelligence Agent ──[New Chat][Copy][Export][Help][Save][Reset]─┐
│  CHAT                                   │  [Turns │ Sessions]              │
│                                         │  ─────────────────────────────   │
│  ✦ Sending query to Gemini...           │  [1] You  › What is Clinware?    │
│  🔍 searchNews(keyword="Clinware AI")   │  [2] Agent › Clinware is a…      │
│  📰 Retrieved 3 result(s)               │  [3] You  › Tell me more...      │
│                                         │  ─────────────────────────────   │
│  Agent › [word-by-word typewriter]      │  3 turns  |  auto-saved          │
│                                         ├──────────────────────────────    │
│                                         │  2026-02-22  13:01  (8 turns)    │
│                                         │    "What is Clinware AI…"        │
│                                         │  [Load Selected]                 │
├─────────────────────────────────────────┴──────────────────────────────────┤
│  [input field ……………………………………………………………………………………………………………] [Send]          │
└────────────────────────────────────────────────────────────────────────────┘
```

### Toolbar buttons

| Button | Effect |
|---|---|
| **New Chat** | Archives the current session, clears chat, starts fresh |
| **Copy** | Copies the last agent response to the clipboard |
| **Export** | Saves the full conversation to a `.txt` file |
| **Help** | Shows the feature guide in the chat pane |
| **Save** | Flushes the current session to disk right now |
| **Reset** | Clears conversation memory (same as New Chat but without archiving) |


### Sidebar — Sessions tab
Lists all archived past sessions, newest first, with:
- Date and time
- Turn count
- Preview of the first user message

Click a session and press **Load Selected** to restore it into the chat pane and resume the conversation.

### Slash commands in the input field
You can also type these directly into the input box:

| Command | Effect |
|---|---|
| `/new` | New Chat (archive + fresh start) |
| `/reset` | Reset memory |
| `/save` | Save session |
| `/copy` | Copy last response |
| `/export` | Export chat to file |
| `/sessions` | Refresh the sessions list |
| `/help` | Show help |
| `/clear` | Clear chat pane |
| `exit` | Close the window |


---

## Session Persistence

Sessions are stored in `~/.clinware/`:

```
~/.clinware/
├── history.json                    # current / live session
└── sessions/
    ├── session_20260222_130101.json
    ├── session_20260221_094523.json
    └── ...
```

| Event | What happens |
|---|---|
| Normal exit (`exit`, `quit`, Ctrl-C, window close) | `history.json` is saved via JVM shutdown hook |
| `/save` or Save button | `history.json` is flushed immediately |
| New Chat | Current session is archived to `sessions/session_YYYYMMDD_HHmmss.json`, then cleared |
| Next launch | `history.json` is loaded automatically; demo query is skipped to preserve API quota |
| Load Session (GUI) | Selected archive is loaded and replayed in the chat pane |

**Format:** Each file is a JSON array of `{role, text}` objects. Only text-bearing turns are saved; tool-call and tool-response turns are transient and not persisted.

---

## How the Agent Loop Works

```
answer(userQuery)
    │
    ├── append user turn to history
    │
    └── loop (max 3 iterations):
            │
            ├── call Gemini with full history
            │       └── (one automatic retry on transient failure)
            │
            ├── response has function_call?
            │   ├── YES → extract keyword + days args
            │   │         call McpStdioClient.callTool("search-news")
            │   │         parse result (3 response shapes)
            │   │         inject Part.fromFunctionResponse() into history
            │   │         continue loop
            │   │
            │   └── NO  → break (final answer obtained)
            │
    ├── append model's final answer turn to history
    └── deliver answer (typewriter in GUI, ANSI print in terminal)
```

**On any error** (timeout, no results, API failure): `rollbackToIndex()` removes all turns added for the current query, leaving history clean for the next question.

### Keyword Fallback Expansion
If the primary search keyword returns no results, `NewsToolExecutor` automatically retries with progressively broader fallbacks:

- **Clinware queries**: `"Clinware AI"` → `"post-acute care AI admissions"` → `"hospital SNF transition technology"`
- **Healthcare queries**: `"AI healthcare technology"` → `"medical technology innovation"` → `"digital health market trends"`

---

## Configuration

Configuration is resolved in this order (highest priority wins):

1. **Environment variables**
2. **`.clinware.yml`** in the current working directory
3. **`~/.clinware.yml`** in the home directory
4. **Built-in defaults**

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `GOOGLE_API_KEY` | *(required)* | Gemini API key from Google AI Studio |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Gemini model ID |
| `MCP_TIMEOUT_MS` | `15000` | MCP tool response timeout in milliseconds |
| `NEWS_SEARCH_DAYS` | `30` | How many days back to search for news |
| `MCP_SERVER_PATH` | `~/coding/verge-news-mcp/build/index.js` | Path to the compiled MCP server entry point |

### YAML config file (alternative to env vars)

Create `.clinware.yml` in the project root (or `~/.clinware.yml` for user-level config):

```yaml
google_api_key: "AIza...your-key..."
gemini_model: "gemini-2.5-flash"
mcp_timeout_ms: 20000
news_search_days: 14
```

---

## MCP Integration Details

The agent uses **Model Context Protocol (MCP)** to communicate with the news server over **stdio** using **JSON-RPC 2.0**.

### Startup handshake (3 steps)
```
Java → MCP:  {"jsonrpc":"2.0","id":1,"method":"initialize","params":{...capabilities...}}
MCP  → Java: {"jsonrpc":"2.0","id":1,"result":{...server capabilities...}}
Java → MCP:  {"jsonrpc":"2.0","method":"notifications/initialized"}   ← no response
```

### Tool call
```
Java → MCP:  {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search-news","arguments":{"keyword":"Clinware","days":30}}}
MCP  → Java: {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"...articles..."}]}}
```

### Response shapes handled
The parser in `NewsToolExecutor.parseAndFormat()` handles three possible MCP response shapes:

| Shape | Description |
|---|---|
| `{"content":[{"type":"text","text":"..."}]}` | Standard MCP content array |
| `{"articles":[{"title":"...","source":"...","date":"...","url":"...","summary":"..."}]}` | Article object array |
| Raw string / primitive | Plain text fallback |

---

## Error Handling Reference

| Scenario | Behavior |
|---|---|
| `GOOGLE_API_KEY` missing | Exits immediately with a clear setup message |
| Node.js not installed | Exits with install instructions |
| MCP process fails to start | `IOException` caught; exits with message |
| MCP handshake timeout | `IOException` thrown; exits with message |
| MCP tool call timeout | Returns `MCP_TIMEOUT_RESPONSE` fallback message; continues session |
| No news after all fallback keywords | Returns `NO_NEWS_RESPONSE` fallback; continues session |
| Gemini API transient failure (attempt 1) | Retries after delay (1.5 s default; parses `429` suggested wait if present) |
| Gemini API down after both attempts | Returns `SERVICE_UNAVAILABLE_RESPONSE`; rolls back history |
| Any unexpected exception in agent loop | History rolled back to pre-query state; session continues |
| Bad YAML config file | Warning logged; falls through to defaults; never crashes startup |

---

## Project Structure

```
cliware/
├── src/
│   ├── main/java/com/clinware/
│   │   ├── Main.java                        # entry point — wires all layers, runs REPL/GUI
│   │   ├── agent/
│   │   │   ├── ClinwareAgent.java           # agentic tool-calling loop + history management
│   │   │   ├── PromptLibrary.java           # system instruction + fallback response strings
│   │   │   └── SessionStore.java            # save/load/archive conversation history
│   │   ├── config/
│   │   │   └── AgentConfig.java             # env-var + YAML config with resolution chain
│   │   ├── mcp/
│   │   │   ├── McpStdioClient.java          # JSON-RPC 2.0 stdio client with timeout
│   │   │   ├── McpRequest.java              # outgoing request POJO
│   │   │   └── McpResponse.java             # incoming response POJO
│   │   ├── tools/
│   │   │   └── NewsToolExecutor.java        # tool dispatch, fallback expansion, result parsing
│   │   └── ui/
│   │       ├── AgentOutput.java             # output interface (terminal vs GUI)
│   │       ├── AgentWindow.java             # Swing GUI window
│   │       ├── MarkdownRenderer.java        # strips markdown for plain-text display
│   │       ├── QueryConsole.java            # raw-mode terminal input with history
│   │       ├── Terminal.java                # ANSI-colored terminal output helpers
│   │       └── TerminalOutput.java          # AgentOutput implementation for terminal
│   └── test/java/com/clinware/
│       ├── config/
│       │   └── AgentConfigTest.java         # hermetic config resolution tests
│       └── tools/
│           └── NewsToolExecutorTest.java    # unit tests for all 3 MCP response shapes
├── .env.example                             # copy to .env and fill in your API key
├── .gitignore
└── pom.xml                                  # Maven build — Java 17, fat JAR via Shade plugin
```

---

## Running Tests

```bash
mvn test
```

The test suite covers:
- **`AgentConfigTest`** — config resolution priority (env → YAML → default), missing key detection, numeric parsing
- **`NewsToolExecutorTest`** — all three MCP response shapes, empty results, null input, fallback annotation

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `com.google.genai:google-genai` | 1.5.0 | Google GenAI Java SDK (Gemini) |
| `com.google.code.gson:gson` | 2.10.1 | JSON-RPC serialization / MCP response parsing |
| `org.slf4j:slf4j-simple` | 2.0.9 | Logging |
| `org.yaml:snakeyaml` | 2.2 | `.clinware.yml` config file parsing |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | Unit tests |

Build tooling: **Maven 3.8+** with the **Shade plugin** for fat JAR assembly.

---

## Troubleshooting

**`GOOGLE_API_KEY is not set`**
```bash
source .env   # must be run in the same shell session before java -jar
```

**`Could not start MCP server`**
```bash
# Make sure the server is built
cd ~/coding/verge-news-mcp && npm install && npm run build

# Or point to a custom location
export MCP_SERVER_PATH=/path/to/your/verge-news-mcp/build/index.js
```

**MCP times out on every query**
```bash
export MCP_TIMEOUT_MS=30000   # increase to 30 seconds
```
Also check your internet connection — the MCP server fetches live news remotely.


**Session not restoring on next launch**
- The session is saved on clean exit (`exit`, `quit`, window close, Ctrl-C)
- Verify `~/.clinware/history.json` exists after exiting
- If you killed the process with `kill -9`, the shutdown hook won't run — use `/save` during your session

**SLF4J / logging noise**
- MCP server stderr is drained to the SLF4J logger at DEBUG level — expected and harmless
- To suppress: set `org.slf4j.simpleLogger.defaultLogLevel=warn` in your JVM args

---

## Implementation

### Overview

 A Java agent that fetches live news via an MCP server and synthesises it into grounded, coherent answers using Gen AI. The implementation is split into four clearly separated layers that communicate through well-defined interfaces.

---

### Layer 1 — Configuration (`AgentConfig`)

`AgentConfig` reads settings from three sources in priority order: **environment variables** > **YAML file** (`.clinware.yml`) > **built-in defaults**. The YAML file is parsed using SnakeYAML's `SafeConstructor`, which prevents YAML-deserialization attacks. Numeric fields fall back to defaults when the raw string is not a valid number rather than crashing.

---

### Layer 2 — MCP Client (`McpStdioClient`, `McpRequest`, `McpResponse`)

The agent spawns the Verge News MCP server (`node verge-news-mcp/build/index.js`) as a child process and communicates with it using **JSON-RPC 2.0 over stdio** — exactly as the MCP specification mandates.

**Startup handshake (3 steps):**
1. Send `initialize` request with protocol version and client info.
2. Receive the server's capability response.
3. Send `notifications/initialized` (one-way, no response expected).

Each request carries a increasing integer ID (`AtomicInteger`). Responses are read on a pooled background thread (`ExecutorService`) and retrieved with `Future.get(timeoutMs, ...)`. If the server does not respond within `MCP_TIMEOUT_MS`, the future is cancelled and a `TimeoutException` propagates to the caller.

---

### Layer 3 — Tool Execution (`NewsToolExecutor`)

`NewsToolExecutor` bridges the Gen AI function-call response to the MCP transport layer:

1. Extracts `keyword` and `days` from Gemini's `FunctionCall` args (with safe defaults).
2. Calls `McpStdioClient.callTool("search-news", ...)`.
3. Parses the raw `JsonElement` response, which may arrive in three shapes:
   - **Shape A** — `{"content":[{"type":"text","text":"..."}]}` (standard MCP)
   - **Shape B** — `{"articles":[{...}]}` (article-object array)
   - **Shape C** — raw string or primitive
4. If no results are returned, automatically retries with progressively broader **fallback keywords** before giving up:
   - Clinware queries: `"Clinware AI"` → `"post-acute care AI admissions"` → `"hospital SNF transition technology"`
   - Healthcare queries: `"AI healthcare technology"` → `"medical technology innovation"` → `"digital health market trends"`

---

### Layer 4 — Agent Loop (`ClinwareAgent`)

The agent drives an **agentic tool-calling loop** built on top of the Google GenAI Java SDK:

```
append user turn to history
loop (max 3 iterations):
    call Gemini with full history + tool definition
    if response contains function_call:
        call NewsToolExecutor.execute()
        inject Part.fromFunctionResponse() into history
        continue loop
    else:
        break → final answer obtained
append model's answer to history
```

**Key design decisions:**

| Decision | Rationale |
|---|---|
| Multi-turn `history` list | Enables follow-up questions; all turns (user, model, tool-result) are accumulated in a single `List<Content>` and sent with every Gemini call. |
| `rollbackToIndex()` on error | If any step fails, all turns added for the current query are removed, keeping history clean for the next question. |
| One-retry on Gemini failure | Transient HTTP errors (rate-limit `429`, network blip) are retried after a delay parsed from the response body; otherwise 1.5 s. |
| `AgentMode` enum | Allows switching between MCP-only, Google-Search-grounding-only, and hybrid at runtime without restarting. In hybrid mode, if MCP returns nothing the agent transparently falls back to Google Search. |
| `FunctionDeclaration` on the SDK | The tool is declared using `FunctionDeclaration.builder()` with a typed `Schema` (OBJECT with `keyword: STRING` and `days: INTEGER`). This is what causes Gemini to emit a structured `FunctionCall` part rather than hallucinating tool invocations in plain text. |

---

### Prompt Engineering (`PromptLibrary`)

The system instruction is structured into four named sections:

1. **YOUR IDENTITY** — strict rules preventing the agent from ever claiming to be Gemini or a Google product.
2. **CLINWARE COMPANY INTELLIGENCE** — directs the model to **always** call `searchNews("Clinware")` first, then surface funding, product launches, partnerships, and market moves.
3. **DISEASES, CURES, AND HEALTHCARE RESEARCH** — extends the agent to the broader healthcare market; uses `searchNews` for recent events, internal knowledge for established medicine.
4. **SCOPE RESTRICTION** — hard block: the agent refuses all off-topic questions (politics, sports, entertainment, etc.) with an exact canned response. `MUST` language and explicit prohibition of exceptions make this non-negotiable even under adversarial prompting.

---

### Session Persistence (`SessionStore`)

Every text-bearing conversation turn is serialised to `~/.clinware/history.json` as a JSON array of `{role, text}` objects using Gson. Tool-call turns and tool-response turns are intentionally excluded (they are transient and regenerated on the next run). A JVM shutdown hook ensures the file is flushed on clean exit (`exit`, `quit`, `Ctrl-C`). "New Chat" archives the current session to a timestamped file under `~/.clinware/sessions/`.

---

### UI (`Terminal`, `QueryConsole`, `AgentWindow`)

- **`Terminal`** — ANSI-aware print helpers; detected automatically on macOS/Linux; gracefully degraded on Windows (no escape codes).
- **`QueryConsole`** — raw-terminal mode (via `stty raw -echo`) for arrow-key history navigation and `Ctrl-U` line-clear; falls back to `BufferedReader` on non-TTY environments (CI, pipes).
- **`AgentWindow`** — full Swing GUI (`JFrame`) with a `JTextPane` chat pane, a `JSplitPane` sidebar, `JTabbedPane` (Turns / Sessions), a mode selector `JComboBox`, and a typewriter-effect render for agent responses. All agent calls run on a dedicated daemon `ExecutorService` to keep the EDT free.

---

## Tech Stack

- **Language:** Java 17
- **AI SDK:** Google GenAI Java SDK 1.5.0 (Gemini 2.5 Flash)
- **Tool protocol:** MCP (Model Context Protocol) over stdio, JSON-RPC 2.0
- **News server:** `@manimohans/verge-news-mcp` (Node.js)
- **GUI:** Java Swing (JFrame, JTextPane, JSplitPane, JTabbedPane)
- **Build:** Maven 3 + Shade plugin (fat JAR)
- **Config:** Environment variables + SnakeYAML
- **Persistence:** Gson — JSON files in `~/.clinware/`
