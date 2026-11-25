# Fiscal V1.0

O módulo organiza NFS-e previamente obtidas. A importação manual preserva os bytes do XML. A situação é informada pelo documento e não passa por consulta externa ou validação da assinatura. Emissão, transmissão, cancelamento e sincronização de eventos governamentais ficam fora desta versão.

## Importação e persistência

A API deriva a empresa e o responsável da sessão autenticada. O envio multipart tem limite de 1 MiB por arquivo, com margem de 1100 KiB para o envelope. Conteúdo e estrutura são validados pelo servidor. O parser desabilita DTD, entidades externas, XInclude e acesso a schemas externos. A profundidade também é limitada.

A V1 aceita XML UTF-8 com raiz `NFSe` no namespace nacional, chave numérica de 50 dígitos em `infNFSe/@Id`, número, `dhProc`, DPS e emitente identificado por CPF/CNPJ numérico. Quando há documento cadastrado na empresa, o prestador deve corresponder a ele. Sem CPF/CNPJ no cadastro da empresa, a comparação fica indisponível. XML municipal ou ABRASF, eventos avulsos, DPS sem NFS-e e chaves com caracteres alfanuméricos ficam fora deste leiaute. A validação é estrutural e cobre os campos utilizados pela V1. Campos opcionais ausentes ficam vazios. A data de emissão corresponde à data local de `dhProc` no documento, e competência usa `dCompet`. Os códigos `cStat` 100, 101 e 102 são mapeados respectivamente para válida, cancelada e substituída. Os demais ficam como não verificados.

A migration V6 cria `fiscal_invoices` e `fiscal_audit`. Chave e SHA-256 são únicos dentro da empresa, de forma atômica mesmo quando os uploads são concorrentes. Índices cobrem período de emissão, competência, cliente e situação. O XML fica em `BYTEA`, separado da projeção das consultas. Bruto e líquido usam `NUMERIC(16,2)`. Outros valores declarados ficam em JSONB e são lidos como `BigDecimal`. O fluxo preserva a precisão de centavos e rejeita valores monetários incompatíveis. A auditoria registra ator, nota, horário e operação de importação, vínculo ou documento servido. As respostas de arquivo têm `Cache-Control: no-store`.

As FKs compostas protegem vínculos com cliente, orçamento, item do orçamento e conta a receber. O item é a representação existente de serviço. Se o orçamento for editado e o item substituído, o vínculo ao item anterior é limpo e o vínculo ao orçamento permanece. A conta a receber precisa existir e ser de receita. Importação e vínculo não criam lançamentos, parcelas, baixas ou movimentações. O resumo soma o valor bruto informado das notas filtradas, incluindo as canceladas ou substituídas se estiverem no resultado. Ele não representa receita recebida.

O armazenamento no banco usa o mesmo backup e restauração da aplicação. O PDF é gerado sob demanda e não é persistido. Para aumento de volume, considere armazenamento privado de objetos e cache versionado pela revisão do gerador, hash do XML, situação e opção de canhoto.

## Gerador oficial

A imagem `danfse/Dockerfile` usa o [gerador oficial 1.0.0](https://github.com/ReceitaFederal/calculadora-rtc-gerador-danfse/tree/8d79e6b2f7fad6375340afcb51c85ed370c64a14), fixado no commit `8d79e6b2f7fad6375340afcb51c85ed370c64a14`. A imagem compila o servidor com Go 1.27.1 e os arquivos Swagger versionados; inclui o aviso MIT original. O container usa usuário sem privilégios, filesystem somente leitura e rede interna compartilhada apenas com o backend. A porta do gerador não é publicada. A aplicação principal não depende do gerador para iniciar ou responder a outras rotas.

O backend envia o XML original por `POST /api/danfse`, com `Content-Type: application/xml`. O parâmetro `canhoto` aceita `true` ou `false`. `stc` é derivado do XML e recebe 0 (válida), 1 (substituída) ou 2 (cancelada) quando conhecido. Nos demais casos, o backend omite o parâmetro. O upstream usa 0 como padrão visual. O PDF é uma representação auxiliar, inclusive nesse caso. O backend limita a resposta a 8 MiB, confirma o tipo de mídia e a assinatura `%PDF-`, e converte falhas do gerador em respostas seguras. O Compose usa `/health/ready` e o gerador também oferece `/health/live`.

| Variável | Padrão e função |
| --- | --- |
| `DANFSE_URL` | `http://danfse:8080` no Compose e `http://localhost:8081` fora dele. Configure uma URL base privada. |
| `DANFSE_TIMEOUT` | `8s`. Inclui conexão e recebimento do arquivo. |
| `XML_MAX_SIZE_BYTES` | `1048576` no container do gerador. |
| `TEST_DANFSE_URL` | Opcional. Executa o teste com um gerador real acessível a partir do processo Maven. |

Para atualizar, confira a versão do Go, licença, Swagger, códigos de situação e requisitos do leiaute no novo commit. Atualize os pinos e o aviso MIT, reconstrua a imagem e execute testes de renderização com amostras sintéticas antes da implantação.

## API autenticada

| Método e caminho | Comportamento |
| --- | --- |
| `POST /api/fiscal/notas/importar` | Multipart no campo `file`. Retorna 201 com detalhes. Duplicata retorna 409, limite retorna 413 e XML inválido retorna 422. |
| `GET /api/fiscal/notas` | Filtra por `search`, `from`, `to`, `competenceFrom`, `competenceTo`, `customerId`, `status` e `origin=IMPORTACAO_MANUAL`. `page` começa em 0, `size` é limitado a 100. `order` aceita `EMISSAO_RECENTE`, `EMISSAO_ANTIGA` ou `MAIOR_VALOR`. |
| `GET /api/fiscal/notas/{id}` | Dados extraídos, vínculos e metadados da importação. |
| `GET /api/fiscal/notas/{id}/xml` | Download do XML original. |
| `GET /api/fiscal/notas/{id}/danfse` | PDF inline. Aceita `download=true` e `canhoto=false`. |
| `PATCH /api/fiscal/notas/{id}/vinculos` | Substitui o conjunto `customerId`, `quotationId`, `quotationItemId` e `obligationId`. Campos nulos removem vínculos e item exige orçamento. |
| `GET /api/fiscal/resumo` | Indicadores agregados com os mesmos filtros da listagem. |
| `GET /api/fiscal/vinculos/{kind}` | Pesquisa até 50 opções de `clientes`, `orcamentos`, `servicos` ou `receber`. Aceita `search`, `selected`; serviços exigem `quotationId`. |
| `GET /api/fiscal/gerador` | Indica se o gerador está pronto. |

Datas usam YYYY-MM-DD e os limites de período são inclusivos. Operações de escrita usam o mesmo token CSRF da aplicação. IDs de outras empresas retornam 404 nas operações direcionadas. A API não recebe um tenant no payload.

## Executar e testar

Na raiz, com o `.env` configurado conforme o README:

```sh
docker compose up --build -d
docker compose ps
# Na primeira execução, crie o banco adicional exigido pelos testes do financeiro.
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" negocio_finance_test'
export FINANCE_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/negocio_finance_test
export FINANCE_TEST_DATABASE_USER=negocio_facil
# Defina FINANCE_TEST_DATABASE_PASSWORD com a senha do banco de testes.
bash scripts/test-backend.sh
cd frontend
npm ci
npm run build
npm test
npx playwright install chromium
npm run test:e2e
```

Ajuste usuário e porta dos testes financeiros conforme a configuração local. O teste fiscal de navegador usa a URL do frontend e requer o gerador ativo.

Para desenvolvimento com a API fora do Compose, publique o gerador somente em loopback:

```sh
docker build -t negocio-facil-danfse ./danfse
docker run --rm -p 127.0.0.1:8081:8080 -e XML_MAX_SIZE_BYTES=1048576 negocio-facil-danfse
```

Em outro terminal, execute o teste de PDF real com `TEST_DANFSE_URL=http://localhost:8081 mvn -f backend/pom.xml -Dtest=DanfseClientTest test`. Use somente XML sintético nos testes. Os testes de integração Java exigem bancos descartáveis cujos nomes terminam em `_test`. Eles podem truncar dados.

## Backup, retenção e acesso

Em produção, use volume e backups cifrados, credenciais e chaves com acesso restrito, cópia separada do banco principal e testes periódicos de restauração. Inclua `fiscal_invoices` e `fiscal_audit` no backup. Registre o prazo de guarda por categoria de documento com o responsável fiscal e de privacidade, considerando requisitos aplicáveis e suspensões de descarte. A V1 não expurga documentos automaticamente. O operador deve executar o descarte autorizado também no ciclo de expiração dos backups e registrar a operação. Limite o acesso operacional a documentos, dumps e logs e mantenha HTTPS no ingresso da aplicação. Consulte o [guia de segurança da ANPD](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-sobre-seguranca-da-informacao-para-agentes-de-tratamento-de-pequeno-porte).
