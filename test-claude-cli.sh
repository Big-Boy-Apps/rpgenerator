#!/bin/bash
# Test script to debug Claude CLI integration

echo "Testing Claude CLI integration..."
echo ""

# Test 1: Basic Claude CLI
echo "Test 1: Basic Claude CLI"
echo "Tell me a very short story" | claude --print --tools "" 2>&1 | head -5
echo ""

# Test 2: With system prompt
echo "Test 2: With system prompt"
echo "Tell me a very short story" | claude --print --system-prompt "You are a game master" --tools "" 2>&1 | head -5
echo ""

# Test 3: Run the actual CLI with --claude-code
echo "Test 3: Running RPGenerator with --claude-code (will start the game)"
echo "This should start the game interface..."
./cli/build/install/cli/bin/cli --claude-code
