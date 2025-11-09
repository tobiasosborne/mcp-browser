#!/bin/bash
# Test script for MCP Browser Server protocol

echo "Testing MCP Browser Server Protocol"
echo "===================================="
echo ""

# Test 1: Initialize
echo "Test 1: Initialize"
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}' | lein run
echo ""

# Test 2: List Tools
echo "Test 2: List Tools"
echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | lein run
echo ""

# Test 3: Ping
echo "Test 3: Ping"
echo '{"jsonrpc":"2.0","id":3,"method":"ping"}' | lein run
echo ""

echo "Tests complete!"
