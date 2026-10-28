# Entrega 2 — Contas a pagar e receber

## Objetivo, autorização e estado

Permitir controlar vencimentos, parcelas, pagamentos/recebimentos parciais e
despesas mensais recorrentes, preservando os saldos reais e o histórico.

Autorização expressa em 28/10/2026: **"Sim, implementar a Entrega 2"**. Estado:
**AGUARDANDO TESTES DO USUÁRIO**. O Git estava limpo no início. A autorização não inicia as
Entregas 3–6 nem equivale à aprovação automática das funcionalidades anteriores.
O usuário testa e realiza seu próprio commit. Sem git add/commit/push/merge/rebase.

## Diagnóstico e decisões

- O stack permanece Java 21/Spring Boot 3.5.16/JPA/Security, PostgreSQL/Flyway e
  React/TypeScript/Vite/Tailwind. Sessão, CSRF e isolamento por empresa existentes
  são reutilizados. Nenhuma dependência nova é necessária.
- O modelo anterior tinha lançamentos simples, uma liquidação ativa por
  lançamento e movimentos imutáveis com estornos. O novo modelo distingue
  obrigação, parcela, liquidação e movimento de saldo.
- A obrigação é o agrupador. Cada `financial_entries` representa uma parcela;
  `financial_settlements` registra cada pagamento/recebimento e
  `financial_movements` registra somente seus efeitos efetivos no caixa.
- Receita/despesa antiga vira obrigação de parcela única, sem inventar data de
  vencimento. Retiradas continuam separadas das obrigações operacionais.
- Parcelas mensais são calculadas em centavos, sem ponto flutuante. A soma na
  criação preserva exatamente o total, com distribuição dos centavos restantes.
  Datas mensais usam o dia original, ajustado ao último dia de cada mês.
- Recorrências são despesas mensais com quantidade finita, limitada a 60
  ocorrências. A geração é limitada e idempotente, sem tarefa que gere registros
  infinitamente. Não há combinação de parcelamento com recorrência nesta etapa.
- Somente liquidações alteram saldo. Uma cobrança de R$ 1.500 recebida em R$ 500
  e R$ 1.000 tem saldo pendente de R$ 1.000 após a primeira operação e zero após
  a segunda. Valores excedentes são rejeitados, sem criar créditos implícitos.
- Datas e vencimento usam São Paulo. Vencida significa vencimento anterior a
  hoje e saldo pendente positivo. Pendências antigas sem vencimento ficam
  identificadas como tal, sem serem marcadas arbitrariamente como vencidas.
- Edição de próximas ocorrências preserva as passadas, quitadas, parciais e
  canceladas, bem como qualquer ocorrência com histórico de liquidação, mesmo
  totalmente estornado. Alterações e estornos são auditados. Pagamentos não são
  apagados.
- Campos financeiros de parcelas com histórico de liquidações são preservados;
  descrição, competência, vencimento e notas podem ser editados. Para corrigir
  valor/conta/categoria, cancelar e cadastrar uma nova obrigação. A edição rápida
  integral de lançamentos QUICK/LEGACY quitados mantém a regra da Entrega 1:
  estorna as liquidações anteriores e registra os novos valores, com auditoria.
- A parcela única rápida preserva seu vencimento ao ser editada em Movimentações.
  Chaves de idempotência anteriores à V5 continuam reconhecidas quando os novos
  campos opcionais não foram informados.
- Cancelamento/estorno é uma correção do controle financeiro, não uma operação
  bancária de reembolso. A interface deve explicar seu efeito no saldo.

## Escopo

- Telas Contas a receber e Contas a pagar, acrescentadas após as abas existentes.
- Cadastro de obrigação simples ou parcelada, vencimento, conta e categoria.
- Pagamentos/recebimentos integrais ou parciais, com histórico individual.
- Consulta de parcelas, filtros e estados pendente/parcial/quitado/vencido/cancelado.
- Edição de uma parcela ou ocorrência e de próximas ocorrências compatíveis.
- Recorrência mensal de despesa com quantidade explícita e idempotência.
- Compatibilidade com Movimentações, extrato, saldos e gráficos de caixa.

Fora do escopo: integração com orçamento, cliente/serviço, cobranças externas,
cartões, relatórios, dashboard completo, previsões, alertas/e-mails e onboarding.
Gráficos de obrigações/vencimentos não serão antecipados; os gráficos existentes
continuam mostrando dinheiro efetivo, inclusive liquidações parciais.

## API implementada

Todos os caminhos abaixo usam o prefixo `/api/finance`, sessão e CSRF nas escritas.
Empresa e autor vêm da sessão, nunca de um `companyId` enviado pelo frontend.

| Método | Caminho | Uso |
|---|---|---|
| POST | `/obligations` | Criar obrigação e parcelas, com chave de idempotência |
| GET | `/obligations/{id}` | Consultar obrigação e suas parcelas |
| GET | `/installments` | Listar parcelas por natureza e filtros |
| GET / PUT | `/installments/{id}` | Consultar / editar uma parcela |
| POST | `/entries/{id}/settle` | Registrar liquidação de valor parcial ou restante |
| POST | `/entries/{id}/cancel` | Cancelar e estornar liquidações ativas |
| GET | `/entries/{id}/settlements` | Histórico de pagamentos/recebimentos |
| POST | `/entries/{id}/settlements/{settlementId}/reverse` | Estorno individual com motivo |
| POST | `/recurrences` | Criar despesa mensal finita |
| GET | `/recurrences/{id}` | Consultar configuração da série |
| PUT | `/recurrences/{id}/future` | Editar próximas ocorrências compatíveis |
| POST | `/recurrences/{id}/cancel-future` | Cancelar próximas ocorrências sem pagamentos |
| GET | `/history/OBRIGACAO/{id}` e `/history/RECORRENCIA/{id}` | Auditoria |

Filtros de parcelas: natureza, situação, conta, categoria, descrição, intervalo de
vencimento e paginação. `PENDENTE` e `PARCIAL` filtram o estágio da liquidação e
podem incluir parcelas vencidas; `VENCIDO` inclui pendentes e parciais com
vencimento anterior a hoje.
O valor pago e a indicação de atraso permanecem visíveis independentemente do
filtro. Não há valores monetários binários: JSON decimal string, BigDecimal e
NUMERIC(16,2).

## Banco de dados

Migration criada: `V5__financial_obligations.sql`.

- `financial_obligations`: obrigação, total, quantidade de parcelas, origem,
  vínculo com recorrência, versão e chave de idempotência.
- `financial_recurrences`: despesa mensal, primeiro vencimento, quantidade finita,
  configuração, versão e chave de idempotência.
- `financial_entries`: `obligation_id`, `installment_number`, `due_date` e estado
  `PARCIAL`. Continua sendo a parcela, separada da obrigação e dos pagamentos.
- Removido somente o índice que limitava uma liquidação ativa por parcela.
  Permanecem unicidade das chaves, dos movimentos por liquidação e dos estornos.
- FKs compostas por empresa/natureza, parcela e ocorrência únicas por agrupador,
  checks e índices de vencimento e liquidações ativas.
- Dados legados vinculados por empresa/UUID, usando identidades normais do banco,
  sem presumir IDs iguais entre tabelas nem inventar vencimentos.

Migrations V1–V4 não foram editadas. A V5 será aplicada pelo Flyway no início do
backend atualizado; não foi aplicada no banco existente pelo agente.

## Verificações

TypeScript passou, **85 testes frontend passaram** em dez arquivos e o build
Vite foi concluído. São 20 testes novos de formulários/parcelas/recorrências,
além dos 65 anteriores. O bundle JavaScript tem 599,63 KB (182,48 KB gzip), com
aviso não bloqueante acima de 500 KB; CSS 51,68 KB (10,80 KB gzip).

Comandos frontend executados em `frontend/`:

```bash
node node_modules/typescript/bin/tsc --noEmit -p tsconfig.json
node node_modules/vitest/vitest.mjs run --config /tmp/negocio-facil-delivery2.config.mts
node node_modules/vite/bin/vite.js build --config /tmp/negocio-facil-delivery2.config.mts --outDir /tmp/negocio-facil-delivery2-build
```

Backend: `python3 scripts/test-finance-isolated.py` passou com **103 testes**,
zero falhas, erros ou ignorados. Inclui 23 testes de integração e quatro unitários
da Entrega 2, além dos 76 de regressão anteriores. Executou `mvn -B -ntp verify`
em Java 21/Maven/PostgreSQL 17, produziu o JAR e validou a V5/mapeamento JPA.
Artefatos e relatórios: `/tmp/negocio-finance-validation-p5fo1xi8`.

As verificações cobrem R$ 1.500 recebidos em R$ 500 + R$ 1.000, excesso de valor,
duas requisições concorrentes e chaves duplicadas, versão a cada pagamento,
liquidações antigas repetidas após migração, estorno individual/cancelamento,
campos financeiros imutáveis após histórico, total da obrigação ao editar uma
parcela, vencimento/legados sem data, mês curto/bissexto, recorrências limitadas,
preservação de passadas/pagas/parciais, gráfico somente com dinheiro efetivo,
contas com pendência parcial, IDs/filtros de outra empresa e constraints do banco.
Um cenário V4→V5 comprova backfill sem vencimentos inventados nem alteração de
movimentos/saldos existentes.

As execuções intermediárias passaram com 76 e 99 testes; a execução final de
103 acima inclui todos os últimos ajustes e é a referência desta entrega.

Testes de integração truncam tabelas, portanto não foram executados no banco existente. O runner
`scripts/test-finance-isolated.py` cria um PostgreSQL novo em tmpfs e compila o
backend em uma cópia temporária explícita dos fontes. A validação frontend usa
configuração temporária com `envDir:false`; não lê `.env` ou variantes.

O ambiente anterior não possuía bibliotecas necessárias para iniciar Chromium.
Não instalar pacotes nem declarar E2E aprovado sem uma execução real. Fluxos HTTP
foram verificados em aplicação descartável, sem tocar serviços existentes.
O runner HTTP existente aceita `--http-only --delivery2` para executar o fluxo
anterior dos gráficos e o novo helper `scripts/delivery2_http_checks.py`, com
sessão/CSRF pelo Nginx. O helper é chamado somente após o runner criar sua própria
aplicação e banco; não recebe a URL do ambiente em uso como argumento de CLI.

Comando HTTP executado:

```bash
python3 scripts/test-charts-e2e-isolated.py --jar /tmp/negocio-finance-validation-p5fo1xi8/target/negocio-facil-0.1.0.jar --frontend /tmp/negocio-facil-delivery2-build --http-only --delivery2
```

**44 verificações HTTP reais passaram**: 20 de regressão de gráficos e 24 de
obrigações/parcelas/liquidações/recorrências, com sessão e CSRF pelo Nginx.
Artefatos: `/tmp/negocio-charts-e2e-1hz7kc6h`. Somente os containers/rede criados
pelos runners foram removidos; os serviços e dados existentes não foram usados.

`git diff --check` passou e os verificadores Python tiveram sintaxe validada.
Package, lockfile e `pom.xml` não foram modificados. Nenhum comando de staging,
commit, push, merge ou rebase foi executado.

## Pendências e limitações

- Nenhum bloqueio de funcionamento conhecido nos testes executados. Aprovação
  funcional e visual do usuário continua pendente.
- E2E/revisão visual em navegador e celular não foram executados nesta etapa.
  Foi reconfirmada via `ldd` a ausência de `libnspr4.so`, `libnss3.so`,
  `libnssutil3.so`, `libsmime3.so` e `libasound.so.2` no navegador instalado.
  Não foram instaladas bibliotecas nem declarado o fluxo de navegador aprovado.
- Os serviços existentes não foram reiniciados nem receberam os novos artefatos.
  Para usar a entrega, o usuário precisa reconstruir backend/frontend. A V5 é
  aplicada pelo Flyway nesse início; não foi aplicada ao banco de trabalho aqui.
- Recorrências geram exatamente a quantidade informada, sem renovação automática.
  Limites: 60 ocorrências mensais e 120 parcelas; sem parcelas de valor zero.
- Os lançamentos legados permanecem sem vencimento até edição explícita.
- Registros com histórico de liquidação preservam valor/conta/categoria. O
  estorno não apaga esse histórico; cancelar e criar outra obrigação é o caminho
  para mudar esses campos nas telas de contas.
- Gráficos de vencimento, integração com cliente/orçamento/serviço, cartões,
  dashboard completo, previsões, relatórios, alertas e e-mails não fazem parte
  desta entrega. Nenhuma funcionalidade da Entrega 3 foi iniciada.
- Permanece o aviso de bundle acima de 500 KB, sem falha de compilação.

## Arquivos da entrega

Criados no backend:

- `backend/src/main/java/com/negociofacil/finance/obligations/FinancialObligation.java`
  e `FinancialObligationRepository.java`: agrupador das parcelas.
- `FinancialRecurrence.java` e `FinancialRecurrenceRepository.java`: definição
  mensal finita.
- `ObligationDtos.java`, `ObligationRules.java`, `ObligationService.java` e
  `ObligationController.java`: contratos, centavos/datas, operações e endpoints.
- `backend/src/main/resources/db/migration/V5__financial_obligations.sql`.
- `backend/src/test/java/com/negociofacil/finance/obligations/ObligationIntegrationTest.java`
  e `ObligationRulesTest.java`.

Modificados no backend: `finance/FinanceController.java`, `FinanceDtos.java`,
`FinanceService.java`, `FinanceTypes.java`, `FinancialEntry.java`,
`FinancialEntryRepository.java` e `FinancialSettlementRepository.java`. São as
integrações necessárias de liquidações múltiplas, status, versões, histórico e
compatibilidade das rotas anteriores. `finance/charts/ChartService.java` alterou
somente as mensagens das métricas ainda indisponíveis, pois vencimentos agora
existem nas telas de contas; não acrescentou gráficos futuros.

Criados em `frontend/src/finance/obligations/`:

- `Obligations.tsx`, `InstallmentTable.tsx` e `ObligationDetails.tsx`: listagem,
  filtros, ações e consulta das parcelas.
- `ObligationForm.tsx`: obrigação simples, parcelada ou recorrente mensal.
- `InstallmentEditor.tsx` e `RecurrenceEditor.tsx`: edição individual e futura.
- `PaymentHistory.tsx`: histórico e estorno com motivo.
- `types.ts`, `rules.ts`, `obligations.css` e `obligations.test.tsx`.

Modificados no frontend: `App.tsx`, `finance/types.ts`, `forms.tsx`,
`Movements.tsx` e `shared.tsx`, para rotas, abas e integração monetária/histórico.
As expectativas de navegação em `finance/charts/charts.test.tsx` e
`frontend/e2e/financial-charts.spec.ts` foram atualizadas de três para cinco abas,
preservando sua ordem anterior.

Complementos: `scripts/delivery2_http_checks.py`,
`scripts/test-charts-e2e-isolated.py`, este documento, o acompanhamento geral,
as notas de compatibilidade em `docs/GRAFICOS_FINANCEIROS.md` e o `README.md`.

## Roteiro manual previsto

1. Atualizar as imagens de backend/frontend pelo fluxo habitual, mantendo o banco
   persistente. O agente não reinicia serviços existentes. Flyway aplica V5 ao
   iniciar o backend atualizado; verificar versão 5 com `success=true`.
2. Abrir **Financeiro → Contas a receber**. Criar cobrança de R$ 1.500, vencimento
   hoje, parcela única. Antes do recebimento, o saldo da conta não muda.
3. Receber R$ 500. Conferir pago R$ 500, pendente R$ 1.000, estado Parcial e
   aumento de R$ 500 na conta/extrato/gráfico de receitas.
4. Receber R$ 1.000. Conferir Quitado, pendente zero e dois recebimentos no
   histórico. Tentar receber valor excedente: operação rejeitada.
5. Em **Contas a pagar**, cadastrar R$ 1.200 em seis parcelas. Conferir seis
   parcelas de R$ 200 e datas mensais. Pagar uma delas não liquida as outras.
6. Cadastrar R$ 100 em três parcelas e conferir total exato de R$ 100, com
   distribuição correta dos centavos. Testar primeiro vencimento no dia 31.
7. Criar despesa recorrente mensal de R$ 90 com seis ocorrências. Conferir
   quantidade finita. Repetir a requisição com a mesma chave não deve duplicar.
8. Editar somente uma ocorrência e conferir que as demais permanecem iguais.
9. Pagar uma ocorrência futura, escolher outra futura pendente e editar as
   próximas. Conferir preservação das passadas/pagas/parciais e histórico.
10. Criar parcela vencida e uma sem vencimento legado. A primeira deve aparecer
    no filtro Vencidas; a segunda exige definição de vencimento para identificar
    atraso. Testar filtros e estados vazios.
11. Estornar um recebimento individual com motivo e conferir recomposição do
    saldo pendente e do extrato. Cancelar uma parcela parcialmente liquidada
    exige confirmação e preserva seu histórico de estornos.
12. Em outra empresa, tentar consultar/editar/liquidar/cancelar IDs da primeira:
    a API deve rejeitar sem revelar os registros.
13. Conferir os temas, uma tela estreita e as abas anteriores. Transferências e
    retiradas permanecem separadas dos resultados operacionais.

## Continuidade

Sugestão de commit: `feat: implementar contas a pagar e receber`.

Próxima entrega autorizável: **Entrega 3 — Integração com orçamentos e serviços**,
ainda NÃO AUTORIZADA. Parar após a Entrega 2 e aguardar testes/commit do usuário.
