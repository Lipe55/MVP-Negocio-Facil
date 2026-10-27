#!/bin/sh
set -eu
if [ -f /run/secrets/maven_settings ]; then
 export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/run/secrets/java_cacerts"
 exec mvn -s /run/secrets/maven_settings -B "$@"
fi
exec mvn -B "$@"
