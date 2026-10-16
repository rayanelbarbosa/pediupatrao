# PediuPatrão

Sistema de pedidos para uma pizzaria, feito com Java 21, Spring Boot, Spring Security, Thymeleaf e MongoDB.
Esta versão acrescenta ao projeto de exemplo três perfis de acesso, regras para o fluxo dos pedidos e uma trilha de auditoria.

## Executando

Precisa de JDK 21, Maven e um MongoDB (local ou Atlas).

Por padrão a aplicação usa `mongodb://localhost:27017/pizzaria`. Para usar outro banco, defina a variável de ambiente antes de subir:

```bash
export SPRING_DATA_MONGODB_URI='mongodb+srv://usuario:senha@seu-cluster.mongodb.net/pizzaria'
mvn spring-boot:run
```

A aplicação abre em http://localhost:8080. Na primeira execução ela cria três usuários, caso não existam:

| usuário   | senha        | perfil    |
|-----------|--------------|-----------|
| admin     | teste123     | ADMIN     |
| gerente   | gerente123   | GERENTE   |
| atendente | atendente123 | ATENDENTE |

Os testes rodam com `mvn test` e não precisam de banco.

## Permissões

| operação                                | ADMIN | GERENTE | ATENDENTE |
|-----------------------------------------|:-----:|:-------:|:---------:|
| administrar usuários e perfis           | sim   |         |           |
| consultar pedidos                       | sim   | sim     | sim       |
| registrar pedido, alterar itens         |       | sim     | sim       |
| avançar status do pedido                |       | sim     |           |
| aplicar desconto, cancelar pedido       |       | sim     |           |
| consultar clientes                      |       | sim     | sim       |
| cadastrar e atualizar clientes          |       |         | sim       |
| excluir cliente                         |       | sim     |           |
| consultar auditoria                     | sim   | sim     |           |
| cadastrar e alterar produtos            |       | sim     |           |

A restrição é feita no backend: o `SecurityConfig` limita cada rota por método HTTP e os serviços usam `@PreAuthorize`.
Sem login, a API responde 401; sem permissão, 403 com uma mensagem em JSON. Os acessos negados vão para o log.

Sobre usuários:

- `/api/usuarios`, que era pública, agora exige ADMIN.
- Só os perfis ADMIN, GERENTE e ATENDENTE são aceitos, e o `id` enviado no cadastro é ignorado.
- Ninguém altera o próprio perfil nem exclui o próprio usuário, e o último ADMIN não pode ser removido ou rebaixado.
- A senha tem no mínimo 8 caracteres, é gravada com BCrypt e nunca aparece nas respostas.
- Usuários antigos com perfil `USER` ficam desabilitados até um ADMIN definir um perfil novo.

O CSRF foi religado. Os formulários enviam o token sozinhos e as chamadas `fetch` usam o token das meta tags do layout.
O logout passou a ser por POST.

## Pedidos

Cada pedido guarda cliente, itens (produto, quantidade, preço unitário, subtotal), subtotal, desconto, total, status,
entrada (data e responsável), saída (data e responsável) e dados de cancelamento.

O cadastro recebe apenas o cliente e a lista de `{produtoId, quantidade}`. Os preços vêm do cadastro de produtos e o total
é calculado no servidor.

Fluxo de status:

```
RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA ou RETIRADO -> FINALIZADO
```

Transições fora desse fluxo retornam 422. Ao sair para entrega ou retirada, a data/hora e o usuário são gravados.

- Cancelamento: só o gerente, com justificativa, antes da saída. Os dados do pedido são mantidos e ele não pode mais ser alterado.
- Desconto: só o gerente, acima de 0 e até 20%, antes da saída. O total é recalculado no servidor.

## Auditoria

Os registros ficam na coleção `auditoria` (entidade, repositório e serviço próprios) e trazem usuário, perfil, data/hora,
operação, entidade e id, campos alterados com valor anterior e novo, e justificativa quando existe.

São auditados: inclusão e alteração de pedido, mudança de status, desconto, cancelamento, inclusão, alteração e exclusão de
cliente, e mudança de perfil de usuário.

A consulta é feita em `/auditoria` ou em `GET /api/auditoria` (filtros `entidade`, `entidadeId`, `usuario`, `limite`) e só
ADMIN e GERENTE acessam. Não há rota de escrita, e o repositório só tem `save`. Senhas nunca entram nos registros e os
telefones aparecem mascarados.

## Testes

- `PedidoServiceTest`: totais, transições, cancelamento e limite de desconto.
- `ClienteServiceTest`: auditoria de endereço e telefone.
- `UsuarioServiceTest`: perfis inválidos, autoelevação e último administrador.
- `AutorizacaoTest`: respostas 401/403 por perfil, CSRF e auditoria somente leitura.
- `TelasTest`: renderização das telas e botões por perfil.

## Roteiro da demonstração

1. Entrar como atendente, alterar o endereço de um cliente e registrar um pedido.
2. Tentar desconto ou cancelamento com o atendente e mostrar o 403.
3. Entrar como gerente, avançar o pedido até a saída, aplicar 10% de desconto, tentar 25% e cancelar outro pedido com justificativa.
4. Entrar como admin, mostrar o 403 no desconto, criar um usuário e tentar mudar o próprio perfil.
5. Abrir `/auditoria` como gerente ou admin e mostrar os registros; mostrar o 403 para o atendente.
