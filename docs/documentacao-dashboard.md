# Dashboard Administrativo - PediuPatrão

Documentação da atividade "Dashboard Administrativo": análise de requisitos, decisões de projeto, API e evidências de testes.

Sumário

1. Escopo e usuários
2. Requisitos funcionais
3. Requisitos não funcionais
4. Decisões de análise e projeto
5. Modelo de dados utilizado
6. Documentação da API
7. Dashboard (tela)
8. Evidências de testes
9. Fluxo de branches (gitflow)

## 1. Escopo e usuários

O dashboard permite que o responsável pelo estabelecimento acompanhe vendas, situação dos pedidos, produtos mais vendidos
e horários de maior movimento, com filtros por período, status e tipo de produto.

Os dados vêm de uma API própria (`/api/dashboard/**`) implementada no mesmo projeto e ligada ao MongoDB. Por conterem
informação comercial, a API e a tela são restritas aos perfis **ADMIN** e **GERENTE** (o ATENDENTE não tem acesso).

## 2. Requisitos funcionais

| id | requisito | como foi atendido |
|----|-----------|-------------------|
| RF01 | Indicadores gerais de vendas | Cartões com receita, pedidos válidos, ticket médio, cancelamentos (quantidade e taxa), descontos concedidos, itens vendidos e clientes atendidos. Endpoint `GET /api/dashboard/resumo`. |
| RF02 | Análise de vendas (gráficos) | Receita por período (linha, por dia ou mês), receita por categoria (pizza) e pedidos por horário (barras). Endpoints `vendas-por-periodo`, `vendas-por-categoria` e `vendas-por-hora`. |
| RF03 | Situação dos pedidos | Gráfico de rosca com a quantidade e o percentual de pedidos em cada status. Endpoint `pedidos-por-status`. |
| RF04 | Produtos mais vendidos | Ranking (gráfico de barras e tabela) por quantidade, com receita do produto. Endpoint `produtos-mais-vendidos` com limite configurável. |
| RF05 | Filtros de consulta | Período (datas e atalhos: hoje, 7 dias, 30 dias, este mês), status, tipo de produto e agrupamento (dia ou mês). Os mesmos parâmetros são aceitos pela API. |
| RF06 | Integração com a API | A tela não calcula nada: cada indicador e gráfico vem de uma chamada `fetch` aos endpoints, que consultam o MongoDB. |
| RF07 | Atualização e tratamento de dados | Botão "Atualizar", atualização automática a cada 60 s (pode ser desligada), horário da última atualização, indicação visual durante o carregamento, mensagens de erro da API exibidas na tela, estados vazios, redirecionamento ao login quando a sessão expira, validação dos parâmetros na API (erros 400) e tratamento de dados antigos sem valores ou sem tipo de produto. |

Premissas adotadas (o enunciado não detalha estes pontos):

- **Receita** = soma do valor final dos pedidos (já com desconto), excluindo os cancelados.
- **Ticket médio** = receita / pedidos válidos (não cancelados).
- **Taxa de cancelamento** = cancelados / total de pedidos do filtro.
- O filtro de **tipo de produto** vale para o ranking e para a receita por categoria; os indicadores gerais continuam
  considerando o pedido inteiro.
- O filtro de **status** vale para indicadores, série de vendas, horários, ranking e categorias. O gráfico de situação dos
  pedidos ignora esse filtro de propósito, para sempre mostrar a distribuição completa.
- O período é filtrado pela data/hora de **entrada** do pedido.

## 3. Requisitos não funcionais

| id | requisito | atendimento |
|----|-----------|-------------|
| RNF01 | Segurança | Acesso restrito a ADMIN e GERENTE no `SecurityConfig` (por rota) e no serviço (`@PreAuthorize`). API somente leitura: métodos diferentes de GET retornam 403. Sem login, 401 em JSON. |
| RNF02 | Desempenho | Consulta filtrada por data de entrada com índice (`@Indexed` em `dataEntrada`); período máximo de 366 dias; ranking limitado a 50 itens. Os gráficos são carregados em paralelo. |
| RNF03 | Usabilidade | Layout responsivo (Bootstrap), valores em reais e números no padrão brasileiro, atalhos de período e legendas nos gráficos. |
| RNF04 | Confiabilidade | Parâmetros inválidos geram erro 400 com mensagem clara; dados antigos incompletos não quebram os cálculos; falha em uma chamada não derruba a página. |
| RNF05 | Precisão numérica | Valores monetários com `BigDecimal` e arredondamento `HALF_UP` com duas casas. |
| RNF06 | Manutenibilidade | Código em camadas (controller, serviço, DTOs), sem lógica de cálculo na tela; testes automatizados dos cálculos e da API. |
| RNF07 | Compatibilidade | Java 21, Spring Boot 3.4, MongoDB; navegadores atuais (Chart.js 4 e Bootstrap 5 via CDN). |
| RNF08 | Privacidade | O dashboard trabalha só com dados agregados (nenhum dado pessoal de cliente é exibido). |

## 4. Decisões de análise e projeto

1. **Um endpoint por visão.** Cada gráfico ou indicador tem seu endpoint (`resumo`, `vendas-por-periodo`, `pedidos-por-status`
   etc.). Isso permite que a tela carregue as partes em paralelo, que uma falha afete só uma parte, e que cada endpoint seja
   testado e documentado separadamente. A alternativa, um único endpoint gigante, acoplaria todos os gráficos.
2. **Filtros iguais em todos os endpoints.** `inicio`, `fim`, `status` e `tipo` têm o mesmo significado em qualquer rota, o que
   simplifica a tela e a documentação. A validação fica concentrada em um só ponto (`FiltroDashboard`).
3. **Agregação na aplicação, filtro no banco.** O MongoDB filtra os pedidos pelo intervalo de datas (com índice) e o serviço
   agrega em memória. O motivo é que o projeto guarda valores monetários como `BigDecimal`, que o Spring Data grava como texto
   no MongoDB; isso impede somar com o pipeline de agregação sem mudar a representação dos dados já gravados. Para o volume de
   uma pizzaria o custo é baixo; o ponto de evolução, se o volume crescer, seria migrar os valores para `Decimal128` e usar
   `$group` no banco.
4. **Série temporal sem buracos.** A API devolve todos os dias (ou meses) do período, inclusive os sem vendas, com valor zero.
   Assim o gráfico não precisa "adivinhar" lacunas.
5. **Pedidos cancelados fora da receita**, mas contados no total e na taxa de cancelamento, para não esconder o problema.
6. **Tipo do produto guardado no item do pedido** (`tipoProduto`). Se o cadastro do produto mudar depois, o histórico de vendas
   continua coerente. Para pedidos antigos sem esse campo, o serviço consulta o cadastro de produtos.
7. **Somente leitura.** Não há operação de escrita no dashboard.
8. **Dados de demonstração opcionais**, ativados por variável de ambiente, para apresentar o painel sem depender de dados
   reais (ver o README).

## 5. Modelo de dados utilizado

Coleções do MongoDB consultadas:

- `pedidos`: `dataEntrada`, `status`, `valorTotal`, `valorDesconto`, `clienteId` e a lista `itens`
  (`produtoId`, `nomeProduto`, `tipoProduto`, `quantidade`, `precoUnitario`, `subtotal`).
- `produtos`: `tipo`, usado para o filtro de tipos e para pedidos antigos.

Alterações feitas para o dashboard: campo `tipoProduto` em `ItemPedido`, índice em `Pedido.dataEntrada`
e a consulta `buscarPorPeriodo` no `PedidoRepository`.

## 6. Documentação da API

Base: `/api/dashboard`. Todos os endpoints são `GET`, exigem login com perfil **ADMIN** ou **GERENTE** e respondem JSON.

### Parâmetros comuns

| parâmetro | tipo | obrigatório | descrição |
|-----------|------|:-----------:|-----------|
| `inicio` | data `AAAA-MM-DD` | não | primeiro dia do período. Padrão: 29 dias antes de `fim`. |
| `fim` | data `AAAA-MM-DD` | não | último dia do período (inclusive). Padrão: hoje. |
| `status` | texto | não | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO` ou `CANCELADO`. |
| `tipo` | texto | não | tipo de produto (ex.: `Pizza`). Sem distinção de maiúsculas. |

Regras: `inicio` não pode ser posterior a `fim`; o período máximo é de 366 dias.

### Erros possíveis (todos os endpoints)

Corpo padrão: `{ "mensagem": "texto explicativo" }`

| status | quando | exemplo de mensagem |
|--------|--------|---------------------|
| 400 | data em formato inválido | `Data inválida em 'inicio': use o formato AAAA-MM-DD.` |
| 400 | início depois do fim | `A data inicial não pode ser posterior à data final.` |
| 400 | período acima de 366 dias | `O período máximo de consulta é de 366 dias.` |
| 400 | status inexistente | `Status inválido: ENTREGUE` |
| 400 | parâmetro com tipo errado | `Valor inválido para o parâmetro 'limite'.` |
| 401 | sem login | `Autenticação necessária.` |
| 403 | perfil sem permissão ou método diferente de GET | `Você não tem permissão para esta operação.` |
| 500 | erro inesperado | `Erro interno ao processar a requisição.` |

### 6.1 `GET /api/dashboard/resumo` (RF01)

Parâmetros: `inicio`, `fim`, `status`.

```
GET /api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10
```

```json
{
  "inicio": "2026-11-01",
  "fim": "2026-11-10",
  "receitaTotal": 1500.00,
  "totalPedidos": 30,
  "pedidosValidos": 28,
  "pedidosCancelados": 2,
  "taxaCancelamento": 6.7,
  "ticketMedio": 53.57,
  "descontosConcedidos": 40.00,
  "itensVendidos": 70,
  "clientesAtendidos": 12
}
```

### 6.2 `GET /api/dashboard/vendas-por-periodo` (RF02)

Parâmetros: `inicio`, `fim`, `status`, `agrupamento` (`dia`, padrão, ou `mes`).

```
GET /api/dashboard/vendas-por-periodo?inicio=2026-11-08&fim=2026-11-10&agrupamento=dia
```

```json
[
  { "rotulo": "2026-11-08", "receita": 162.00, "pedidos": 2 },
  { "rotulo": "2026-11-09", "receita": 50.00,  "pedidos": 1 },
  { "rotulo": "2026-11-10", "receita": 0.00,   "pedidos": 0 }
]
```

Erro adicional: `400` com `Agrupamento inválido: use 'dia' ou 'mes'.`

### 6.3 `GET /api/dashboard/vendas-por-hora` (RF02)

Parâmetros: `inicio`, `fim`, `status`. Devolve sempre 24 posições (00h a 23h) com os pedidos não cancelados.

```json
[
  { "rotulo": "18h", "receita": 410.00, "pedidos": 9 },
  { "rotulo": "19h", "receita": 680.50, "pedidos": 14 }
]
```

### 6.4 `GET /api/dashboard/vendas-por-categoria` (RF02)

Parâmetros: `inicio`, `fim`, `status`, `tipo`. Ordenado por receita (maior primeiro).

```json
[
  { "tipo": "Pizza",  "quantidade": 55, "receita": 2540.00 },
  { "tipo": "Bebida", "quantidade": 30, "receita": 360.00 }
]
```

### 6.5 `GET /api/dashboard/pedidos-por-status` (RF03)

Parâmetros: `inicio`, `fim`. Sempre traz os 7 status, mesmo com zero pedidos.

```json
[
  { "status": "RECEBIDO",  "descricao": "Recebido",   "quantidade": 2,  "percentual": 6.7 },
  { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 20, "percentual": 66.7 },
  { "status": "CANCELADO",  "descricao": "Cancelado",  "quantidade": 2,  "percentual": 6.7 }
]
```

### 6.6 `GET /api/dashboard/produtos-mais-vendidos` (RF04)

Parâmetros: `inicio`, `fim`, `status`, `tipo`, `limite` (inteiro de 1 a 50; padrão 5). Pedidos cancelados não entram.
Ordenação: quantidade (maior primeiro) e, em empate, receita. A `receita` do produto é o valor dos itens antes do desconto do pedido.

```
GET /api/dashboard/produtos-mais-vendidos?tipo=Pizza&limite=3
```

```json
[
  { "produtoId": "673f...", "nome": "Calabresa", "tipo": "Pizza", "quantidade": 22, "receita": 1056.00 },
  { "produtoId": "673f...", "nome": "Mussarela", "tipo": "Pizza", "quantidade": 15, "receita": 630.00 }
]
```

Erro adicional: `400` com `O limite deve estar entre 1 e 50.`

### 6.7 `GET /api/dashboard/tipos-de-produto` (RF05)

Sem parâmetros. Lista os tipos cadastrados, usada para montar o filtro da tela.

```json
["Bebida", "Esfiha", "Lanche", "Pizza"]
```

### Exemplos com `curl`

A API usa sessão (login por formulário). Exemplo:

```bash
# 1) pega o token CSRF da tela de login e entra como gerente
curl -s -c cookies.txt http://localhost:8080/login -o login.html
TOKEN=$(grep -o 'name="_csrf" value="[^"]*"' login.html | sed 's/.*value="//;s/"//')
curl -s -b cookies.txt -c cookies.txt -d "username=gerente" -d "password=gerente123" -d "_csrf=$TOKEN" http://localhost:8080/login

# 2) consulta o resumo
curl -s -b cookies.txt "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10"

# 3) erro 400
curl -s -b cookies.txt "http://localhost:8080/api/dashboard/resumo?inicio=10/11/2026"

# 4) erro 401 (sem sessão)
curl -s -i http://localhost:8080/api/dashboard/resumo
```

## 7. Dashboard (tela)

Acesso: menu lateral, item **Dashboard** (visível para ADMIN e GERENTE), rota `/dashboard`.

Componentes: filtros no topo; cartões de indicadores (RF01); gráfico de linha de receita por período (RF02); rosca de situação
dos pedidos (RF03); barras de produtos mais vendidos e tabela de ranking (RF04); pizza de receita por categoria e barras de
pedidos por horário (RF02).

## 8. Evidências de testes

- Resultado da execução automatizada (`mvn test`) e lista dos casos: [evidencias-testes.md](evidencias-testes.md).
- Os testes cobrem: cálculo dos indicadores, série temporal, filtros, ranking, validações de parâmetros, formato JSON dos
  endpoints, erros 400/401/403, restrição de perfis e renderização da tela.
- Evidências visuais da tela funcionando (capturas) devem ser adicionadas em `docs/evidencias/`.

## 9. Fluxo de branches (gitflow)

- `main`: versão estável.
- `develop`: integração.
- `feature/dashboard-api`: implementação da API e do dashboard, integrada ao `develop` por merge e depois ao `main`.
