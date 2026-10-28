# Implementação financeira — Negócio Fácil

## Objetivo e protocolo

Controle de caixa para MEIs, integrado à autenticação e ao isolamento por empresa existentes. Implementar uma entrega de cada vez. Não iniciar a próxima sem a frase explícita do usuário "Pode implementar a próxima entrega". O usuário testa e faz seus próprios commits; o agente não executa git add/commit/push/merge/rebase.

| Entrega | Escopo | Estado |
|---|---|---|
| 1 | Fundação: contas, categorias, lançamentos, liquidação integral, saldo, extrato, histórico, transferências e retiradas | AGUARDANDO TESTES DO USUÁRIO |
| 2 | Contas a pagar/receber, parciais, parcelas e recorrências | AGUARDANDO TESTES DO USUÁRIO |
| 3 | Integração com orçamentos e serviços | NÃO INICIADA |
| 4 | Dashboard e projeções | NÃO INICIADA |
| 5 | Cartões e relatórios | NÃO INICIADA |
| 6 | Alertas, e-mails e onboarding | NÃO INICIADA |

## Diagnóstico inicial

Java 21, Spring Boot 3.5.16, Spring Data JPA/Security, PostgreSQL/Flyway; React/TypeScript/Vite/Tailwind com componentes próprios e temas claro/escuro. Existem empresas, usuários, clientes e orçamentos. Não existem módulos de serviços ou NFS-e. Sessão e CSRF serão reutilizados. Alterações preexistentes: permissões de backend/build-maven.sh e scripts/test-backend.sh; README, docs e scripts cloud não rastreados. Nenhuma delas será descartada.

## Decisões da Entrega 1

- Modelos distintos para lançamento (obrigação simples), liquidação integral e movimento efetivo de saldo; parcelas somente na Entrega 2.
- BigDecimal e NUMERIC(16,2); valores monetários JSON como strings. Saldo pode ser negativo e será exibido explicitamente.
- Saldo inicial é referência patrimonial da conta, nunca receita. Sua alteração é permitida somente antes de qualquer movimento efetivo; renomear continua permitido.
- Livro de movimentos somente acrescenta registros. Edições e cancelamentos de lançamentos liquidados estornam os movimentos anteriores e preservam as liquidações históricas.
- Transferências têm duas pernas vinculadas; retiradas têm natureza própria, separada de despesas operacionais.
- Todas as mutações financeiras serializam por bloqueio da empresa. Versões impedem edição perdida; chaves de idempotência protegem criação, liquidação e transferência.
- Conta principal e categorias iniciais para empresas existentes via migration; empresas novas recebem a mesma estrutura no cadastro transacional.
- Nenhum dado fictício nas telas. Sem dashboard completo, previsões, parcelas, recorrência, cartões, exportação ou cobrança automática de orçamento.

## Funcionalidades implementadas

- Conta principal automática no cadastro de empresa e para empresas preexistentes na migration. Cinco categorias iniciais por empresa; criação de categorias próprias de receitas e despesas.
- Contas bancárias/dinheiro, renomeação, saldo inicial com referência, ativação/desativação. A Conta principal permanece ativa; outras contas exigem saldo resultante zero e ausência de pendências para desativar. Contas inativas precisam ser reativadas para corrigir liquidações históricas.
- Receitas, despesas operacionais e retiradas pessoais; pendente ou liquidado integralmente. Competência, conta, data efetiva e notas. Não há pagamentos parciais, vencimentos, parcelas ou recorrência nesta entrega.
- Recebimentos, pagamentos e retiradas efetivos alteram saldo. Pendências não geram movimentos. Saldo inicial não é receita e transferências não são receitas/despesas.
- Edição de lançamento liquidado: estorno da liquidação anterior e nova liquidação quando aplicável. Pode retornar a pendente de maneira controlada. Natureza não pode ser trocada na edição. Cancelamento é lógico e idempotente.
- Transferência gera duas pernas atômicas e admite estorno integral vinculado. Falha em uma das pernas reverte a transação inteira. A conta de origem pode ficar negativa: esta é uma ferramenta de controle, sem bloqueio de crédito/saldo físico.
- Histórico com usuário, instante e snapshots antes/depois. Movimentos e auditoria não admitem UPDATE/DELETE no banco; correções acrescentam registros. Estornos mantêm a data efetiva original e possuem data de registro própria; são correções do registro, não pagamentos bancários automáticos.
- Extrato paginado, saldos acumulados, saldo anterior, variação e saldo ao fim do período. Filtros de extrato usam data efetiva; filtros de lançamentos usam competência.
- Idempotência por UUID e hash de payload em criação de lançamento, liquidação e transferência. Repetir a mesma operação retorna o registro; reutilizar chave com outros dados retorna 409. Edição usa versão para rejeitar atualização perdida.
- Telas sem mocks: Movimentações e Contas e saldos, com estados de carregamento, vazio e falha, formulários rápidos, filtros e diálogos. Tema claro/escuro reutilizado e tabelas com rolagem em telas estreitas. Gravações bloqueiam fechamento do diálogo enquanto estão em andamento.

## Migration

`backend/src/main/resources/db/migration/V3__financial_foundation.sql` cria:

1. `financial_accounts` — contas e referência do saldo inicial.
2. `financial_categories` — categorias por empresa/natureza.
3. `financial_entries` — obrigações simples e estado atual.
4. `financial_settlements` — liquidações integrais e estornos históricos.
5. `financial_transfers` — vínculo das duas pernas da transferência.
6. `financial_movements` — livro imutável de movimentos efetivos.
7. `financial_audit` — snapshots e autoria.

Inclui FKs compostas por empresa, unicidade de Conta principal, unicidade de liquidação ativa, idempotência e estornos únicos; checks de natureza, valores e origens; índices para filtros/extratos e triggers de imutabilidade. Adiciona somente uma constraint composta em `users(id,company_id)` para o FK de auditoria. V1 e V2 não foram editadas.

Flyway aplica V3 ao iniciar o backend atualizado. Foi validada em banco descartável tanto a instalação limpa quanto atualização V2→V3 com empresa preexistente. Não foi aplicada no banco local existente. Não modificar migrations antigas nem executar a suíte de integração em banco com dados que devam ser preservados.

## Endpoints disponíveis

Todos exigem a sessão existente; escritas exigem CSRF. Empresa e autor vêm da sessão, nunca do payload.

| Método | Caminho | Operação |
|---|---|---|
| GET / POST | `/api/finance/accounts` | Listar saldos / cadastrar conta |
| PUT | `/api/finance/accounts/{id}` | Editar conta, referência inicial quando permitida e estado ativo |
| GET | `/api/finance/accounts/{id}/statement` | Extrato, `from`, `to`, `page`, `size` |
| GET / POST | `/api/finance/categories` | Listar / criar categoria |
| GET / POST | `/api/finance/entries` | Filtrar / criar lançamento |
| GET / PUT | `/api/finance/entries/{id}` | Consultar / editar lançamento |
| POST | `/api/finance/entries/{id}/settle` | Liquidação integral |
| POST | `/api/finance/entries/{id}/cancel` | Cancelamento lógico e estorno |
| POST | `/api/finance/transfers` | Transferência entre contas |
| POST | `/api/finance/transfers/{id}/cancel` | Estorno das duas pernas |
| GET | `/api/finance/history/{resource}/{id}` | Histórico de `CONTA`, `LANCAMENTO` ou `TRANSFERENCIA` |

Listagem de lançamentos: `kind`, `status`, `accountId`, `categoryId`, `search`, `from`, `to`, `page`, `size`. Paginação começa em zero, tamanho 1–100. Escritas críticas de criação exigem `requestId` UUID; alterações/liquidações/cancelamentos usam `version`. O frontend gera e conserva a chave durante a tentativa. JSON monetário é string decimal. Valores individuais têm até 14 dígitos inteiros e duas casas; o frontend aceita `1.234,56` ou `1234.56`.

## Testes e resultados reais

Validação executada nesta Entrega 1:

- `python3 scripts/test-finance-isolated.py`: **58 testes backend**, zero falhas/erros/ignorados; 22 de integração financeira e 3 unitários financeiros, mais 33 existentes. Executou `mvn -B -ntp verify` em Maven/Java 21 e PostgreSQL 17 descartável; JAR gerado. Log/relatórios da execução final: `/tmp/negocio-finance-validation-h2h2eptc`.
- O script usa rede e containers próprios, banco `negocio_finance_test` em tmpfs, sem portas publicadas, sem volumes de dados e sem ler `.env`. Remove os containers/rede criados ao terminar. Mantém somente um cache Maven dedicado para reexecução. Pode baixar a imagem Maven e dependências de build. Não usa `scripts/test-backend.sh`, que lê configuração protegida e inicia serviços existentes.
- `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.json`: passou sem erros.
- `node node_modules/vitest/vitest.mjs run --config /tmp/negocio-facil-finance.config.mts`: **37 testes frontend** em 7 arquivos, incluindo 9 financeiros. Todos passaram. A configuração temporária usa `envDir:false` e importa a configuração real do projeto, evitando leitura de ambientes protegidos.
- `node node_modules/vite/bin/vite.js build --config /tmp/negocio-facil-finance.config.mts --outDir /tmp/negocio-facil-finance-final-build`: build passou. Bundle JS 534,03 KB (164,43 KB gzip); aviso de chunk acima de 500 KB, sem erro de build.
- `git diff --check`: passou. Nenhum comando de staging/commit/push/merge/rebase foi executado. Package e lockfile não foram modificados.

Os testes financeiros cobrem receita recebida, despesa paga, pendências sem dinheiro, retirada distinta de despesa, edição e cancelamento com estorno, idempotência, versão, liquidações concorrentes, transferência/reversão, rollback de estorno, datas e saldos acumulados, migração de empresa existente, abertura não operacional, desativação inválida, imutabilidade, autenticação/CSRF e tentativas de acesso/associação entre empresas. O frontend cobre dinheiro brasileiro/negativo, formulário, pendência, retirada, erros, saldo consolidado e bloqueio durante gravação.

## Teste manual da Entrega 1

Use uma empresa de teste no ambiente de desenvolvimento, com backend e frontend atualizados. A instância Docker anterior não foi reiniciada e não recebeu esta entrega: editar os fontes não atualiza automaticamente as imagens.

1. Antes de atualizar banco com dados importantes, providenciar backup. Pelo seu fluxo normal, reconstruir/iniciar as imagens atualizadas; por exemplo, `docker compose up --build -d --wait`. Esse comando é para execução manual pelo usuário: ele pode ler seu ambiente e aplicar migrations. O agente não o executou. Verificar que `flyway_schema_history` registra a versão `3` com `success=true`.
2. Entrar e abrir **Financeiro → Contas e saldos**. Deve existir uma Conta principal, sem dados fictícios.
3. Editar a conta principal: nome `Conta principal`, saldo inicial `1.000,00`, referência hoje. Saldo disponível esperado: **R$ 1.000,00**; não há receita operacional no extrato.
4. Criar uma conta `Dinheiro`, tipo dinheiro em espécie, saldo inicial zero, referência hoje.
5. Em **Movimentações**, criar receita `Serviço de teste`, valor `200,00`, categoria Prestação de serviços, recebido. Saldo consolidado: **R$ 1.200,00**.
6. Criar despesa `Material`, valor `50,00`, categoria Materiais, paga. Saldo: **R$ 1.150,00**.
7. Criar receita pendente de `300,00` e despesa pendente de `80,00`. Saldo continua **R$ 1.150,00**. Filtrar por Pendente; não aparecem como dinheiro no extrato.
8. Registrar retirada pessoal `100,00`, paga. Saldo: **R$ 1.050,00**. Sua natureza é Retirada pessoal, sem categoria operacional.
9. Transferir `250,00` da Conta principal para Dinheiro. Saldos: **R$ 800,00 / R$ 250,00**, consolidado **R$ 1.050,00**. Conferir entrada e saída nos respectivos extratos.
10. Editar a receita recebida de `200,00` para `220,00`. Consolidado: **R$ 1.070,00**. O extrato preserva o movimento original, seu estorno e a nova entrada; o histórico mostra antes/depois.
11. Cancelar a despesa paga de `50,00`. Consolidado: **R$ 1.120,00**. O lançamento permanece Cancelado; o movimento e o estorno permanecem no extrato.
12. Liquidar a receita pendente de `300,00`: consolidado **R$ 1.420,00**. Liquidar a despesa pendente de `80,00`: **R$ 1.340,00**. A ação não fica disponível depois da liquidação.
13. Pelo extrato, estornar a transferência. Conta principal **R$ 1.340,00**, Dinheiro **R$ 0,00**. Consolidado continua **R$ 1.340,00**.
14. Tentar alterar o saldo inicial de uma conta com movimentos: rejeitado. Renomear permanece permitido. Tentar desativar a Conta principal ou uma conta com saldo/pendências: rejeitado. A conta Dinheiro zerada, sem pendências, pode ser desativada.
15. Conferir busca, natureza, situação, conta e datas de competência; conferir filtros de data efetiva do extrato. Abrir histórico de lançamento, conta e transferência. Testar criação de categoria e valor inválido/zero.
16. Em janela anônima, cadastrar outra empresa. Deve receber sua própria Conta principal e categorias, sem os dados da primeira. Tentativas de consultar/editar/liquidar/cancelar IDs da outra empresa devem retornar 404; a suíte isolada cobre esses acessos.
17. Repetir o fluxo nos temas claro/escuro e em tela estreita; conferir diálogos, feedback e rolagem das tabelas.

## Pendências e limites

- Não há bloqueio conhecido nos testes executados; a aprovação funcional/visual do usuário continua pendente.
- Navegador/E2E, revisão visual real em celular e implantação da nova versão no ambiente existente não foram executados. A suíte E2E antiga cria registros e não foi apontada para o banco em uso.
- Java/Maven do host continuam dispensáveis para a validação isolada; o script usa Docker. O banco local real e arquivos de ambiente permanecem intocados.
- Histórico inicial das contas/categorias automáticas é sua criação por migration/bootstrap; edições e todas as operações financeiras do usuário geram auditoria própria. Categorias podem ser criadas; edição/exclusão de categorias não faz parte desta entrega.
- Não há pagamentos parciais, parcelas, vencimentos avançados, recorrências, integração com orçamento/serviço/NFS-e, dashboard financeiro completo, projeções, cartões, relatórios, PDFs, Excel, alertas, e-mails ou onboarding.
- Saldo inicial só é corrigível antes do primeiro movimento; depois, usar lançamento de ajuste identificado ou estornar registros incorretos. Reversões registram correções do controle, não fazem reembolso/transferência bancária real.
- A lista de histórico é integral nesta versão; grandes volumes podem justificar paginação específica. Otimização de bundle permanece pendente e não impede o build.

## Arquivos da entrega


### Criados
- `backend/src/main/java/com/negociofacil/finance/BalanceMovement.java`
- `backend/src/main/java/com/negociofacil/finance/BalanceMovementRepository.java`
- `backend/src/main/java/com/negociofacil/finance/FinanceBootstrap.java`
- `backend/src/main/java/com/negociofacil/finance/FinanceController.java`
- `backend/src/main/java/com/negociofacil/finance/FinanceDtos.java`
- `backend/src/main/java/com/negociofacil/finance/FinanceRules.java`
- `backend/src/main/java/com/negociofacil/finance/FinanceService.java`
- `backend/src/main/java/com/negociofacil/finance/FinanceTypes.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialAccount.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialAccountRepository.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialAudit.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialAuditRepository.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialCategory.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialCategoryRepository.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialEntry.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialEntryRepository.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialSettlement.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialSettlementRepository.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialTransfer.java`
- `backend/src/main/java/com/negociofacil/finance/FinancialTransferRepository.java`
- `backend/src/main/resources/db/migration/V3__financial_foundation.sql`
- `backend/src/test/java/com/negociofacil/finance/FinanceIntegrationTest.java`
- `backend/src/test/java/com/negociofacil/finance/FinanceRulesTest.java`
- `docs/FINANCEIRO_IMPLEMENTACAO.md`
- `frontend/src/finance/Accounts.tsx`
- `frontend/src/finance/Movements.tsx`
- `frontend/src/finance/finance.test.tsx`
- `frontend/src/finance/forms.tsx`
- `frontend/src/finance/schemas.ts`
- `frontend/src/finance/shared.tsx`
- `frontend/src/finance/types.ts`
- `scripts/test-finance-isolated.py`

### Modificados por integração necessária
- `backend/src/main/java/com/negociofacil/auth/AuthService.java`
- `backend/src/test/java/com/negociofacil/AuthServiceTest.java`
- `frontend/src/App.tsx`
- `frontend/src/components/Layout.tsx`
- `frontend/src/components/ui.tsx`
- `frontend/src/money.ts`
- `frontend/src/styles.css`

`AuthService` e seu teste foram ajustados somente para criar a estrutura financeira no cadastro transacional. Rotas/menu/estilos integram as duas telas ao app existente; o helper monetário passou a exibir saldos negativos corretamente; o diálogo compartilhado ganhou bloqueio opcional durante gravações. As diferenças de permissão nos dois scripts antigos e os demais arquivos locais anteriores foram preservados.

## Registro histórico — encerramento da Entrega 1

Entrega 1: **AGUARDANDO TESTES DO USUÁRIO**. Não aprovada automaticamente. Sugestão de commit: `feat(finance): implementar fundação financeira`.

Próxima entrega autorizável: Entrega 2 — Contas a pagar e receber. Ainda **NÃO AUTORIZADA**. Somente iniciar após a autorização explícita "Pode implementar a próxima entrega" e nova leitura deste documento/estado do Git. Não interpretar "feito", "testei" ou "funcionou" como autorização.

### Atualização de autorização — 28/10/2026

O usuário autorizou expressamente **"Sim, implementar a Entrega 2"**. O Git estava
limpo na abertura desta etapa. Essa autorização substitui o bloqueio de início da
Entrega 2 no registro histórico acima, sem registrar aprovação automática das
entregas anteriores. Implementar somente a Entrega 2; Entregas 3–6 continuam não
autorizadas. Acompanhamento detalhado: [FINANCEIRO_ENTREGA_2.md](FINANCEIRO_ENTREGA_2.md).

## Entrega independente — Gráficos financeiros personalizáveis

Autorização específica posterior para uma aba **Gráficos**, entre Movimentações e
Contas e saldos, com construtor e preferências persistentes por usuário/empresa.
Estado: **AGUARDANDO TESTES DO USUÁRIO**. A autorização desta funcionalidade não modifica os
estados das seis entregas acima e não autoriza as outras funcionalidades da
Entrega 4. Detalhes, métricas suportadas, limitações, endpoints e verificações:
[GRAFICOS_FINANCEIROS.md](GRAFICOS_FINANCEIROS.md).

Validação desta entrega independente: 76 testes backend, 65 testes frontend e
20 verificações HTTP reais passaram; builds concluídos. E2E de navegador não
executou interações por falta de bibliotecas do Chromium. V4 foi validada apenas
em banco descartável. Sem commit, deploy ou aprovação automática do usuário.

## Estado atual — Entrega 2

**AGUARDANDO TESTES DO USUÁRIO**. Implementadas contas a pagar/receber, vencimentos,
liquidações parciais, parcelamento e recorrências mensais finitas com histórico.
V5 validada somente em PostgreSQL descartável. Resultado final: **103 testes
backend**, **85 testes frontend** e **44 verificações HTTP reais** passaram;
builds concluídos. Não houve atualização dos serviços existentes, leitura de
arquivos de ambiente, instalação de dependências ou comandos Git de escrita.
E2E/validação visual continuam pendentes por bibliotecas do Chromium ausentes.

Detalhes, arquivos, endpoints e roteiro: [FINANCEIRO_ENTREGA_2.md](FINANCEIRO_ENTREGA_2.md).
Sugestão de commit: `feat: implementar contas a pagar e receber`.

Próxima entrega autorizável: **Entrega 3 — Integração com orçamentos e serviços**,
**NÃO AUTORIZADA**. Aguardar testes e commit manual do usuário. Só iniciar com
autorização explícita para a próxima entrega, após reler este arquivo e o Git.
