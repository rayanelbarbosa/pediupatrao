package com.umc.pediupatrao.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {HomeController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class TelasTest {
    @Autowired MockMvc mvc;

    @MockBean UsuarioDetailsService usuarioDetailsService;
    @MockBean PedidoService pedidoService;
    @MockBean ClienteService clienteService;
    @MockBean ProdutoService produtoService;
    @MockBean UsuarioService usuarioService;
    @MockBean AuditoriaService auditoriaService;

    @BeforeEach
    void dados() {
        ItemPedido item = new ItemPedido();
        item.setNomeProduto("Calabresa");
        item.setQuantidade(2);

        Pedido pedido = new Pedido();
        pedido.setId("64b0c0ffee0000000000abcd");
        pedido.setClienteId("c1");
        pedido.setItens(List.of(item));
        pedido.setValorTotal(new BigDecimal("80.00"));
        pedido.setDescontoPercentual(new BigDecimal("20"));
        pedido.setStatus("RECEBIDO");
        pedido.setDataEntrada(LocalDateTime.of(2026, 9, 23, 14, 35));
        pedido.setResponsavelRegistro("atendente");

        when(pedidoService.listarPedidos()).thenReturn(List.of(pedido));
        when(pedidoService.nomesDosClientes()).thenReturn(Map.of("c1", "Maria"));
        when(clienteService.listarClientes()).thenReturn(List.of());

        Produto produto = new Produto();
        produto.setId("pz1");
        produto.setNome("Calabresa");
        produto.setPreco(45.0);
        produto.setAtivo(true);
        when(produtoService.listarProdutos()).thenReturn(List.of(produto));

        when(auditoriaService.consultar(null, null, null, null)).thenReturn(List.of(
                new Auditoria("1", "atendente01", "ATENDENTE", LocalDateTime.of(2026, 9, 23, 14, 35),
                        TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1",
                        List.of(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")), null)));
    }

    @Test
    void gerenteVeBotoesDeGestaoDoPedido() throws Exception {
        mvc.perform(get("/pedidos").with(user("g").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Maria")))
                .andExpect(content().string(containsString("data-acao=\"cancelar\"")))
                .andExpect(content().string(containsString("data-acao=\"desconto\"")))
                .andExpect(content().string(containsString("23/09/2026 14:35")));
    }

    @Test
    void atendenteVeOsPedidosSemBotoesDeGestao() throws Exception {
        mvc.perform(get("/pedidos").with(user("t").roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Novo Pedido")))
                .andExpect(content().string(not(containsString("data-acao=\"cancelar\""))))
                .andExpect(content().string(not(containsString("data-acao=\"desconto\""))));
    }

    @Test
    void adminVeOsPedidosSemAcoes() throws Exception {
        mvc.perform(get("/pedidos").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Novo Pedido"))))
                .andExpect(content().string(not(containsString("data-acao="))));
    }

    @Test
    void formularioDeNovoPedidoAbreParaAtendenteMasNaoParaAdmin() throws Exception {
        mvc.perform(get("/pedidos/novo").with(user("t").roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registrar pedido")));
        mvc.perform(get("/pedidos/novo").with(user("a").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginaDeAuditoriaMostraValoresAnterioresENovos() throws Exception {
        mvc.perform(get("/auditoria").with(user("g").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("atendente01")))
                .andExpect(content().string(containsString("Rua das Flores")))
                .andExpect(content().string(containsString("Av. Brasil")))
                .andExpect(content().string(containsString("23/09/2026 14:35")));
        mvc.perform(get("/auditoria").with(user("t").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void menuLateralEscondeOpcoesSemPermissao() throws Exception {
        mvc.perform(get("/").with(user("t").roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/auditoria\""))))
                .andExpect(content().string(not(containsString("href=\"/usuarios\""))));
        mvc.perform(get("/").with(user("a").roles("ADMIN")))
                .andExpect(content().string(containsString("href=\"/usuarios\"")))
                .andExpect(content().string(containsString("href=\"/auditoria\"")));
    }
}
