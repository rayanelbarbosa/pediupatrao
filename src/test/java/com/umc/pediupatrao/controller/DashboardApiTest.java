package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.PontoVenda;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.exception.ApiExceptionHandler;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardApiController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class DashboardApiTest {

    @Autowired MockMvc mvc;

    @MockBean UsuarioDetailsService usuarioDetailsService;
    @MockBean DashboardService dashboardService;

    @Test
    void gerenteConsultaOResumoComOFormatoEsperado() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(new DashboardResumo(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                new BigDecimal("1500.00"), 30, 28, 2, new BigDecimal("6.7"),
                new BigDecimal("53.57"), new BigDecimal("40.00"), 70, 12));

        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-01").param("fim", "2026-11-10")
                .with(user("g").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaTotal").value(1500.00))
                .andExpect(jsonPath("$.pedidosValidos").value(28))
                .andExpect(jsonPath("$.ticketMedio").value(53.57))
                .andExpect(jsonPath("$.clientesAtendidos").value(12));
    }

    @Test
    void adminTambemConsulta() throws Exception {
        when(dashboardService.vendasPorPeriodo(any(), any())).thenReturn(
                List.of(new PontoVenda("2026-11-10", new BigDecimal("10.00"), 1)));
        mvc.perform(get("/api/dashboard/vendas-por-periodo").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rotulo").value("2026-11-10"));
    }

    @Test
    void rankingDevolveListaDeProdutos() throws Exception {
        when(dashboardService.produtosMaisVendidos(any(), anyInt())).thenReturn(
                List.of(new ProdutoRanking("p1", "Calabresa", "Pizza", 12, new BigDecimal("576.00"))));
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "3").with(user("g").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Calabresa"))
                .andExpect(jsonPath("$[0].quantidade").value(12));
    }

    @Test
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").with(user("t").roles("ATENDENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void semLoginRetorna401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiDoDashboardEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(user("g").roles("GERENTE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void parametroInvalidoRetorna400ComMensagem() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "10/11/2026").with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Data inválida em 'inicio': use o formato AAAA-MM-DD."));
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-10").param("fim", "2026-11-01")
                .with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/vendas-por-periodo").param("status", "ENTREGUE").with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limiteNaoNumericoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "abc").with(user("g").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }
}
