# Arquitetura e limites operacionais

Monólito modular por domínio com controllers REST, DTOs validados, services transacionais e repositories JPA. React Router controla as páginas; TanStack Query controla dados remotos; React Hook Form e Zod validam formulários; Axios centraliza cookies, CSRF e erros.

## Autorização e sessão

`SessionUser` guarda somente identidade, empresa e e-mail. A sessão é mantida no PostgreSQL via Spring Session JDBC, expira após 8 horas de inatividade e tem identificador rotacionado no login. Cookie HttpOnly, SameSite=Lax e Secure em produção. Senhas BCrypt, custo 12; limite de 72 bytes verificado antes de armazenar. A troca de senha revoga as sessões persistidas do usuário, incrementa a versão de credencial e encerra a sessão atual. Cada requisição autenticada verifica essa versão no banco, rejeitando também sessões salvas por um login concorrente com senha antiga.

Não há tokens no armazenamento do navegador. CORS permite apenas uma origem configurada. CSRF também protege endpoints públicos de escrita. Login/cadastro têm limite de 15 tentativas por minuto/IP; rotas públicas, 90 requisições por minuto/IP. Esse limite reside em memória por instância e não substitui um gateway com limites distribuídos.

## Multiempresa e concorrência

Os repositories administrativos sempre recebem a empresa da sessão. A empresa não é lida do payload. Clientes de outra empresa retornam 404. A chave estrangeira composta entre orçamento e cliente também impede associação entre empresas no banco.

Um bloqueio pessimista na empresa serializa a alocação de números e a edição dos dados da empresa. O par empresa/número é único. A edição, o envio e as respostas públicas bloqueiam a linha do orçamento; somente um respondente vence. Status final nunca volta a aguardando. Valores, itens, condições comerciais e os nomes das partes enviados são imutáveis. Os nomes são copiados no envio; alterações cadastrais posteriores não mudam a proposta.

BigDecimal na API e inteiros BigInt no frontend. Quantidades têm até 3 casas; preços, 2. Arredondamento HALF_UP de cada item antes da soma. Totais calculados pelo backend, mesmo se o navegador enviar outro total. Constraints do banco protegem os invariantes.

## Datas e dados públicos

Timestamps em UTC, apresentação em America/Sao_Paulo. O orçamento é válido até o fim da data nesse fuso. Expiração é calculada/persistida ao consultar listagens, dashboard, detalhes ou ao responder. Não há job agendado: registros ainda não consultados podem permanecer AGUARDANDO no banco após a validade, mas nenhuma resposta vencida é aceita.

Token gerado com SecureRandom de 32 bytes, codificado em Base64URL. A API pública expõe apenas empresa, número da proposta, nome do cliente, itens comerciais, valores, validade, status e data da resposta. Não expõe IDs internos, documentos, contatos, observações internas ou o token no corpo público.

Arquivamento preserva os clientes ligados a propostas. Notas internas do orçamento são visíveis apenas no painel administrativo. O campo separado de condições comerciais é compartilhado no endpoint público. O administrador pode revogar definitivamente o link sem modificar o status ou apagar o histórico da proposta.

## Operação e LGPD

A aplicação não implementa recuperação de senha por e-mail, envio automático de mensagens, assinatura digital, cobrança, auditoria completa ou política de retenção automatizada. São funcionalidades distintas do escopo solicitado. O operador é responsável por base legal, aviso de privacidade apropriado, retenção, backups, exportação/remoção assistida de dados e controle de acesso às operações do banco.

URLs públicas são credenciais por posse. Não adicionar analytics externos nessas páginas nem registrar caminhos completos no proxy, gateway ou ferramenta de monitoramento. A configuração Nginx fornecida omite esses logs e usa Referrer-Policy=no-referrer. Em produção, revise também a configuração do ingresso externo.

Migrations não são alteradas depois de aplicadas; futuras mudanças usam novos arquivos versionados. A API falha ao iniciar se o schema não corresponder ao modelo JPA. `/api/health` consulta efetivamente o banco.

Veja [correções e validação pendente](security-fixes.md) para a migration V2 e a configuração de proxies confiáveis.
