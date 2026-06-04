#!/bin/bash
# PostToolUse hook — fires after every Bash tool call.
# If the command was a git commit and .java files were touched, remind to update CLAUDE.md.

command=$(python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('command', ''))
except Exception:
    pass
")

# Only act on git commit commands
if ! echo "$command" | grep -qE "git.+commit"; then
  exit 0
fi

# Check what files changed in the last commit
repo_root=$(git rev-parse --show-toplevel 2>/dev/null)
changed=$(git -C "$repo_root" diff HEAD~1 --name-only 2>/dev/null | grep '\.java$' || true)

if [ -n "$changed" ]; then
  echo ""
  echo "Java files were committed. If any architectural patterns, module ownership, or Phase 4 steps changed, update CLAUDE.md."
fi
