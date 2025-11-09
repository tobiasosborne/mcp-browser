# MCP Browser Server - Quick Start

## Project Structure

```
mcp-browser/
├── project.clj
├── src/
│   └── mcp_browser/
│       └── browser_management.clj
└── test/
    └── mcp_browser/
        └── browser_management_test.clj
```

## Setup (5 minutes)

```bash
# 1. Create directory structure
mkdir -p mcp-browser/src/mcp_browser
mkdir -p mcp-browser/test/mcp_browser

# 2. Copy files into structure:
# - project.clj → mcp-browser/
# - browser_management.clj → mcp-browser/src/mcp_browser/
# - browser_management_test.clj → mcp-browser/test/mcp_browser/

# 3. Install dependencies
cd mcp-browser
lein deps

# 4. Make sure w3m or lynx is installed
# Ubuntu/Debian:
sudo apt install w3m lynx

# Mac:
brew install w3m lynx
```

## Running Tests

```bash
# Run all tests
lein test

# Run specific test namespace
lein test mcp-browser.browser-management-test

# Run with auto-reload
lein test-refresh
```

## REPL Usage

```clojure
# Start REPL
lein repl

# Load namespace
(require '[mcp-browser.browser-management :as bm])

# Quick test
(bm/fetch-url :w3m "https://example.com")

# Create a session
(def session-id (bm/create-session :w3m))

# Navigate
(bm/navigate-session session-id "https://example.com")

# Get stats
(bm/get-session-stats session-id)

# Cleanup
(bm/cleanup-session session-id)
```

## What's Working

✅ **Browser Process Management**
- Spawn w3m/lynx processes
- Track process lifecycle
- Kill processes cleanly

✅ **Session Management**
- Create/retrieve sessions
- Track navigation history
- Update session state
- Cleanup resources

✅ **Browser Operations**
- Fetch URLs in dump mode
- Navigate within sessions
- Read process output

✅ **Test Suite**
- 40+ test cases
- Process lifecycle tests
- Session management tests
- Resource limit tests

## What's Next

- [ ] MCP protocol layer (JSON-RPC)
- [ ] Interactive browser mode
- [ ] Link following
- [ ] Content processing (extract links, tables)
- [ ] Caching layer
- [ ] Rate limiting

## Current Status

**Layer 2 (Browser Management): COMPLETE** ✓

Ready to move on to:
- Layer 1: MCP Protocol
- Layer 3: Tool Implementation
- Layer 4: Content Processing
