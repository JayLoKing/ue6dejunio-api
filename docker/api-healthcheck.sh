#!/bin/bash
set -euo pipefail
timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/"${SERVER_PORT:-8090}"'
