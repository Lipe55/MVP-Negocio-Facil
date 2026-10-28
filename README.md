# Negócio Fácil

SaaS de gestão comercial para pequenas empresas. React + TypeScript no frontend; Java 21, Spring Boot 3.5.16, JPA e PostgreSQL na API. Dados persistidos no PostgreSQL e sessões persistidas no mesmo banco. Não há dados fictícios nem autenticação em localStorage.

## Estrutura

```text
frontend/           React, Vite, Tailwind, formulários e testes
backend/            API Spring Boot, domínios, Flyway e testes
docker-compose.yml  PostgreSQL, API e frontend com proxy Nginx
.env.example        Variáveis de desenvolvimento, sem segredos reais
scripts/            Testes e ferramentas do ambiente cloud
docs/               Decisões, segurança e relatório de validação
```

## Funcionalidades

- Cadastro transacional de empresa e administrador, login, logout, recuperação de sessão e alteração de senha com confirmação da senha atual.
- Clientes: cadastro, detalhes, busca, paginação, edição, exclusão e arquivamento para preservar orçamentos vinculados.
- Orçamentos: múltiplos itens, quantidades fracionárias, descontos, listagem com filtros, edição apenas em rascunho, numeração por empresa e envio.
- Link público com token aleatório de 256 bits, aceite ou recusa com confirmação, vencimento e resposta definitiva. Dados privados não são expostos.
- Compartilhamento pelo WhatsApp via `wa.me`, dashboard atualizado e configurações da empresa.
- Temas claro e escuro com alternância no topo, preferência salva no navegador e tema inicial do sistema.
- Financeiro com contas, movimentações e painel de gráficos personalizáveis por usuário. Consulte [a documentação dos gráficos](docs/GRAFICOS_FINANCEIROS.md) para métricas suportadas e validação.
- Contas a pagar e receber com vencimentos, pagamentos parciais, parcelamento e despesas mensais recorrentes finitas. Consulte [a Entrega 2 financeira](docs/FINANCEIRO_ENTREGA_2.md).
- Interface em português, layout responsivo, estados vazios, carregamento, erros e diálogos nativos acessíveis por teclado.

## Execução com Docker

Requisitos: Docker Engine e Docker Compose v2.24 ou superior.

```bash
cp .env.example .env
# Edite .env e substitua DATABASE_PASSWORD por uma senha forte.
docker compose up --build -d --wait
```

Abra `http://localhost:5173` **na sua máquina local**. A API fica acessível em `/api` pelo mesmo frontend. As migrations Flyway rodam automaticamente antes da inicialização da API. O banco usa volume persistente; `docker compose down` preserva os dados. `docker compose down -v` apaga os dados e só deve ser usado conscientemente.

O frontend no Docker serve um build estático: editar `frontend/src` não atualiza automaticamente o container. Para incorporar as alterações à imagem, reconstrua e recrie somente o frontend, sem suas dependências: `docker compose up --build --no-deps -d frontend`. Depois, recarregue o navegador. Para recarga automática durante edição, use o desenvolvimento com Vite descrito abaixo.

```bash
docker compose logs -f backend
docker compose ps
docker compose down
```

O Compose mantém o frontend e o PostgreSQL ligados à interface de loopback do host. Para publicar, coloque um proxy HTTPS na frente do frontend; não exponha a API diretamente.

## Desenvolvimento com recarga

Requisitos: **JDK** 21 (não apenas JRE), Maven 3.9+, Node 22.12+ ou 24+, npm e Docker.

```bash
cp .env.example .env
# Configure a senha no arquivo antes de continuar.
set -a
. ./.env
set +a
docker compose up -d --wait db
npm ci --prefix frontend
mvn -f backend/pom.xml spring-boot:run
```

Em outro terminal, a partir da raiz:

```bash
npm run dev --prefix frontend
```

O Vite encaminha `/api` para a API na porta 8080. Use o frontend em `http://localhost:5173` para manter cookies e CSRF no mesmo host. Para banco externo, defina `DATABASE_URL=jdbc:postgresql://HOST:PORTA/BANCO`; a porta do Compose não altera essa URL automaticamente.

Para reproduzir a preparação do Codex cloud, execute `./scripts/setup-cloud.sh`. Ele mantém o checkout, instala ferramentas em `/workspace/tools`, usa o proxy e a CA existentes, instala pelo lockfile e compila as duas aplicações. Se não houver `.env` nem senha injetada, gera uma senha aleatória **somente para o banco local de desenvolvimento**. Não substitui configuração de produção.

Para construir imagens no cloud com verificação TLS ativa, há `docker-compose.cloud.yml`. Use `scripts/build-cloud.sh`; certificados são montados como secrets de build e não ficam nas imagens finais.

No ambiente cloud preparado, `scripts/cloud-maven` usa o JDK, o Maven, a CA e o proxy locais. Exemplo: `scripts/cloud-maven -f backend/pom.xml spring-boot:run`. Cada tarefa cloud já é isolada: use o checkout existente, sem criar worktrees para inicializar o projeto.

## Testes e builds

Os testes de integração usam PostgreSQL real. **Eles truncam as tabelas do banco de testes** e verificam que seu nome termina em `_test`. Nunca configure `TEST_DATABASE_URL` para o banco da aplicação.

```bash
# Cria negocio_facil_test quando necessário e executa JUnit/Mockito/integração.
./scripts/test-backend.sh
# No cloud:
MAVEN_CMD=./scripts/cloud-maven ./scripts/test-backend.sh

npm test --prefix frontend
npm run build --prefix frontend
# Empacotamento após executar a suíte de testes:
mvn -f backend/pom.xml package -DskipTests
```

Testes de navegador, com a API e o frontend já iniciados:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

`E2E_BASE_URL` permite apontar para outra instância de desenvolvimento. O teste cria duas empresas e dados reais novos; use uma instância dedicada à validação. Ele não apaga esses dados depois. Nunca aponte os testes para produção.

## Variáveis

| Variável | Uso |
|---|---|
| `DATABASE_PASSWORD` | Obrigatória; senha do PostgreSQL. Sem valor padrão na API. |
| `DATABASE_USER` | Usuário PostgreSQL; padrão local `negocio_facil`. |
| `DATABASE_NAME` | Nome do banco no Compose; padrão `negocio_facil`. |
| `DATABASE_URL` | URL JDBC para execução fora do Compose. Obrigatória no perfil `prod`. |
| `DATABASE_PORT` | Porta do banco no host; padrão 5432. |
| `APP_ORIGIN` | Origem exata permitida para CORS; padrão local `http://localhost:5173`. Obrigatória em produção. |
| `WEB_PORT` | Porta do frontend no Compose; padrão 5173. |
| `COOKIE_SECURE` | `false` apenas para HTTP local; cookies seguros são obrigatórios no perfil `prod`. |
| `SPRING_PROFILES_ACTIVE` | `default` em desenvolvimento; `prod` em produção. |
| `PORT` | Porta interna da API, padrão 8080; o proxy deve acompanhar qualquer mudança. |
| `TEST_DATABASE_URL` | Banco exclusivo dos testes, com nome terminado em `_test`. |

## Produção

O arquivo `docker-compose.prod.yml` remove a porta publicada do banco, ativa o perfil `prod`, força cookies seguros e restringe o processo Java. Defina uma origem HTTPS real e credenciais exclusivas antes de executar:

```bash
cp .env.production.example .env.production
# Configure credenciais exclusivas e sua origem HTTPS no arquivo.
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up --build -d --wait
```

Use o perfil `prod`, `APP_ORIGIN=https://seu-dominio`, HTTPS e credenciais próprias fornecidas por um gerenciador de segredos. O perfil força cookies Secure. O frontend e a API devem usar a mesma origem. Nunca reutilize a senha local ou versionar `.env`. A API deve ficar acessível somente pelo proxy confiável.

As imagens fazem builds em múltiplos estágios; o processo Java roda com usuário sem privilégios. O Nginx configura CSP, bloqueio de frames e proteção contra envio de tokens no referrer. Não registra acessos com tokens públicos. Para um proxy externo adicional, configure IPs de proxies confiáveis explicitamente; o padrão da API confia apenas em redes Docker 172.16/12 e o Nginx substitui cabeçalhos encaminhados.

Antes de operar comercialmente: configure backups criptografados com restauração testada, monitoramento, atualizações de segurança, retenção e atendimento a titulares de dados. Rate limiting da aplicação é por IP e por instância; em múltiplas réplicas, aplique também limites compartilhados no gateway. Configure o IP real no ingresso confiável quando houver outra camada de proxy. Veja [segurança e arquitetura](docs/architecture.md).

## API

Todas as operações administrativas obtêm `company_id` da sessão. IDs de outra empresa retornam 404. Respostas usam DTOs, nunca entidades JPA. Listagens aceitam `page` (a partir de 0) e `size` (1–100). Valores decimais são serializados como strings para preservar a precisão.

- `GET /api/auth/csrf`: obtém token CSRF. Envie `X-XSRF-TOKEN` e cookies nas escritas, inclusive login, cadastro e resposta pública.
- `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/password`.
- `GET/POST /api/customers`, `GET/PUT/DELETE /api/customers/{id}`, `POST /api/customers/{id}/archive`. Busca: `search`, `archived`.
- `GET/POST /api/quotations`, `GET/PUT /api/quotations/{id}`, `POST /api/quotations/{id}/send`. Filtros: `customerId`, `status`, `from`, `to` (datas ISO, período inclusivo em São Paulo).
- `POST /api/quotations/{id}/revoke-public`: revogação definitiva do link, preservando o histórico.
- `GET /api/public/quotations/{token}`, `POST /api/public/quotations/{token}/accept`, `POST /api/public/quotations/{token}/reject`.
- `GET /api/dashboard/summary`, `GET/PUT /api/company`, `GET /api/health`.

O envio disponibiliza o link; o MVP não envia mensagens automaticamente. A ação de WhatsApp abre a mensagem pronta para envio pelo usuário. O link funciona como autorização por posse: quem o recebe pode responder. Isso é uma confirmação comercial simples, **não** uma assinatura digital com identidade verificada.

## Evidências de validação

Veja [resultados e limitações dos testes](docs/validation.md). A aplicação foi validada também com o fluxo completo de navegador através do Docker/Nginx e PostgreSQL real.

## Evolução para MVP 2.0

Prioridades sugeridas: exportação de propostas em PDF, convites e permissões por colaborador, histórico de versões, CRM e acompanhamento comercial. Ordens de serviço, agendamento e pagamentos podem ser adicionados em domínios separados, mantendo o isolamento por empresa. Não há cobrança de assinatura neste MVP.

Veja [correções de segurança e validação pendente](docs/security-fixes.md) antes de atualizar uma instância existente.
