# MCP Browser Server - TODO List

## ✅ Completed (Session 1)
- [x] Project structure setup with Leiningen
- [x] Browser Management Layer (Layer 2) - COMPLETE
  - [x] Process spawning (w3m/lynx)
  - [x] Process lifecycle management
  - [x] Session creation and tracking
  - [x] Navigation history
  - [x] Resource cleanup
  - [x] Mock mode for testing without browsers
  - [x] Cross-platform support (Windows/Linux/Mac)
- [x] Complete test suite (40+ tests)
  - [x] All tests passing with mock mode
  - [x] Process lifecycle tests
  - [x] Session management tests
  - [x] Browser operations tests

## 🔄 Next Session Priorities

### 1. MCP Protocol Layer (HIGH PRIORITY)
**Goal:** Enable JSON-RPC communication over stdin/stdout

- [ ] Create `mcp_protocol.clj` namespace
- [ ] Implement JSON-RPC 2.0 message handling
  - [ ] Parse incoming requests from stdin
  - [ ] Format responses for stdout
  - [ ] Handle `initialize` handshake
  - [ ] Handle `tools/list` request
  - [ ] Handle `tools/call` request
- [ ] Error handling and validation
- [ ] Write protocol tests
- [ ] Create main entry point that connects protocol to browser layer

**Files to create:**
- `src/mcp_browser/protocol.clj`
- `test/mcp_browser/protocol_test.clj`

**Key functions needed:**
```clojure
(defn read-request [])           ; Read JSON-RPC from stdin
(defn write-response [response]) ; Write JSON-RPC to stdout
(defn handle-message [msg])      ; Route messages to handlers
(defn initialize [params])       ; MCP handshake
(defn list-tools [])            ; Return available tools
(defn call-tool [name params])  ; Execute tool and return result
```

### 2. Tool Implementation Layer (MEDIUM PRIORITY)
**Goal:** Define MCP tools that expose browser functionality

- [ ] Create `mcp_tools.clj` namespace
- [ ] Define tool schemas
  - [ ] `browse_page` - Simple fetch and dump
  - [ ] `create_session` - Start browsing session
  - [ ] `navigate` - Navigate to URL in session
  - [ ] `get_links` - Extract links from current page
  - [ ] `follow_link` - Click a numbered link
  - [ ] `get_session_info` - Session stats
- [ ] Wire tools to browser management layer
- [ ] Write tool tests

**Tool schema example:**
```clojure
{:name "browse_page"
 :description "Fetch a webpage as text"
 :inputSchema {:type "object"
               :properties {:url {:type "string"
                                  :description "URL to fetch"}
                           :browser {:type "string"
                                    :enum ["w3m" "lynx"]
                                    :default "w3m"}}
               :required ["url"]}}
```

### 3. Content Processing Layer (MEDIUM PRIORITY)
**Goal:** Parse and extract useful information from browser output

- [ ] Create `content_processor.clj` namespace
- [ ] Link extraction and numbering
  - [ ] Parse w3m link format
  - [ ] Parse lynx link format
  - [ ] Assign sequential numbers to links
- [ ] Table parsing and formatting
- [ ] Content sanitization
- [ ] Text summarization for long pages
- [ ] Write content processing tests

**Key functions:**
```clojure
(defn extract-links [text browser-type])
(defn number-links [links])
(defn parse-tables [text])
(defn sanitize-output [text])
(defn truncate-content [text max-length])
```

### 4. Interactive Browser Mode (LOW PRIORITY)
**Goal:** Enable multi-step browsing within a session

- [ ] Enhance browser spawning for interactive mode
- [ ] Send commands to running browser process
- [ ] Handle browser prompts and inputs
- [ ] Form filling capabilities
- [ ] Cookie management

### 5. Cache Layer (LOW PRIORITY)
**Goal:** Avoid redundant fetches

- [ ] Create `cache.clj` namespace
- [ ] LRU cache implementation
- [ ] TTL-based expiration
- [ ] Cache invalidation
- [ ] Write cache tests

### 6. Integration & Polish (ONGOING)
- [ ] Update `core.clj` as main entry point
- [ ] Add command-line argument parsing
- [ ] Logging configuration
- [ ] Rate limiting implementation
- [ ] robots.txt checking
- [ ] Documentation
  - [ ] API documentation
  - [ ] Usage examples
  - [ ] MCP client configuration
- [ ] Performance optimization

## 📋 Known Issues to Fix

1. **Test isolation:** Session count tests occasionally fail due to cleanup timing
   - Consider using `reset!` on sessions atom in fixture
   
2. **Mock mode indication:** Tests should log when using mock vs real browser
   - Add logging to indicate test mode

3. **Windows path handling:** Might need special handling for file paths
   - Test on actual Windows system with w3m installed

4. **Error messages:** Make error messages more helpful
   - Add context to exceptions

## 🎯 Immediate Next Steps (30 min session)

If you only have 30 minutes, do these in order:

1. **Create protocol.clj skeleton** (10 min)
   - Basic namespace and core functions
   - Read/write JSON-RPC stubs

2. **Implement `initialize` handshake** (10 min)
   - Handle MCP initialize request
   - Return capabilities

3. **Implement `tools/list`** (10 min)
   - Return list of available tools
   - Start with just `browse_page` tool

## 🎯 Full Session Next Steps (60+ min)

1. Complete MCP protocol layer (30 min)
2. Implement tool layer with basic browse_page (20 min)
3. Wire everything together and test end-to-end (10 min)

## 📚 Reference Materials

**MCP Specification:**
- https://spec.modelcontextprotocol.io/specification/
- JSON-RPC 2.0 format
- Tool schema format

**Testing MCP Server:**
```bash
# Install MCP CLI
npm install -g @modelcontextprotocol/cli

# Test server
mcp-cli --server "lein run"
```

**Example MCP initialize request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "test-client",
      "version": "1.0.0"
    }
  }
}
```

## 🔧 Development Commands

```bash
# Run tests
lein test

# Run specific test
lein test :only mcp-browser.browser-management-test/test-session-creation

# Start REPL
lein repl

# Run server (once core.clj is ready)
lein run

# Build uberjar
lein uberjar
```

## 💡 Notes & Ideas

- Consider adding a web UI for debugging (optional)
- Could support more browsers (links, elinks)
- Think about accessibility features (screen reader mode)
- Consider adding JavaScript support via Playwright (much later)
- Rate limiting per-session vs global?

## ⏱️ Time Estimates

- **Protocol Layer:** 30-45 min
- **Tool Layer:** 20-30 min  
- **Content Processing:** 45-60 min
- **Integration Testing:** 15-30 min
- **Documentation:** 30 min

**Total to MVP:** ~3-4 hours

## 🎉 Success Criteria

You'll know you're done when:
1. ✅ Can run `lein run` and server starts
2. ✅ Can send MCP initialize request and get response
3. ✅ Can call `browse_page` tool and get webpage text
4. ✅ Can create session and navigate within it
5. ✅ Can extract and follow links
6. ✅ All tests passing
7. ✅ Works with Claude Desktop or other MCP client
