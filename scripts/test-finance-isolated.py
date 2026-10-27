#!/usr/bin/env python3
"""Run backend verification against a disposable PostgreSQL, never loading .env.
Requires Python 3 and Docker. Only containers/network created here are removed.
Maven dependencies are cached in a dedicated test-only Docker volume.
"""
from pathlib import Path
import json
import shutil
import subprocess
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
work = Path(tempfile.mkdtemp(prefix='negocio-finance-validation-'))
suffix = uuid.uuid4().hex[:12]
network = 'nf-finance-net-' + suffix
database = 'nf-finance-db-' + suffix
runner = 'nf-finance-maven-' + suffix
cache_state = Path(tempfile.gettempdir()) / 'negocio-facil-finance-maven-cache.json'

def docker(*args, capture=True):
    return subprocess.run(['docker', *args], check=True, text=True,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None)

# Stage only explicit source/config extensions. Do not inspect .env variants,
# secrets, working-directory Docker Compose configuration or existing services.
shutil.copyfile(root / 'backend/pom.xml', work / 'pom.xml')
for directory, extensions in [('src/main/java', {'.java'}), ('src/test/java', {'.java'}),
                              ('src/main/resources', {'.sql', '.yml', '.yaml'})]:
    for path in (root / 'backend' / directory).rglob('*'):
        relative = path.relative_to(root / 'backend')
        if any(part.startswith('.env') for part in relative.parts) or path.is_symlink():
            continue
        if path.is_file() and path.suffix in extensions:
            destination = work / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, destination)

if cache_state.exists():
    cache = json.loads(cache_state.read_text())['volume']
    if not cache.startswith('nf-finance-m2-'):
        raise RuntimeError('Invalid isolated cache name')
else:
    cache = 'nf-finance-m2-' + suffix
    docker('volume', 'create', cache)
    cache_state.write_text(json.dumps({'volume': cache}))

network_created = False
database_created = False
try:
    docker('network', 'create', network)
    network_created = True
    docker('run', '-d', '--name', database, '--network', network,
           '--network-alias', 'finance-db', '--tmpfs', '/var/lib/postgresql/data',
           '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-e', 'POSTGRES_DB=negocio_finance_test',
           'postgres:17-alpine')
    database_created = True
    # No published ports, no data volumes, and no existing database credentials.
    for attempt in range(40):
        ready = subprocess.run(['docker', 'exec', database, 'pg_isready', '-U', 'postgres',
                                '-d', 'negocio_finance_test'], capture_output=True)
        if ready.returncode == 0:
            break
        time.sleep(0.5)
    else:
        raise RuntimeError('Isolated PostgreSQL did not become ready')
    url = 'jdbc:postgresql://finance-db:5432/negocio_finance_test'
    args = ['docker', 'run', '--rm', '--name', runner, '--network', network,
            '--mount', f'type=bind,src={work},dst=/work',
            '--mount', f'type=volume,src={cache},dst=/root/.m2', '-w', '/work']
    for key, value in {'TEST_DATABASE_URL': url, 'DATABASE_USER': 'postgres', 'DATABASE_PASSWORD': 'unused',
                       'FINANCE_TEST_DATABASE_URL': url, 'FINANCE_TEST_DATABASE_USER': 'postgres',
                       'FINANCE_TEST_DATABASE_PASSWORD': 'unused'}.items():
        args += ['-e', key + '=' + value]
    args += ['maven:3.9.16-eclipse-temurin-21', 'mvn', '-B', '-ntp', 'verify']
    print('Verificando backend em PostgreSQL descartável, sem portas publicadas.', flush=True)
    with (work / 'maven.log').open('w') as log:
        result = subprocess.run(args, stdout=log, stderr=subprocess.STDOUT)
    reports = sorted((work / 'target/surefire-reports').glob('TEST-*.xml'))
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, '0'))
        print(suite.get('name') + ': ' + ', '.join(key + '=' + suite.get(key, '0') for key in counts))
    print('TOTAL: ' + ', '.join(key + '=' + str(value) for key, value in counts.items()))
    print('Artefatos e log local: ' + str(work))
    if result.returncode:
        raise SystemExit(result.returncode)
    assert counts['tests'] > 0 and counts['failures'] == 0 and counts['errors'] == 0
    assert (work / 'target/negocio-facil-0.1.0.jar').is_file()
    print('Build backend e testes concluídos; JAR validado.')
finally:
    # Names have a unique random suffix and belong only to this invocation.
    subprocess.run(['docker', 'rm', '-f', runner], capture_output=True)
    if database_created:
        subprocess.run(['docker', 'rm', '-f', database], capture_output=True)
    if network_created:
        subprocess.run(['docker', 'network', 'rm', network], capture_output=True)
