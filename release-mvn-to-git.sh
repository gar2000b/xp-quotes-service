#!/usr/bin/env bash
set -euo pipefail

# On Windows, protoc-dependencies can be locked. Try to remove it with retries.
# Using Git Bash commands only (no PowerShell)
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || -n "${WINDIR:-}" ]]; then
  # Try multiple times to remove protoc-dependencies (Windows file locks can be transient)
  for i in {1..3}; do
    rm -rf target/protoc-dependencies 2>/dev/null && break || sleep 1
  done
fi

# Remove target directory (protoc-dependencies should be gone now, or will be skipped)
rm -rf target 2>/dev/null || true

# Small delay on Windows to allow file handles to release
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || -n "${WINDIR:-}" ]]; then
  sleep 2
fi

mvn -B release:prepare release:perform \
  && git push

