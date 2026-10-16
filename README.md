# PediuPatrão

Sistema web de gestão de pedidos para uma pizzaria: cadastro de clientes e produtos, registro e acompanhamento de pedidos,
controle de acesso por perfil e trilha de auditoria das alterações.

Atividade Prática Individual M1 de Engenharia de Software, evolução do projeto de exemplo PediuPatrão.

**Tecnologias:** Java 21, Spring Boot 3.4, Spring Security, Spring Data MongoDB, Thymeleaf, Maven.

## Como executar

Pré-requisitos: JDK 21, Maven e um banco MongoDB (local ou MongoDB Atlas).

1. Clone o repositório.
2. Configure a conexão com o banco. Por padrão a aplicação usa `mongodb://localhost:27017/pizzaria`.
   Para usar outro banco (por exemplo, um cluster do Atlas), defina a variável de ambiente `SPRING_DATA_MONGODB_URI`:

   ```bash
   export SPRING_DATA_MONGODB_URI='mongodb+srv://usuario:senha@seu-cluster.mongodb.net/pizzaria'
   ```

3. Suba a aplicação:

   ```bash
   mvn spring-boot:run
   ```

4. Acesse http://localhost:8080.

Na primeira execução são criados três usuários, caso ainda não existam:

| usuário   | senha        | perfil    |
|-----------|--------------|-----------|
| admin     | teste123     | ADMIN     |
| gerente   | gerente123   | GERENTE   |
| atendente | atendente123 | ATENDENTE |

Os testes automatizados rodam com `mvn test` e não dependem de banco de dados.

## Requisitos implementados

### RF01 - Controle de usuários e permissões

Três perfis de acesso, atribuídos pelo ADMIN:

| operação                                  | ADMIN | GERENTE | ATENDENTE |
|-------------------------------------------|:-----:|:-------:|:---------:|
| administrar usuários e perfis             | sim   |         |           |
| consultar pedidos                         | sim   | sim     | sim       |
| registrar pedido e alterar itens          |       | sim     | sim       |
| avançar o status do pedido                |       | sim     |           |
| aplicar desconto e cancelar pedido        |       | sim     |           |
| consultar clientes                        |       | sim     | sim       |
| cadastrar e atualizar clientes            |       |         | sim       |
| excluir cliente                           |       | sim     |           |
| consultar auditoria                       | sim   | sim     |           |
| cadastrar e alterar produtos              |       | sim     |           |

- A autorização é aplicada no backend: o `SecurityConfig` restringe cada rota por método HTTP e os serviços usam `@PreAuthorize`.
  Ocultar botões nas telas é apenas conveniência.
- Sem login a API responde `401`; sem permissão, `403` com mensagem em JSON. Os acessos negados são registrados no log
  (usuário, método e caminho, sem corpo da requisição).
- Proteção contra autoelevação: a rota `/api/usuarios` (antes pública) exige ADMIN; o perfil é validado contra os três valores
  permitidos; ninguém altera o próprio perfil nem exclui o próprio usuário; o último ADMIN não pode ser removido ou rebaixado.
- Senhas com no mínimo 8 caracteres, gravadas com BCrypt e nunca devolvidas pela API.
- Usuários antigos com perfil `USER` ficam desabilitados até que um ADMIN atribua um perfil válido.
- CSRF habilitado: formulários Thymeleaf enviam o token automaticamente e as chamadas `fetch` usam o token das meta tags
  do layout. O logout passou a ser feito por `POST`.

### RF02 - Cadastro de pedidos

O pedido registra cliente, itens (produto, quantidade, preço unitário e subtotal), subtotal, percentual e valor do desconto,
valor total, responsável pelo registro e data/hora de entrada.

O cadastro (`POST /api/pedidos`) recebe somente o cliente e a lista de `{produtoId, quantidade}`. Preços vêm do cadastro de
produtos e o total é calculado no servidor com `BigDecimal`; não é possível definir valores pela requisição. São validados
cliente existente, produto ativo, quantidade entre 1 e 99 e ao menos um item.

### RF03 - Entrada, saída e transições

```
RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA ou RETIRADO -> FINALIZADO
```

Transições fora desse fluxo (pular etapa, voltar, finalizar um pedido em preparação) são recusadas com `422`.
Na saída para entrega ou retirada, a data/hora e o usuário responsável são gravados. Um pedido cancelado não aceita mais
nenhuma alteração.

### RF04 - Cancelamento e descontos

Operações exclusivas do GERENTE:

- `POST /api/pedidos/{id}/cancelamento`: exige justificativa, só é permitido antes da saída, preserva os dados históricos
  do pedido e impede alterações posteriores.
- `POST /api/pedidos/{id}/desconto`: percentual maior que zero e até 20%, somente antes da saída; o total é recalculado
  no servidor.

### RF05 - Auditoria

Os registros ficam na coleção `auditoria` do MongoDB, com entidade (`Auditoria`), repositório (`AuditoriaRepository`) e
serviço (`AuditoriaService`) próprios. Cada registro contém usuário e perfil, data/hora, tipo de operação, entidade e id,
campos alterados com valor anterior e novo, e justificativa quando exigida.

Operações auditadas: inclusão e alteração de pedido, mudança de status, desconto, cancelamento, inclusão, alteração e exclusão
de cliente (com destaque para endereço e telefone) e mudança de perfil de usuário.

- Consulta em `/auditoria` (tela) e `GET /api/auditoria` (filtros `entidade`, `entidadeId`, `usuario` e `limite`),
  restrita a ADMIN e GERENTE.
- Integridade: não existe rota de escrita ou exclusão (as tentativas retornam `403`) e o repositório expõe apenas `save`.
- Proteção de dados: senhas e tokens nunca entram na trilha, e os telefones são registrados mascarados (apenas os 4 últimos
  dígitos), embora a alteração seja detectada comparando o valor completo.

## Principais alterações

- **Novos:** `Perfil`, `StatusPedido`, `TipoOperacao`, `ItemPedido`, `Auditoria`, `AlteracaoCampo`, DTOs de pedido,
  `RegraNegocioException`, `RecursoNaoEncontradoException`, `ApiExceptionHandler`, `AuditoriaRepository`, `AuditoriaService`,
  `AuditoriaController` e as telas `pedidos-form.html` e `auditoria.html`.
- **Alterados:** `SecurityConfig`, `UsuarioDetailsService` (perfil real no lugar do `USER` fixo), `UsuarioService`, `Pedido`,
  `PedidoService`, `PedidoController`, `ClienteService`, `ProdutoService`, `HomeController`, `DataInitializer` e os templates
  de layout, menu, pedidos, clientes, usuários e produtos.
- A tela de produtos, que gerava erro 500 no projeto original, foi convertida para fragmento do layout e passou a enviar JSON
  com token CSRF. Os links do menu para páginas inexistentes (funcionários, entregadores e horários) foram removidos.
- Nas telas de clientes, os dados passaram a ser escapados antes de entrar no HTML.
- Pedidos antigos (campos `tipoPizza` e `sabor`) continuam legíveis, mas o novo modelo usa itens.
- A exclusão de cliente, que o enunciado não atribui a nenhum perfil, ficou restrita ao GERENTE e também é auditada.

## Testes

- `PedidoServiceTest`: cálculo de total, transições, cancelamento, limite de desconto e bloqueios após saída ou cancelamento.
- `ClienteServiceTest`: auditoria de alterações de endereço e telefone.
- `UsuarioServiceTest`: perfis inválidos, autoelevação, último administrador e senha.
- `AutorizacaoTest`: respostas 401/403 por perfil nas APIs, CSRF e auditoria somente leitura.
- `TelasTest`: renderização de todas as telas e botões exibidos por perfil.

## Estrutura

```
src/main/java/com/umc/pediupatrao
  config/       segurança, carga inicial de usuários e coleções
  controller/   telas (Thymeleaf) e APIs REST
  dto/          objetos de entrada da API
  entity/       documentos do MongoDB e enumerações
  exception/    exceções de negócio e tratamento de erros da API
  repository/   acesso ao MongoDB
  service/      regras de negócio e auditoria
src/main/resources/templates   telas
src/test/java                  testes automatizados
```
