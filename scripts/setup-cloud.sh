#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Each task is isolated. Reuse this checkout; do not create a worktree.
export NPM_CONFIG_CACHE=/workspace/.npm
export BUILDX_CONFIG=/workspace/.docker-buildx
mkdir -p /workspace/tools
if [ ! -x /workspace/tools/apache-maven-3.9.11/bin/mvn ]; then
 curl -fsSL https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz -o /tmp/nf-maven.tar.gz
 printf '%s  %s\n' 'bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978' /tmp/nf-maven.tar.gz | sha512sum -c -
 tar -xzf /tmp/nf-maven.tar.gz -C /workspace/tools
fi
if [ ! -x /workspace/tools/jdk-21-lts/bin/javac ]; then
 docker pull maven:3.9.16-eclipse-temurin-21
 nf_jdk_container=$(docker create maven:3.9.16-eclipse-temurin-21)
 trap 'docker rm "$nf_jdk_container" >/dev/null' EXIT
 docker cp "$nf_jdk_container:/opt/java/openjdk" /workspace/tools/jdk-21-lts
 docker rm "$nf_jdk_container" >/dev/null
 trap - EXIT
fi
python - <<'PYCONFIG'
import os, socket, urllib.parse, xml.etree.ElementTree as E
from pathlib import Path
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
root=E.Element('settings')
if proxy.hostname:
 entry=E.SubElement(E.SubElement(root,'proxies'),'proxy')
 for key,value in {'id':'cloud','active':'true','protocol':'http','host':proxy.hostname,'port':str(proxy.port or 80),'nonProxyHosts':'localhost|127.0.0.1'}.items():E.SubElement(entry,key).text=value
Path('/workspace/tools/maven-settings.xml').write_text(E.tostring(root,encoding='unicode'))
if proxy.hostname:entry.find('host').text=socket.gethostbyname(proxy.hostname)
Path('/workspace/tools/maven-settings-docker.xml').write_text(E.tostring(root,encoding='unicode'))
local=Path('.env')
if not local.exists() and not os.environ.get('DATABASE_PASSWORD'):
 import secrets
 local.write_text('DATABASE_PASSWORD='+secrets.token_urlsafe(32)+'\nDATABASE_USER=negocio_facil\nDATABASE_NAME=negocio_facil\nAPP_ORIGIN=http://localhost:5173\n')
 local.chmod(0o600)
PYCONFIG
npm ci --prefix frontend --no-audit --no-fund
npm run build --prefix frontend
scripts/cloud-maven -B -f backend/pom.xml package -DskipTests
