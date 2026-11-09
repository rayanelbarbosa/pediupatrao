package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CategoriaVenda;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.dto.PontoVenda;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.dto.StatusContagem;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public DashboardResumo resumo(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status) {
        return dashboardService.resumo(filtro(inicio, fim, status, null));
    }

    @GetMapping("/vendas-por-periodo")
    public List<PontoVenda> vendasPorPeriodo(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "dia") String agrupamento) {
        return dashboardService.vendasPorPeriodo(filtro(inicio, fim, status, null), agrupamento);
    }

    @GetMapping("/vendas-por-hora")
    public List<PontoVenda> vendasPorHora(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status) {
        return dashboardService.vendasPorHora(filtro(inicio, fim, status, null));
    }

    @GetMapping("/pedidos-por-status")
    public List<StatusContagem> pedidosPorStatus(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        return dashboardService.pedidosPorStatus(filtro(inicio, fim, null, null));
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoRanking> produtosMaisVendidos(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false, defaultValue = "5") int limite) {
        return dashboardService.produtosMaisVendidos(filtro(inicio, fim, status, tipo), limite);
    }

    @GetMapping("/vendas-por-categoria")
    public List<CategoriaVenda> vendasPorCategoria(@RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.vendasPorCategoria(filtro(inicio, fim, status, tipo));
    }

    @GetMapping("/tipos-de-produto")
    public List<String> tiposDeProduto() {
        return dashboardService.tiposDeProduto();
    }

    private FiltroDashboard filtro(String inicio, String fim, String status, String tipo) {
        return FiltroDashboard.de(inicio, fim, status, tipo, LocalDate.now(AuditoriaService.FUSO));
    }
}
