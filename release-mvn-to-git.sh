#!/usr/bin/env bash
set -euo pipefail

# Remove target directory (protoc-dependencies should be gone now, or will be skipped)
rm -rf target

# Small delay on Windows to allow file handles to release
echo "Waiting 2 seconds for file handles to release..."
sleep 2

mvn -B release:prepare release:perform && git push && git pull
