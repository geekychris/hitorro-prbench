#!/bin/bash
# MCP stdio server for PR Bench - wraps the REST API for Claude Code
# Usage: Add to .claude/settings.json under mcpServers

BASE="http://localhost:8090/api"

while IFS= read -r line; do
  method=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('method',''))" 2>/dev/null)
  id=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
  params=$(echo "$line" | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin).get('params',{})))" 2>/dev/null)

  case "$method" in
    "initialize")
      echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"pr-bench\",\"version\":\"1.0.0\"}}}"
      ;;
    "notifications/initialized")
      # no response needed
      ;;
    "tools/list")
      cat <<'TOOLS'
{"jsonrpc":"2.0","id":ID,"result":{"tools":[
{"name":"list_repos","description":"List repos. Params: search, tag, language, owner, isFork, hasNotes","inputSchema":{"type":"object","properties":{"search":{"type":"string"},"tag":{"type":"string"},"language":{"type":"string"},"owner":{"type":"string"}}}},
{"name":"get_repo","description":"Get a repo by ID","inputSchema":{"type":"object","properties":{"id":{"type":"number"}},"required":["id"]}},
{"name":"github_status","description":"Compare local vs GitHub state for a repo","inputSchema":{"type":"object","properties":{"id":{"type":"number"}},"required":["id"]}},
{"name":"add_tag","description":"Add a git tag to a repo (pushes to GitHub)","inputSchema":{"type":"object","properties":{"id":{"type":"number"},"tag":{"type":"string"}},"required":["id","tag"]}},
{"name":"remove_tag","description":"Remove a git tag from a repo (deletes from GitHub)","inputSchema":{"type":"object","properties":{"id":{"type":"number"},"tag":{"type":"string"}},"required":["id","tag"]}},
{"name":"set_description","description":"Set repo description (pushes to GitHub, max 350 chars)","inputSchema":{"type":"object","properties":{"id":{"type":"number"},"notes":{"type":"string"}},"required":["id","notes"]}},
{"name":"generate_description","description":"AI-generate description via Ollama by scanning repo","inputSchema":{"type":"object","properties":{"id":{"type":"number"},"save":{"type":"boolean","default":true}},"required":["id"]}},
{"name":"repo_stats","description":"Faceted stats: counts by owner, language, tag","inputSchema":{"type":"object","properties":{}}},
{"name":"sync_to_github","description":"Force-push description and tags to GitHub","inputSchema":{"type":"object","properties":{"id":{"type":"number"}},"required":["id"]}}
]}}
TOOLS
      # Replace ID placeholder
      sed -i '' "s/\"id\":ID/\"id\":$id/" /dev/stdout 2>/dev/null || true
      ;;
    "tools/call")
      tool=$(echo "$params" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null)
      args=$(echo "$params" | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin).get('arguments',{})))" 2>/dev/null)

      case "$tool" in
        "list_repos")
          query=$(echo "$args" | python3 -c "
import sys,json
a=json.load(sys.stdin)
p=[]
for k in ['search','tag','language','owner','isFork','hasNotes']:
    if k in a and a[k]: p.append(f'{k}={a[k]}')
print('&'.join(p))
")
          result=$(curl -s "$BASE/repos?$query")
          ;;
        "get_repo")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          result=$(curl -s "$BASE/repos/$rid")
          ;;
        "github_status")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          result=$(curl -s "$BASE/repos/$rid/github-status")
          ;;
        "add_tag")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          tag=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['tag'])")
          result=$(curl -s -X POST "$BASE/repos/$rid/tags" -H "Content-Type: application/json" -d "{\"tag\":\"$tag\"}")
          ;;
        "remove_tag")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          tag=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['tag'])")
          result=$(curl -s -X DELETE "$BASE/repos/$rid/tags/$tag")
          ;;
        "set_description")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          notes=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['notes'])")
          result=$(curl -s -X POST "$BASE/repos/$rid/notes" -H "Content-Type: application/json" -d "{\"notes\":$(echo "$notes" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().strip()))")}")
          ;;
        "generate_description")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          save=$(echo "$args" | python3 -c "import sys,json; print(str(json.load(sys.stdin).get('save',True)).lower())")
          result=$(curl -s -X POST "$BASE/repos/$rid/generate-description?save=$save")
          ;;
        "repo_stats")
          result=$(curl -s "$BASE/repos/meta/stats")
          ;;
        "sync_to_github")
          rid=$(echo "$args" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
          result=$(curl -s -X POST "$BASE/repos/$rid/sync-to-github")
          ;;
        *)
          result="{\"error\":\"unknown tool: $tool\"}"
          ;;
      esac

      echo "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":{\"content\":[{\"type\":\"text\",\"text\":$(echo "$result" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().strip()))")}]}}"
      ;;
  esac
done
