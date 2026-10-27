# Correções da análise de 14/10/2026

## Mudanças implementadas

- Edição da empresa e numeração usam o mesmo bloqueio pessimista. Alterações no nome do responsável também bloqueiam o usuário, evitando sobrescrever senha/versão de credencial concorrentes.
- A coluna de identificação de sessões aceita 254 caracteres, assim como o cadastro.
- Sessões carregam uma versão de credencial; a troca de senha incrementa a versão sob bloqueio e revoga as sessões existentes. Requisições autenticadas verificam a versão no banco. Um login antigo salvo depois da revogação não autoriza novas operações. Requisições já em execução antes da revogação podem terminar.
- Nomes da empresa e cliente são copiados no envio. Detalhes, listagens e proposta pública usam os nomes preservados.
- Condições comerciais são um campo separado e público. Notas internas antigas continuam privadas; nenhuma nota existente é publicada automaticamente.
- Link público pode ser revogado pelo proprietário. Consultas e respostas posteriores retornam 404; o status comercial e o histórico são preservados. A revogação é definitiva e serializa com respostas usando o bloqueio do orçamento.
- Itens vazios/nulos e offsets de paginação que não cabem em int são rejeitados com 400.
- CSRF é obtido novamente para cada escrita; apenas chamadas simultâneas compartilham uma consulta em andamento. Uma escrita só é repetida uma vez se a API responder com o código CSRF_INVALID, antes do controller. Timeouts, conflitos e outras negativas não repetem a operação.
- Login/logout/troca de senha notificam outras abas por BroadcastChannel sem transmitir dados privados. Caches privados são descartados ao trocar a sessão.
- O filtro de orçamentos inclui clientes arquivados; o seletor de novos orçamentos continua buscando clientes ativos.
- Contextos Docker excluem todas as variantes com prefixo .env, inclusive .envv.
- Nginx usa imagem sem privilégios, porta interna 8080 e, em produção, filesystem somente leitura, tmpfs, capabilities removidas e no-new-privileges.

## Migration e implantação

V1 não foi modificada. A nova V2 amplia a coluna de sessões, adiciona versão de credencial, snapshots, condições comerciais e revogação. Ela será executada pelo Flyway na próxima inicialização; não foi aplicada nesta sessão.

Sessões antigas sem versão de credencial serão rejeitadas e os usuários precisarão entrar novamente. Para propostas já enviadas, a migration preserva os nomes disponíveis no momento da atualização. Nomes históricos originais não podem ser reconstruídos sem fonte externa. Faça backup e valide a migration em cópia isolada antes da implantação.

Publique backend e frontend atualizados juntos. A porta interna do frontend mudou de 80 para 8080; o Compose acompanha essa alteração, mas proxies externos que apontam diretamente ao container precisam acompanhar. A porta publicada no host não mudou.

## Ingresso HTTPS e IP real

O arquivo frontend/trusted-proxies.conf não confia em nenhum proxy por padrão. A configuração Nginx já inclui esse arquivo, usa real_ip_header X-Forwarded-For e real_ip_recursive on. Configure nele apenas set_real_ip_from com o endereço ou CIDR exato do ingresso controlado. É possível substituir /etc/nginx/trusted-proxies.conf por um arquivo montado somente para leitura.

Não use 0.0.0.0/0 nem ::/0. O ingresso deve substituir o IP do cliente de forma confiável e bloquear acesso direto ao frontend que contorne o ingresso. Em cadeias de proxies, enumere apenas os proxies controlados. O Nginx encaminha à API o IP já validado, substituindo o cabeçalho recebido. Limites compartilhados entre réplicas continuam sendo responsabilidade do gateway.

A rede real do ingresso não foi fornecida nem inspecionada; sua configuração operacional permanece pendente. Mantenha APP_ORIGIN como origem HTTPS exata e o perfil prod; não é necessário fornecer valores de segredos para revisar estas alterações.

## Testes adicionados/atualizados

- Backend: e-mail longo e recuperação de sessão, itens vazios/nulos, paginação extrema, snapshots após aceite, separação de condições/notas, revogação com isolamento entre empresas, credenciais antigas após troca de senha, edição da empresa concorrente com numeração e testes unitários do filtro/validação.
- Frontend: renovação CSRF, repetição limitada apenas para rejeição CSRF, ausência de repetição em falhas de negócio/timeouts, sincronização de sessão e apresentação das condições.
- E2E: condições públicas, privacidade de notas, revogação e logout entre abas.

## Validação desta alteração

Na verificação inicial, as dependências frontend estavam ausentes e Java/Maven não estavam no PATH. Na revisão posterior dos erros do IDE, o usuário autorizou instalar o frontend pelo lockfile. `npm ci --ignore-scripts --no-audit --no-fund` instalou as dependências, sem executar scripts de instalação. Os hashes de package.json e package-lock.json permaneceram iguais.

Resultados da revisão posterior:

- `tsc --noEmit -p tsconfig.json`: passou sem erros, incluindo os arquivos TSX e testes.
- Vitest: 5 arquivos e 22 testes passaram. Foi usada configuração temporária com `envDir: false` para impedir o carregamento de arquivos de ambiente protegidos.

Os testes backend, integração, E2E e build das imagens continuam sem execução. Java/Maven não estão no PATH; integração e E2E alteram dados e exigem PostgreSQL e aplicação descartáveis dedicados. O sufixo _test sozinho não garante isolamento. Não foram iniciados/parados serviços nem aplicada a migration. Não use scripts/test-backend.sh contra ambientes existentes: ele pode iniciar o banco e carrega configuração protegida.

Verificações iniciais executadas: bash -n dos cinco scripts passou; parsing dos três JSONs e do pom.xml passou; parsing YAML dos dois Compose e dos dois arquivos application passou, sem resolver variáveis. Sete verificações diretas das funções monetárias, arredondamento, formatação e WhatsApp passaram usando o suporte TypeScript do Node, sem dependências externas. Os resultados anteriores em validation.md não validam esta alteração.

A consulta anterior de avisos de Axios não encontrou vulnerabilidade aplicável aos avisos examinados para a versão do lockfile. Não foi realizada varredura completa de dependências Java/JavaScript ou imagens. Nenhuma certificação de segurança é inferida. A varredura completa e o build/teste das imagens precisam ocorrer na validação isolada antes da publicação.

Referências de configuração: [Nginx real IP](https://nginx.org/en/docs/http/ngx_http_realip_module.html) e [imagem Nginx sem privilégios](https://github.com/nginx/docker-nginx-unprivileged). A documentação da imagem confirma porta 8080, PID e diretórios temporários em /tmp; a configuração mantém o nginx.conf principal da imagem.

## Atualização do frontend em execução

Na revisão do visual, o container local ainda servia um CSS antigo, sem theme-switch, data-theme ou dashboard-welcome. O Compose também apontava à porta 80, enquanto a configuração Nginx atual do código usa 8080; o mapeamento do código foi alinhado a 8080.

Foi gerado um novo build com carregamento de arquivos de ambiente desativado. TypeScript e Vite passaram. Com autorização, apenas os arquivos estáticos foram copiados ao container local existente: primeiro os novos assets e depois index.html, mantendo os assets antigos e um backup do HTML em /tmp. Requisições públicas sem cookies confirmaram que HTML, CSS e JavaScript servidos correspondiam ao novo build. Não houve reinício de serviços, atualização da imagem em execução, acesso à API ou alteração de banco.

A imagem Docker existente continua antiga. A cópia dos assets permanece enquanto esse container existir; ao recriá-lo, é necessário reconstruir a imagem a partir dos fontes atualizados para preservar o novo visual. A mudança da porta interna também passa a valer quando a nova imagem/configuração for aplicada. Nenhum container backend foi atualizado e a migration continua pendente.
