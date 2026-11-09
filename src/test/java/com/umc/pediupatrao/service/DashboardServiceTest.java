package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.CategoriaVenda;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.dto.PontoVenda;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.dto.StatusContagem;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Mock PedidoRepository pedidoRepository;
    @Mock ProdutoRepository produtoRepository;

    DashboardService service;

    @BeforeEach
    void preparar() {
        service = new DashboardService(pedidoRepository, produtoRepository);
    }

    private FiltroDashboard filtro(String inicio, String fim, String status, String tipo) {
        return FiltroDashboard.de(inicio, fim, status, tipo, HOJE);
    }

    private ItemPedido item(String id, String nome, String tipo, int qtd, String preco) {
        ItemPedido i = new ItemPedido();
        i.setProdutoId(id);
        i.setNomeProduto(nome);
        i.setTipoProduto(tipo);
        i.setQuantidade(qtd);
        i.setPrecoUnitario(new BigDecimal(preco));
        i.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(qtd)));
        return i;
    }

    private Pedido pedido(String cliente, String status, LocalDateTime entrada, String total, String desconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setClienteId(cliente);
        p.setStatus(status);
        p.setDataEntrada(entrada);
        p.setValorTotal(new BigDecimal(total));
        p.setValorDesconto(new BigDecimal(desconto));
        p.setItens(List.of(itens));
        return p;
    }

    private void dados(Pedido... pedidos) {
        when(pedidoRepository.buscarPorPeriodo(any(), any()))
                .thenReturn(List.of(pedidos));
    }

    private List<Pedido> cenario() {
        ItemPedido pizza = item("p1", "Calabresa", "Pizza", 2, "40.00");   // 80
        ItemPedido refri = item("b1", "Refrigerante", "Bebida", 1, "10.00"); // 10
        return List.of(
                pedido("c1", "FINALIZADO", LocalDateTime.of(2026, 11, 8, 19, 10), "90.00", "0.00", pizza, refri),
                pedido("c2", "FINALIZADO", LocalDateTime.of(2026, 11, 8, 20, 30), "72.00", "8.00", item("p1", "Calabresa", "Pizza", 2, "40.00")),
                pedido("c1", "PRONTO", LocalDateTime.of(2026, 11, 9, 19, 45), "50.00", "0.00", item("p2", "Mussarela", "Pizza", 1, "50.00")),
                pedido("c3", "CANCELADO", LocalDateTime.of(2026, 11, 9, 21, 0), "200.00", "0.00", item("p2", "Mussarela", "Pizza", 4, "50.00")));
    }

    @Test
    void resumoCalculaIndicadoresIgnorandoCancelados() {
        dados(cenario().toArray(new Pedido[0]));

        DashboardResumo r = service.resumo(filtro("2026-11-01", "2026-11-10", null, null));

        assertEquals(new BigDecimal("212.00"), r.receitaTotal());           // 90 + 72 + 50
        assertEquals(4, r.totalPedidos());
        assertEquals(3, r.pedidosValidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(new BigDecimal("25.0"), r.taxaCancelamento());
        assertEquals(new BigDecimal("70.67"), r.ticketMedio());             // 212 / 3
        assertEquals(new BigDecimal("8.00"), r.descontosConcedidos());
        assertEquals(6, r.itensVendidos());                                   // 2+1 + 2 + 1
        assertEquals(2, r.clientesAtendidos());                               // c1 e c2 (c3 so tem pedido cancelado)
    }

    @Test
    void resumoSemPedidosDevolveZeros() {
        dados();
        DashboardResumo r = service.resumo(filtro(null, null, null, null));
        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.receitaTotal());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(new BigDecimal("0.0"), r.taxaCancelamento());
    }

    @Test
    void filtroDeStatusRestringeOsPedidos() {
        dados(cenario().toArray(new Pedido[0]));
        DashboardResumo r = service.resumo(filtro("2026-11-01", "2026-11-10", "finalizado", null));
        assertEquals(2, r.totalPedidos());
        assertEquals(new BigDecimal("162.00"), r.receitaTotal());
    }

    @Test
    void periodoPadraoSaoOsUltimos30Dias() {
        FiltroDashboard f = filtro(null, null, null, null);
        assertEquals(HOJE, f.fim());
        assertEquals(HOJE.minusDays(29), f.inicio());
    }

    @Test
    void vendasPorDiaPreencheDiasSemVendas() {
        dados(cenario().toArray(new Pedido[0]));

        List<PontoVenda> serie = service.vendasPorPeriodo(filtro("2026-11-07", "2026-11-10", null, null), "dia");

        assertEquals(4, serie.size());
        assertEquals("2026-11-07", serie.get(0).rotulo());
        assertEquals(new BigDecimal("0.00"), serie.get(0).receita());
        assertEquals(new BigDecimal("162.00"), serie.get(1).receita());     // dia 08
        assertEquals(2, serie.get(1).pedidos());
        assertEquals(new BigDecimal("50.00"), serie.get(2).receita());      // dia 09 sem o cancelado
        assertEquals(1, serie.get(2).pedidos());
    }

    @Test
    void vendasPorMesAgrupaNoMes() {
        dados(cenario().toArray(new Pedido[0]));
        List<PontoVenda> serie = service.vendasPorPeriodo(filtro("2026-10-01", "2026-11-10", null, null), "mes");
        assertEquals(2, serie.size());
        assertEquals("2026-11", serie.get(1).rotulo());
        assertEquals(new BigDecimal("212.00"), serie.get(1).receita());
    }

    @Test
    void agrupamentoInvalidoEhRecusado() {
        assertThrows(ParametroInvalidoException.class,
                () -> service.vendasPorPeriodo(filtro(null, null, null, null), "semana"));
    }

    @Test
    void vendasPorHoraMostraOsPicos() {
        dados(cenario().toArray(new Pedido[0]));
        List<PontoVenda> horas = service.vendasPorHora(filtro("2026-11-01", "2026-11-10", null, null));
        assertEquals(24, horas.size());
        assertEquals(2, horas.get(19).pedidos());   // 19h10 e 19h45
        assertEquals(1, horas.get(20).pedidos());
        assertEquals(0, horas.get(21).pedidos());   // o das 21h foi cancelado
    }

    @Test
    void pedidosPorStatusTrazTodosOsStatusComPercentual() {
        dados(cenario().toArray(new Pedido[0]));
        List<StatusContagem> status = service.pedidosPorStatus(filtro("2026-11-01", "2026-11-10", null, null));

        assertEquals(7, status.size());
        StatusContagem finalizado = status.stream().filter(s -> s.status().equals("FINALIZADO")).findFirst().orElseThrow();
        assertEquals(2, finalizado.quantidade());
        assertEquals(new BigDecimal("50.0"), finalizado.percentual());
        StatusContagem recebido = status.stream().filter(s -> s.status().equals("RECEBIDO")).findFirst().orElseThrow();
        assertEquals(0, recebido.quantidade());
    }

    @Test
    void rankingOrdenaPorQuantidadeEIgnoraCancelados() {
        dados(cenario().toArray(new Pedido[0]));
        List<ProdutoRanking> ranking = service.produtosMaisVendidos(filtro("2026-11-01", "2026-11-10", null, null), 5);

        assertEquals("Calabresa", ranking.get(0).nome());
        assertEquals(4, ranking.get(0).quantidade());
        assertEquals(new BigDecimal("160.00"), ranking.get(0).receita());
        // Mussarela: so o pedido nao cancelado (1 unidade)
        ProdutoRanking mussarela = ranking.stream().filter(p -> p.nome().equals("Mussarela")).findFirst().orElseThrow();
        assertEquals(1, mussarela.quantidade());
    }

    @Test
    void rankingRespeitaLimiteETipo() {
        dados(cenario().toArray(new Pedido[0]));
        assertEquals(1, service.produtosMaisVendidos(filtro("2026-11-01", "2026-11-10", null, null), 1).size());

        List<ProdutoRanking> bebidas = service.produtosMaisVendidos(filtro("2026-11-01", "2026-11-10", null, "bebida"), 5);
        assertEquals(1, bebidas.size());
        assertEquals("Refrigerante", bebidas.get(0).nome());
    }

    @Test
    void limiteForaDoIntervaloEhRecusado() {
        assertThrows(ParametroInvalidoException.class,
                () -> service.produtosMaisVendidos(filtro(null, null, null, null), 0));
        assertThrows(ParametroInvalidoException.class,
                () -> service.produtosMaisVendidos(filtro(null, null, null, null), 51));
    }

    @Test
    void itensAntigosSemTipoUsamOCadastroDeProdutos() {
        Produto cadastro = new Produto();
        cadastro.setId("p9");
        cadastro.setNome("Esfiha");
        cadastro.setTipo("Esfiha");
        when(produtoRepository.findAll()).thenReturn(List.of(cadastro));
        dados(pedido("c1", "FINALIZADO", LocalDateTime.of(2026, 11, 9, 12, 0), "12.00", "0.00",
                item("p9", "Esfiha", null, 2, "6.00")));

        List<CategoriaVenda> categorias = service.vendasPorCategoria(filtro("2026-11-01", "2026-11-10", null, null));

        assertEquals("Esfiha", categorias.get(0).tipo());
        assertEquals(new BigDecimal("12.00"), categorias.get(0).receita());
    }

    @Test
    void categoriasSomamReceitaPorTipo() {
        dados(cenario().toArray(new Pedido[0]));
        List<CategoriaVenda> categorias = service.vendasPorCategoria(filtro("2026-11-01", "2026-11-10", null, null));
        assertEquals("Pizza", categorias.get(0).tipo());
        assertEquals(new BigDecimal("210.00"), categorias.get(0).receita());   // 80 + 80 + 50
        assertTrue(categorias.stream().anyMatch(c -> c.tipo().equals("Bebida")));
    }

    @Test
    void pedidoAntigoSemValoresNaoQuebraOsCalculos() {
        Pedido legado = new Pedido();
        legado.setStatus("FINALIZADO");
        legado.setDataEntrada(LocalDateTime.of(2026, 11, 9, 12, 0));
        dados(legado);
        DashboardResumo r = service.resumo(filtro("2026-11-01", "2026-11-10", null, null));
        assertEquals(1, r.pedidosValidos());
        assertEquals(new BigDecimal("0.00"), r.receitaTotal());
    }
}
