package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.exception.ApiExceptionHandler;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {PedidoController.class, AuditoriaController.class,
    UsuarioController.class, ClienteController.class})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class AutorizacaoTest {
    @Autowired MockMvc mvc;

    @MockBean UsuarioDetailsService usuarioDetailsService;
    @MockBean PedidoService pedidoService;
    @MockBean AuditoriaService auditoriaService;
    @MockBean UsuarioService usuarioService;
    @MockBean ClienteService clienteService;

    private static final String DESCONTO = "{\"percentual\":10}";
    private static final String CANCELA = "{\"justificativa\":\"teste\"}";

    @Test
    void semLoginAApiResponde401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    void semTokenCsrfOPostEhRecusado() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(user("g").roles("GERENTE"))
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteAplicaDescontoECancela() throws Exception {
        when(pedidoService.aplicarDesconto(eq("1"), any())).thenReturn(new Pedido());
        when(pedidoService.cancelar(eq("1"), any())).thenReturn(new Pedido());

        mvc.perform(post("/api/pedidos/1/desconto").with(user("g").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pedidos/1/cancelamento").with(user("g").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELA))
                .andExpect(status().isOk());
    }

    @Test
    void adminNaoAplicaDescontoNemCancela() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(user("a").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").exists());
        mvc.perform(post("/api/pedidos/1/cancelamento").with(user("a").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELA))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteNaoAplicaDescontoNemCancelaNemMudaStatus() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(user("t").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/1/cancelamento").with(user("t").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELA))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1/status").param("status", "PRONTO")
                .with(user("t").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteRegistraEConsultaPedidos() throws Exception {
        when(pedidoService.listarPedidos()).thenReturn(List.of());
        when(pedidoService.criarPedido(any())).thenReturn(new Pedido());

        mvc.perform(get("/api/pedidos").with(user("t").roles("ATENDENTE"))).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(user("t").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":\"c\",\"itens\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminSoConsultaPedidos() throws Exception {
        when(pedidoService.listarPedidos()).thenReturn(List.of());
        mvc.perform(get("/api/pedidos").with(user("a").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(user("a").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void regraDeNegocioViraRespostaComMensagem() throws Exception {
        when(pedidoService.aplicarDesconto(eq("1"), any())).thenThrow(new RegraNegocioException("limite excedido"));
        mvc.perform(post("/api/pedidos/1/desconto").with(user("g").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":50}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("limite excedido"));
    }

    @Test
    void auditoriaSoParaAdminEGerente() throws Exception {
        when(auditoriaService.consultar(any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/auditoria").with(user("a").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("g").roles("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("t").roles("ATENDENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void auditoriaNaoAceitaEscritaNemExclusao() throws Exception {
        mvc.perform(post("/api/auditoria").with(user("a").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/auditoria/1").with(user("g").roles("GERENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/auditoria/1").with(user("g").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuariosSoParaAdmin() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(List.of());
        mvc.perform(get("/api/usuarios").with(user("a").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").with(user("g").roles("GERENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(user("t").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"12345678\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gerenteNaoCriaUsuarioNemSeAutoPromove() throws Exception {
        mvc.perform(put("/api/usuarios/1").with(user("g").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"g\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientesRespeitamPerfis() throws Exception {
        when(clienteService.listarClientes()).thenReturn(List.of());
        mvc.perform(get("/api/clientes").with(user("g").roles("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").with(user("t").roles("ATENDENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").with(user("a").roles("ADMIN"))).andExpect(status().isForbidden());

        mvc.perform(put("/api/clientes/1").with(user("g").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
