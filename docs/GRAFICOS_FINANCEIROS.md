# Gráficos financeiros personalizáveis

## Escopo e estado

Entrega independente autorizada pelo usuário: um painel por usuário da empresa,
com construtor de gráficos sobre os dados reais já disponíveis no financeiro.
Não autoriza nem aprova as Entregas 2–6 do plano financeiro original.

Estado: **AGUARDANDO TESTES DO USUÁRIO**. Implementada, sem aprovação automática.
O usuário testa e realiza seu commit manualmente.

## Diagnóstico

Java 21, Spring Boot 3.5.16, Spring Data JPA/Security e PostgreSQL/Flyway;
React/TypeScript, Vite, Tailwind e componentes próprios. O módulo financeiro
separa lançamentos, liquidações e movimentos efetivos, com estornos imutáveis.
Autenticação por sessão, CSRF, autorização por empresa e cache privado existentes
são reutilizados. Não existe biblioteca de gráficos instalada: a visualização
usa SVG/React, sem nova dependência. O Git estava limpo no início desta entrega.

## Métricas e limites do modelo existente

| Métrica | Regra |
|---|---|
| Receitas recebidas | Entradas efetivamente liquidadas, descontando seus estornos |
| Despesas pagas | Pagamentos operacionais efetivos, descontando seus estornos |
| Resultado operacional de caixa | Receitas recebidas menos despesas operacionais pagas; não é lucro contábil |
| Retiradas pessoais | Saídas pessoais, separadas das despesas operacionais |
| Saldo disponível | Referências de saldo inicial e todos os movimentos efetivos, incluindo transferências e retiradas |

Não há datas de vencimento no modelo atual. Contas a pagar/receber por vencimento
e valores vencidos ficam indisponíveis, mesmo existindo lançamentos pendentes.
Clientes existem no sistema, mas não estão associados aos lançamentos
financeiros. Serviços, custos/margens por serviço, cartões e subcategorias também
não estão disponíveis. O construtor apresenta as razões, sem inventar vínculos,
valores, categorias ou novas funcionalidades financeiras.

Cinco sugestões iniciais: Receitas versus despesas, Evolução do saldo, Despesas
por categoria, Resultado operacional de caixa e Retiradas pessoais. Excluir todos
os gráficos mantém o painel vazio; somente uma restauração explícita recria as
sugestões. Restaurar exige confirmação e não modifica movimentações financeiras.

## Personalização

- Navegação: **Movimentações → Gráficos → Contas e saldos**.
- Construtor em três etapas: métricas/filtros, visualização/período e título/revisão.
- Tipos: barras agrupadas, linhas, área, pizza e rosca.
- Agrupamentos: dia, semana, mês, ano, categoria e conta financeira.
- Períodos: 7/30 dias, mês atual, 3/6/12 meses, ano atual e personalizado.
- Filtros por múltiplas categorias e contas compatíveis.
- Edição completa, duplicação independente, exclusão e restauração confirmadas.
- Reordenação acessível, tamanhos pequeno/médio/grande e aparência configurável.
- Prévia consultada no backend, com atraso para agrupar alterações rápidas.
- Tooltips, legendas, unidades em reais e alternativa tabular acessível.
- Dados recalculados; os valores financeiros não são armazenados no gráfico.

Saldo é estoque: cada ponto temporal representa o fechamento do intervalo, sem
somar saldos diários. Saldo de abertura é apresentado separadamente. A referência
inicial de uma conta entra somente a partir da sua data. Transferências entram
no saldo de cada conta, mas se anulam no consolidado e não entram nos fluxos
operacionais. Correções seguem a data efetiva dos estornos do financeiro.
Saldo zero com histórico real é exibido como dado válido. Meses móveis incluem
o mês atual parcial e N−1 meses anteriores; semanas começam na segunda-feira.

Fluxos podem ser comparados por tempo ou conta. Saldo não pode ser misturado com
fluxos. Categoria exige uma métrica de receita ou despesa compatível; pizza e
rosca exigem uma única métrica de fluxo não negativa por categoria ou conta.
Resultado operacional e saldo não usam pizza/rosca, pois podem ser negativos.
As mesmas validações são aplicadas no backend.

## Banco e API

Migration criada: `V4__financial_charts.sql`. As configurações pertencem ao
usuário **e** à empresa autenticados; IDs de outro proprietário não são aceitos.
Nenhum valor financeiro agregado é persistido como dado estático do painel.

- `financial_chart_panels`: identifica a inicialização única e a versão do painel
  do usuário na empresa. Permite manter vazio um painel cujos gráficos foram
  removidos.
- `financial_charts`: configuração JSON, posição, versão e datas; vinculada ao
  painel e ao proprietário por chaves compostas. A configuração contém título,
  tipo, métricas, agrupamento, período, filtros, tamanho, paleta e rótulos.
- `financial_movement_chart_period_idx`: índice por empresa/data efetiva para
  consultas financeiras. As tabelas e migrations financeiras antigas não mudam.

Flyway aplica V4 ao iniciar a API atualizada. A migration e o mapeamento JPA foram
validados em PostgreSQL descartável. Não foi aplicada no banco existente.

Prefixo `/api/finance/charts`:

| Método | Caminho | Uso |
|---|---|---|
| GET | Sem sufixo | Painel e versão; inicialização única das sugestões |
| GET | `/catalog` | Métricas, agrupamentos, limitações e capacidades |
| POST | Sem sufixo | Criar gráfico |
| GET / PUT | `/{id}` | Consultar / editar configuração |
| POST | `/{id}/duplicate` | Duplicar configuração |
| DELETE | `/{id}?version=...` | Excluir configuração |
| PUT | `/order` | Persistir ordem completa com versão do painel |
| POST | `/restore` | Substituir personalizações pelas sugestões |
| GET | `/{id}/data` | Recalcular gráfico salvo |
| POST | `/preview` | Agregar dados para configuração ainda não salva |

Todas as rotas exigem sessão; escritas exigem CSRF. Métricas e agrupamentos são
catálogos fechados. Filtros são parâmetros e seus IDs são validados na empresa.
O frontend não envia SQL, nomes de tabelas nem colunas. Intervalos, filtros,
quantidade de gráficos e pontos são limitados. Valores monetários JSON são
strings decimais; agregações financeiras usam PostgreSQL/BigDecimal.
Limites: 24 gráficos por usuário, 50 IDs por filtro, até cinco anos e 370 pontos
por consulta. Versões protegem edição e mudanças concorrentes no painel.

## Verificações

Os testes de integração e E2E criam registros, portanto usam exclusivamente banco
descartável. Não executar contra o banco de trabalho ou de produção. Arquivos
`.env` e variantes não são lidos pelos scripts de validação desta entrega.

- `python3 scripts/test-finance-isolated.py`: **76 testes backend passaram**, sem
  falhas, erros ou ignorados. São 15 de integração e 3 unitários de gráficos,
  além dos 58 anteriores. Executou `mvn -B -ntp verify` e produziu o JAR.
  Artefatos: `/tmp/negocio-finance-validation-rjovjxgy`.
- Na primeira execução houve uma asserção de teste inadequada comparando a
  precisão de nanossegundos da entidade recém-criada com microssegundos do
  PostgreSQL. Foi corrigida para comparar identidade/configuração. A execução
  final acima passou. A revisão também encontrou e corrigiu a indicação de
  ausência de dados para saldos realmente zerados; há regressão automatizada.
- `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.json`: passou.
- `node node_modules/vitest/vitest.mjs run --config /tmp/negocio-facil-charts.config.mts`:
  **65 testes frontend passaram**, em nove arquivos. Incluem 28 testes de gráficos
  (15 de construtor/painel/regras e 13 do renderizador), mais 37 anteriores.
- `node node_modules/vite/bin/vite.js build --config /tmp/negocio-facil-charts.config.mts --outDir /tmp/negocio-facil-charts-final-build`:
  passou. JavaScript 568,27 KB (174,61 KB gzip), CSS 48,13 KB (10,26 KB gzip).
  Continua o aviso não bloqueante de chunk acima de 500 KB.
- A configuração temporária importa `frontend/vite.config.ts` com
  `envDir:false`, sem leitura de arquivos de ambiente. Package e lockfile não
  foram modificados.
- `python3 scripts/test-charts-e2e-isolated.py --jar /tmp/negocio-finance-validation-rjovjxgy/target/negocio-facil-0.1.0.jar --frontend /tmp/negocio-facil-charts-final-build --http-only`:
  **20 verificações HTTP reais passaram** através do Nginx, com aplicação e
  PostgreSQL novos. Cobrem sessão/CSRF, rota SPA, catálogo, combustível/pendências,
  comparação, saldo, cópia/edição independente, atualização de dados, ordem,
  novo login, isolamento, filtros indisponíveis, exclusão, restauração e painel
  vazio persistente. Artefatos: `/tmp/negocio-charts-e2e-50_sqt1z`.
- A tentativa do mesmo runner sem `--http-only` **não executou o fluxo de UI**:
  o navegador falhou antes da primeira interação por ausência de `libnspr4.so`.
  `ldd` também identificou `libnss3.so`, `libnssutil3.so` e `libasound.so` ausentes.
  Não foram instalados pacotes. Artefatos da tentativa:
  `/tmp/negocio-charts-e2e-7keft0lp`.
- `git diff --check`: passou. Sintaxe Python do runner: passou.

Os testes JUnit verificam também preferências privadas entre usuários da mesma
empresa, tentativas de IDOR em todas as operações, filtros de outra empresa,
rejeição de combinações/SQL arbitrário, datas efetivas, agrupamentos temporais,
estornos, saldo inicial antes/dentro do período, transferências, saldo realmente
zero e limites de consulta. Os testes frontend verificam as cinco visualizações,
filtros múltiplos, debounce, estados vazios/falhas, valores negativos/centavos,
tooltips por teclado e adaptação de dimensões via ResizeObserver.

O runner `scripts/test-finance-isolated.py` constrói o backend e executa JUnit
em PostgreSQL novo, sem portas publicadas, removendo somente containers/rede que
ele próprio criou. O runner `scripts/test-charts-e2e-isolated.py` recebe o JAR e
frontend já compilados, executa uma aplicação nova acessível somente por loopback
e remove seus containers/rede ao concluir. Não instala ferramentas/dependências.
Sem `--http-only`, executa também `frontend/e2e/financial-charts.spec.ts`. Esse
fluxo exige as bibliotecas de sistema do Chromium, além do binário Playwright.
O teste é ignorado por padrão fora do runner para evitar gravar no banco em uso.

## Limitações restantes

- Revisão visual real e E2E de navegador não concluídos devido às bibliotecas
  de sistema ausentes. Responsividade foi verificada por testes de componente;
  não há captura visual aprovada nesta entrega.
- Não houve deploy, reinício dos serviços existentes ou aplicação de V4 ao banco
  de trabalho. Fontes modificados não atualizam imagens Docker já iniciadas.
- Clientes/serviços/subcategorias, vencimentos e cartões permanecem indisponíveis
  pelos limites do modelo atual. O catálogo e a interface explicam os motivos.
- Atualização automática dos dados a cada minuto com a aba ativa, ao atualizar
  explicitamente e por invalidação após operações no financeiro; não há canal
  em tempo real entre sessões diferentes.
- Reordenação usa controles acessíveis Subir/Descer, sem arrastar e soltar.
- Aviso de bundle acima de 500 KB continua não bloqueante. Não há outro bloqueio
  de funcionamento conhecido nas verificações executadas.

## Arquivos da entrega

Backend, em `backend/src/main/java/com/negociofacil/finance/charts/`:

- `ChartTypes.java`, `ChartDtos.java` e `ChartRules.java`: catálogo, contratos,
  normalização, períodos e combinações financeiras válidas.
- `ChartAggregation.java`: consultas agregadas de fluxo e saldo, com estornos e
  filtros autorizados.
- `ChartService.java` e `ChartController.java`: preferências, inicialização,
  duplicação, ordem, restauração, prévia e dados.
- `FinancialChart.java`, `FinancialChartPanel.java` e respectivos repositories:
  persistência das configurações e controle de versão.
- `backend/src/main/resources/db/migration/V4__financial_charts.sql`: tabelas,
  constraints e índice.
- `backend/src/test/java/com/negociofacil/finance/charts/ChartIntegrationTest.java`
  e `ChartRulesTest.java`: integração, autorização, cálculos e limites.

Frontend, em `frontend/src/finance/charts/`:

- `Charts.tsx`: painel, menus, confirmação, atualização e reordenação.
- `ChartBuilder.tsx`: construtor, edição, prévia e validação.
- `ChartVisual.tsx`: cinco visualizações SVG e consulta acessível dos dados.
- `ChartFilters.tsx`, `types.ts` e `rules.ts`: filtros, contratos, rótulos e
  validação compatível com a API.
- `charts.css`: layout, temas e responsividade.
- `charts.test.tsx` e `ChartVisual.test.tsx`: testes de componentes e regras.
- `frontend/src/App.tsx`: rota protegida.
- `frontend/src/finance/shared.tsx`: aba intermediária Gráficos.
- `frontend/e2e/financial-charts.spec.ts`: fluxo completo com dados reais no
  ambiente descartável; ignorado por padrão fora do runner isolado.

Complementos: `scripts/test-charts-e2e-isolated.py`, este documento,
`docs/FINANCEIRO_IMPLEMENTACAO.md` e o link correspondente no `README.md`.

## Roteiro manual

1. Atualizar backend/frontend pelo fluxo habitual; Flyway aplicará V4. O agente
   não atualiza nem reinicia os serviços existentes nesta entrega.
2. Entrar em **Financeiro → Gráficos**. Conferir a posição da aba e as cinco
   sugestões. Sem movimentos, deve haver estado vazio honesto.
3. Registrar uma despesa paga de R$ 120 em Transporte, uma de R$ 80 em Materiais
   e uma pendente de R$ 400 em Transporte, na mesma conta e período.
4. Adicionar gráfico: Despesas pagas, categoria Transporte, agrupamento Mês,
   últimos 12 meses, Barras, título **Meus gastos com combustível**. Se houver
   categoria própria de combustível, pode selecioná-la; nenhuma é criada
   automaticamente. A prévia deve totalizar R$ 120, sem a pendência.
5. Selecionar Transporte e Materiais, restringir à conta usada e conferir R$ 200.
   Selecionar outra conta sem movimentos deve exibir ausência de resultados.
6. Registrar receita recebida de R$ 1.000. Criar **Receitas x Despesas** com ambas
   as métricas, Mês, últimos seis meses, Barras. Esperado: R$ 1.000 / R$ 200.
7. Salvar, atualizar a página, sair e entrar novamente. Configurações devem ser
   recuperadas, mantendo os dados atuais.
8. Editar: trocar barras por linhas, período, filtros e título. Conferir prévia
   e cartão atualizado sem recarregar a página inteira.
9. Duplicar, alterar a categoria da cópia e conferir que o original não muda.
10. Personalizar painel: mover gráficos e alterar tamanho. Atualizar a página e
    conferir a persistência da ordem e das preferências.
11. Registrar nova despesa paga de R$ 30 em Transporte e voltar ao painel. O
    gráfico deve passar a R$ 150 nessa categoria; não é um valor estático salvo.
12. Conferir saldo inicial, retirada e transferência. Retirada não entra em
    despesas operacionais; transferência não entra em receitas/despesas; saldo
    consolidado é preservado na transferência.
13. Excluir um gráfico. Restaurar padrões exige confirmação e deve modificar
    somente as configurações, preservando contas e movimentações.
14. Validar em celular, teclado e modo escuro. Tooltips/seleção e tabela de dados
    devem permitir consultar valores sem depender exclusivamente de cores.
15. Entrar em outra empresa/usuário: painel próprio, sem gráficos privados da
    primeira sessão. IDs e filtros alheios devem ser rejeitados pela API.

## Continuidade

Sugestão de commit: `feat(finance): adicionar gráficos financeiros personalizáveis`.
Não iniciar outra entrega após este trabalho; aguardar avaliação explícita.
