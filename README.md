# MCP Browser Server - An agentic coding experiment in clojure

## What is this thing?
This project is an MCP server written in clojure that exposes some tools to LLMs via MCP. In particular, the tools exposed allow an agent to access URLs via the text-based browsers w3m and lynx. 

Think of it as giving your LLM the ability to actually browse the web as text - perfect for research, documentation reading, or any task where the AI needs to fetch current web content.

## Warning!!!
This was 100% vibe coded using agentic programming via LLMs. I have never coded a project in clojure before, I barely understand the language and the MCP protocol.

I should never have been given the keys to this thing ;)

**USE AT YOUR OWN RISK! YOU HAVE REALLY BEEN WARNED!**

That said, it has 38 tests with 264 assertions, all passing. So it's *probably* fine. Probably.

## Features

- **Browse webpages** - Fetch any URL as clean text (no JavaScript, no ads, just content)
- **Session management** - Create persistent browsing sessions to navigate multiple pages
- **Dual browser support** - Use either w3m or lynx (your choice, we don't judge)
- **Cross-platform** - Works on Linux, macOS, and Windows (via WSL2)
- **Mock mode** - Runs tests even without browsers installed
- **Comprehensive logging** - See exactly what the LLM is asking for
- **MCP compliant** - Works with Claude Desktop, gptel, or any MCP client

## Available Tools

### 1. `browse_page`
Fetch a webpage and return its text content. Quick and simple.

**Parameters:**
- `url` (required): The URL to fetch
- `browser` (optional): "w3m" or "lynx" (default: "w3m")

**Example:**
```json
{
  "name": "browse_page",
  "arguments": {
    "url": "https://example.com",
    "browser": "w3m"
  }
}
```

### 2. `create_session`
Start a browsing session for multi-page navigation.

**Parameters:**
- `browser` (optional): "w3m" or "lynx" (default: "w3m")

**Returns:** A session ID for use with other session commands

### 3. `navigate`
Navigate to a URL within an existing session.

**Parameters:**
- `session_id` (required): Session ID from create_session
- `url` (required): URL to navigate to

### 4. `get_session_info`
Get details about a browsing session (history, uptime, current page, etc.)

**Parameters:**
- `session_id` (required): Session ID to query

### 5. `close_session`
Close a session and cleanup resources.

**Parameters:**
- `session_id` (required): Session ID to close

## Installation

### Prerequisites

1. **Leiningen** (Clojure build tool)
   ```bash
   # Linux/WSL2
   curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > ~/bin/lein
   chmod +x ~/bin/lein
   lein
   
   # macOS
   brew install leiningen
   ```

2. **w3m and/or lynx** (text-based browsers)
   ```bash
   # Ubuntu/Debian/WSL2
   sudo apt install w3m lynx
   
   # macOS
   brew install w3m lynx
   
   # Arch Linux (because of course you'd mention that)
   sudo pacman -S w3m lynx
   ```

3. **Java** (version 8 or higher)
   ```bash
   # Ubuntu/Debian
   sudo apt install openjdk-11-jdk
   
   # macOS (probably already installed)
   brew install openjdk@11
   ```

### Setup

```bash
# Clone or download this repo
cd mcp-browser

# Install dependencies
lein deps

# Run tests (because we're responsible developers... sometimes)
lein test

# Should see: "Ran 38 tests containing 264 assertions. 0 failures, 0 errors."
```

## Usage

### Running the Server

The server communicates via stdin/stdout using JSON-RPC 2.0:

```bash
# Start the server
lein run
```

The server will wait for JSON-RPC messages on stdin and respond on stdout.

### Testing Manually

Create a test file `test.json`:
```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
```

Then:
```bash
cat test.json | lein run
```

You should get a JSON response with server capabilities.

### Using with Claude Desktop

Add this to your Claude Desktop MCP settings (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "browser": {
      "command": "lein",
      "args": ["run"],
      "cwd": "/path/to/mcp-browser"
    }
  }
}
```

Restart Claude Desktop, and the browser tools should appear!

### Using with gptel in Emacs

You are on your own at the moment, but no doubt I will come back with a vibe coded tutorial. I used gptel and mcp in emacs to use the tool. I hooked it up to gemini and it seemed to work as expected.

(Translation: I got it working but can't remember exactly how. Will document later. Maybe. Probably not.)

### Using with Any MCP Client

Since this is a standard MCP server, it works with any client that supports the protocol:

```bash
# Generic MCP client example
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | lein run
```

The server implements:
- `initialize` - MCP handshake
- `tools/list` - List available tools
- `tools/call` - Execute a tool
- `ping` - Health check

## Example Conversation with an LLM

**Human:** Can you check what's on example.com?

**Claude (via MCP):** Let me browse that page for you.
*[Calls browse_page tool]*

**Result:** 
```
Example Domain

This domain is for use in documentation examples without needing permission.
Avoid use in operations.

Learn more
```

**Claude:** The page shows that example.com is a domain reserved for documentation...

## Project Structure

```
mcp-browser/
+-- project.clj                          # Leiningen project config
+-- README.md                            # This file!
+-- src/
¦   +-- mcp_browser/
¦       +-- core.clj                     # Main entry point
¦       +-- browser_management.clj       # Browser process management
¦       +-- protocol.clj                 # MCP/JSON-RPC protocol
¦       +-- tools.clj                    # Tool implementations
+-- test/
    +-- mcp_browser/
        +-- browser_management_test.clj  # Browser tests
        +-- protocol_test.clj            # Protocol tests
        +-- tools_test.clj               # Tool tests
        +-- integration_test.clj         # End-to-end tests
```

## Architecture

### Layer 1: Browser Management
- Spawns and manages w3m/lynx processes
- Tracks browsing sessions with history
- Handles process lifecycle and cleanup
- Mock mode for testing without browsers

### Layer 2: MCP Protocol
- JSON-RPC 2.0 message handling
- Request routing and validation
- Error handling and responses
- Tool registration and discovery

### Layer 3: Tools
- Implements the 5 browsing tools
- Translates between MCP and browser layer
- Formats responses for LLMs

## Development

### Running Tests

```bash
# Run all tests
lein test

# Run specific test namespace
lein test mcp-browser.protocol-test

# Run with auto-reload (requires lein-test-refresh)
lein test-refresh
```

### REPL Development

```bash
# Start REPL
lein repl

# Load and test functions
(require '[mcp-browser.browser-management :as bm])
(bm/fetch-url :w3m "https://example.com")

# Create a session
(def session-id (bm/create-session :w3m))
(bm/navigate-session session-id "https://example.com")
(bm/get-session-stats session-id)
(bm/cleanup-session session-id)
```

### Building an Uberjar

```bash
# Create standalone JAR
lein uberjar

# Run it
java -jar target/uberjar/mcp-browser-0.1.0-SNAPSHOT-standalone.jar
```

## Troubleshooting

### "Cannot run program 'w3m'"
Install w3m or lynx. Or both. More is better.

### "Session not found"
Sessions are ephemeral. If you restart the server, all sessions are lost. This is by design (or lazy coding, you decide).

### Tests fail with "Process not alive"
You're on Windows without WSL2, or the browsers aren't installed. Tests automatically use mock mode if browsers aren't available.

### "Too many open files"
Close some browser sessions. Or increase your ulimit. Or stop browsing so much.

### It's not working
1. Check that lein and Java are installed
2. Make sure w3m or lynx work from command line
3. Run `lein test` to verify everything works
4. Read the logs (they're verbose for a reason)
5. Remember the warning at the top of this README

## Known Limitations

- **No JavaScript support** - w3m and lynx don't run JavaScript (that's a feature, not a bug)
- **No image rendering** - It's text-only (again, feature not bug)
- **Sessions don't persist** - Restart the server, lose all sessions
- **No authentication support** - Can't login to sites (yet)
- **Rate limiting** - Not implemented (please be nice to websites)
- **robots.txt** - Not checked (we're rebels like that)

## Why This Exists

Because sometimes you just want to let your LLM read a webpage without spinning up a whole browser automation framework. Also, it was a fun way to learn Clojure via aggressive prompting of other LLMs.

## Contributing

PRs welcome, but remember: this entire thing was vibe coded. Your PR might be reviewed by an LLM. Or me, after 3 coffees. Equally unreliable.

## License

EPL-2.0 (same as Clojure)

## Acknowledgments

- The Clojure community, for making a language that somehow works even when you don't understand it
- w3m and lynx developers, for browsers that do one thing well
- Anthropic, for MCP and Claude (and for patiently answering my terrible questions)
- The LLM that wrote most of this code while I nodded along pretending to understand

## Support

File an issue if something breaks. Or doesn't work. Or works but shouldn't. Or if you have questions about why any of this was a good idea.

---

**Remember:** This was 100% agentic coding. If it breaks, blame the AI. If it works, I take full credit. That's how this works, right?

*Built with Claude, tested with determination, deployed with hope.*