#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Use the platform's existing proxy. No credentials are saved or printed.
python - "$@" <<'PYBUILD'
import os, socket, subprocess, urllib.parse, sys
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
args=['docker','compose','-f','docker-compose.yml','-f','docker-compose.cloud.yml','build']
if proxy.hostname:
 url='http://'+socket.gethostbyname(proxy.hostname)+':'+str(proxy.port or 80)
 args+=['--build-arg','HTTPS_PROXY='+url,'--build-arg','HTTP_PROXY='+url]
args+=sys.argv[1:]
env=os.environ.copy();env['BUILDX_CONFIG']='/workspace/.docker-buildx'
raise SystemExit(subprocess.run(args,env=env).returncode)
PYBUILD
