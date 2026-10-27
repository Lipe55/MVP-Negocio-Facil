# Validação executada

Validação em 13/10/2026 no checkout local de desenvolvimento. Java 21, Spring Boot 3.5.16, PostgreSQL 17, Node 24 e Vitest 5.0.4. Todos os dados dos fluxos funcionais foram armazenados no PostgreSQL; mocks são usados somente nos testes de componentes e em um teste unitário do serviço de autenticação.

| Verificação | Resultado |
|---|---|
| Backend JUnit/Mockito e integração PostgreSQL | 22 testes passaram, zero falhas, erros ou testes ignorados |
| Frontend Vitest/Testing Library | 13 testes passaram, zero falhas |
| Fluxo completo Playwright pelo Docker/Nginx | 1 teste passou, incluindo duas empresas, aceite público e navegação móvel |
| Largura da página no celular | Confirmada em 390 px, sem overflow horizontal da página |
| Build de produção frontend | TypeScript e Vite passaram |
| Empacotamento backend | Maven gerou o JAR executável |
| Builds Docker de frontend e backend | Passaram, com verificação TLS ativa |
| Inicialização Docker Compose | PostgreSQL, API e frontend iniciaram; banco e API saudáveis |
| Requisição real através do Nginx | `/api/health` retornou `UP`, consultando PostgreSQL |
| Proteções HTTP do frontend | CSP, nosniff, bloqueio de frames e no-referrer confirmados |
| Token CSRF | Endpoint e cookie confirmados por requisição real |
| Configuração Docker de produção | Compose validou o arquivo de override |
| npm audit, incluindo desenvolvimento | Zero vulnerabilidades reportadas |
| Reexecução do setup cloud | Instalação pelo lockfile e os dois builds passaram |

## Cobertura de negócio

O backend validou cadastro transacional, BCrypt, e-mail único, login válido/inválido, recuperação e encerramento de sessão, CSRF, rate limiting, CRUD e paginação de clientes, isolamento entre empresas, criação e cálculo de orçamento, desconto excessivo, valores negativos, envio, proteção de dados públicos, aceite, recusa, expiração, respostas duplicadas, bloqueio de edição após envio/aceite, arquivamento e configurações/senha.

Os testes concorrentes criaram 12 orçamentos em seis threads sem colisões de número. Duas respostas públicas simultâneas produziram exatamente um sucesso HTTP 200 e um conflito HTTP 409.

O frontend validou formulários, cálculo exato, arredondamento, quantidades em português, WhatsApp, criação com múltiplos itens por API, carregamento, erros, recuperação de sessão e proteção de rotas.

## Fluxo de navegador

O teste registrou a empresa e o administrador, recuperou a sessão após login, cadastrou um cliente, criou um orçamento com dois itens e desconto, confirmou R$ 290,25, enviou a proposta e abriu o token público em outro contexto de navegador sem login. O aceite foi persistido, o painel exibiu ACEITO, a ação de editar desapareceu e uma segunda empresa não conseguiu abrir o registro privado. Também foram verificadas a navegação móvel e a largura da página. Capturas foram geradas em `frontend/test-results/`, diretório ignorado pelo Git e pelos builds Docker.

## Limitações operacionais

Não houve publicação em domínio externo, configuração de HTTPS real, envio de mensagem pelo WhatsApp, teste de restauração de backup ou teste de carga. O compartilhamento abre `wa.me`; o envio da mensagem é confirmado pelo usuário no WhatsApp. A configuração de produção existe, mas sua ativação exige domínio, TLS e credenciais próprias.

A auditoria npm é uma verificação das dependências JavaScript. Não equivale a uma certificação de segurança do sistema. Spring Boot e a imagem Java foram atualizados para versões estáveis disponíveis na implementação; mantenha uma rotina de atualizações e análise de dependências.

A limitação de rede inicial foi resolvida com domínios específicos e uso do proxy/CA existentes. O cache Docker esgotou o disco durante um build local; somente o cache descartável foi removido e o build foi repetido com sucesso. Dados PostgreSQL, volumes e fontes foram preservados.

Código e documentação foram criados localmente; não houve push para GitHub ou implantação externa. As instruções `install_script` e `start_skill` foram salvas no rascunho do ambiente; publicação do ambiente é uma ação separada na interface do Codex.
