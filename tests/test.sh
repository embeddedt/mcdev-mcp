#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BINARY="${SCRIPT_DIR}/../target/mcdev-mcp"

if [[ ! -x "$BINARY" ]]; then
    echo "ERROR: native binary not found at $BINARY" >&2
    echo "Run: mvn package -Pnative" >&2
    exit 1
fi

if ! command -v jq &>/dev/null; then
    echo "ERROR: jq is required but not installed" >&2
    exit 1
fi

# ── Counters ──────────────────────────────────────────────────────────────────
PASS=0
FAIL=0

# ── Helpers ───────────────────────────────────────────────────────────────────

# Send one or more newline-delimited JSON-RPC messages to the binary and print
# all responses.  Each argument is treated as one JSON-RPC line.
rpc() {
    printf '%s\n' "$@" | "$BINARY" 2>/dev/null
}

# Assert that a jq expression evaluates to true (jq exit 0) against a JSON string.
#   assert_jq <label> <json> <jq-expr>
assert_jq() {
    local label="$1" json="$2" expr="$3"
    if echo "$json" | jq -e "$expr" >/dev/null 2>&1; then
        echo "PASS: $label"
        (( PASS++ )) || true
    else
        local got
        got=$(echo "$json" | jq -r "$expr" 2>/dev/null || echo "<jq error>")
        echo "FAIL: $label"
        echo "      expr:     $expr"
        echo "      got:      $got"
        echo "      response: $json"
        (( FAIL++ )) || true
    fi
}

# ── Tests ─────────────────────────────────────────────────────────────────────

echo "── ping ─────────────────────────────────────────────────────────────────"

response=$(rpc '{"jsonrpc":"2.0","id":1,"method":"ping"}')
assert_jq "ping: jsonrpc version"   "$response" '.jsonrpc == "2.0"'
assert_jq "ping: id echoed"         "$response" '.id == 1'
assert_jq "ping: result present"    "$response" '.result != null'
assert_jq "ping: no error field"    "$response" 'has("error") | not'

echo
echo "── initialize ───────────────────────────────────────────────────────────"

response=$(rpc '{"jsonrpc":"2.0","id":2,"method":"initialize"}')
assert_jq "initialize: protocolVersion is 2024-11-05"  "$response" '.result.protocolVersion == "2024-11-05"'
assert_jq "initialize: serverInfo.name is mcdev-mcp"   "$response" '.result.serverInfo.name == "mcdev-mcp"'
assert_jq "initialize: capabilities has tools"         "$response" '.result.capabilities | has("tools")'
assert_jq "initialize: instructions non-empty"         "$response" '(.result.instructions | length) > 0'

echo
echo "── tools/list ───────────────────────────────────────────────────────────"

response=$(rpc '{"jsonrpc":"2.0","id":3,"method":"tools/list"}')
assert_jq "tools/list: no error"                             "$response" 'has("error") | not'
assert_jq "tools/list: exactly one tool"                     "$response" '(.result.tools | length) == 1'
assert_jq "tools/list: tool name is get_minecraft_source_path" "$response" '.result.tools[0].name == "get_minecraft_source_path"'
assert_jq "tools/list: tool has description"                 "$response" '(.result.tools[0].description | length) > 0'
assert_jq "tools/list: inputSchema type is object"           "$response" '.result.tools[0].inputSchema.type == "object"'
assert_jq "tools/list: schema has minecraftVersion field"    "$response" '.result.tools[0].inputSchema.properties | has("minecraftVersion")'
assert_jq "tools/list: schema has loader field"              "$response" '.result.tools[0].inputSchema.properties | has("loader")'
assert_jq "tools/list: loader enum includes VANILLA"         "$response" '[.result.tools[0].inputSchema.properties.loader.enum[] | select(. == "VANILLA")] | length == 1'
assert_jq "tools/list: loader enum includes NEOFORGE"        "$response" '[.result.tools[0].inputSchema.properties.loader.enum[] | select(. == "NEOFORGE")] | length == 1'

echo
echo "── error handling ───────────────────────────────────────────────────────"

response=$(rpc '{"jsonrpc":"2.0","id":4,"method":"no_such_method"}')
assert_jq "unknown method: error code is -32601"   "$response" '.error.code == -32601'
assert_jq "unknown method: error message present"  "$response" '(.error.message | length) > 0'

response=$(rpc '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"no_such_tool","arguments":{}}}')
assert_jq "unknown tool: returns error"            "$response" '.error != null'

echo
echo "── notification (no id) ─────────────────────────────────────────────────"

response=$(rpc '{"jsonrpc":"2.0","method":"ping"}')
if [[ -z "$response" ]]; then
    echo "PASS: notification produces no response"
    (( PASS++ )) || true
else
    echo "FAIL: notification should produce no response, got: $response"
    (( FAIL++ )) || true
fi

echo
echo "── multi-request session ────────────────────────────────────────────────"

responses=$(rpc \
    '{"jsonrpc":"2.0","id":10,"method":"ping"}' \
    '{"jsonrpc":"2.0","id":11,"method":"initialize"}' \
    '{"jsonrpc":"2.0","id":12,"method":"tools/list"}')

line_count=$(echo "$responses" | wc -l)
if [[ "$line_count" -eq 3 ]]; then
    echo "PASS: multi-request session returns 3 responses"
    (( PASS++ )) || true
else
    echo "FAIL: expected 3 responses, got $line_count"
    (( FAIL++ )) || true
fi

# Each line is independent valid JSON
while IFS= read -r line; do
    assert_jq "multi-request: each response has jsonrpc field" "$line" 'has("jsonrpc")'
done <<< "$responses"

echo
echo "── tools/call: 1.20.1 forge 47.4.0 (slow - downloads & decompiles) ──────"

response=$(rpc '{"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"get_minecraft_source_path","arguments":{"minecraftVersion":"1.20.1","loader":"FORGE","loaderVersion":"47.4.0"}}}')
assert_jq "forge 1.20.1: no rpc error"       "$response" 'has("error") | not'
assert_jq "forge 1.20.1: isError is false"    "$response" '.result.isError == false'
assert_jq "forge 1.20.1: content is array"    "$response" '(.result.content | type) == "array"'
assert_jq "forge 1.20.1: content non-empty"   "$response" '(.result.content | length) > 0'
assert_jq "forge 1.20.1: content type is text" "$response" '.result.content[0].type == "text"'
assert_jq "forge 1.20.1: path non-empty"           "$response" '(.result.content[0].text | length) > 0'
assert_jq "forge 1.20.1: path contains version"   "$response" '.result.content[0].text | contains("1.20.1")'
assert_jq "forge 1.20.1: path contains loader"    "$response" '.result.content[0].text | contains("forge")'
# Note: the sources directory lives under SESSION_PATH (/tmp/mcdev-mcp) which is
# deleted by a shutdown hook when the server exits — it is only valid while the
# server is running, so we cannot check for it on the filesystem after the process ends.

# ── Summary ───────────────────────────────────────────────────────────────────
echo
echo "─────────────────────────────────────────────────────────────────────────"
echo "Results: $PASS passed, $FAIL failed"

[[ "$FAIL" -eq 0 ]]
