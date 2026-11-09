package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.CategoriaVenda;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.dto.PontoVenda;
import com.umc.pediupatrao.dto.ProdutoRanking;
import com.umc.pediupatrao.dto.StatusContagem;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Consultas agregadas para o dashboard. A receita considera o valor final dos
 * pedidos (ja com desconto) e ignora os cancelados.
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final String SEM_TIPO = "Não informado";

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;

    public DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
    }

    public DashboardResumo resumo(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscar(filtro);
        List<Pedido> validos = pedidos.stream().filter(p -> !cancelado(p)).toList();

        BigDecimal receita = soma(validos.stream().map(p -> valor(p.getValorTotal())));
        BigDecimal descontos = soma(validos.stream().map(p -> valor(p.getValorDesconto())));
        long cancelados = pedidos.size() - validos.size();
        long itens = validos.stream().flatMap(p -> p.getItens().stream()).mapToLong(ItemPedido::getQuantidade).sum();
        Set<String> clientes = new HashSet<>();
        validos.forEach(p -> {
            if (p.getClienteId() != null) {
                clientes.add(p.getClienteId());
            }
        });

        return new DashboardResumo(
                filtro.inicio(), filtro.fim(),
                receita,
                pedidos.size(),
                validos.size(),
                cancelados,
                percentual(cancelados, pedidos.size()),
                validos.isEmpty() ? BigDecimal.ZERO.setScale(2) : receita.divide(BigDecimal.valueOf(validos.size()), 2, RoundingMode.HALF_UP),
                descontos,
                itens,
                clientes.size());
    }

    /** Serie temporal de vendas. agrupamento: "dia" (padrao) ou "mes". */
    public List<PontoVenda> vendasPorPeriodo(FiltroDashboard filtro, String agrupamento) {
        boolean porMes = "mes".equalsIgnoreCase(agrupamento);
        if (agrupamento != null && !agrupamento.isBlank() && !porMes && !"dia".equalsIgnoreCase(agrupamento)) {
            throw new ParametroInvalidoException("Agrupamento inválido: use 'dia' ou 'mes'.");
        }

        Map<String, BigDecimal> receita = new TreeMap<>();
        Map<String, Long> quantidade = new HashMap<>();

        // Todos os periodos aparecem na serie, mesmo sem vendas, para o grafico nao ter buracos
        if (porMes) {
            for (YearMonth m = YearMonth.from(filtro.inicio()); !m.isAfter(YearMonth.from(filtro.fim())); m = m.plusMonths(1)) {
                receita.put(m.toString(), BigDecimal.ZERO.setScale(2));
            }
        } else {
            for (LocalDate d = filtro.inicio(); !d.isAfter(filtro.fim()); d = d.plusDays(1)) {
                receita.put(d.toString(), BigDecimal.ZERO.setScale(2));
            }
        }

        for (Pedido p : buscar(filtro)) {
            if (cancelado(p) || p.getDataEntrada() == null) {
                continue;
            }
            String chave = porMes ? YearMonth.from(p.getDataEntrada()).toString() : p.getDataEntrada().toLocalDate().toString();
            receita.merge(chave, valor(p.getValorTotal()), BigDecimal::add);
            quantidade.merge(chave, 1L, Long::sum);
        }

        List<PontoVenda> serie = new ArrayList<>();
        receita.forEach((rotulo, total) -> serie.add(new PontoVenda(rotulo, total, quantidade.getOrDefault(rotulo, 0L))));
        return serie;
    }

    /** Horario de entrada dos pedidos: mostra os picos de movimento ao longo do dia. */
    public List<PontoVenda> vendasPorHora(FiltroDashboard filtro) {
        BigDecimal[] receita = new BigDecimal[24];
        long[] quantidade = new long[24];
        for (int h = 0; h < 24; h++) {
            receita[h] = BigDecimal.ZERO.setScale(2);
        }
        for (Pedido p : buscar(filtro)) {
            if (cancelado(p) || p.getDataEntrada() == null) {
                continue;
            }
            int hora = p.getDataEntrada().getHour();
            receita[hora] = receita[hora].add(valor(p.getValorTotal()));
            quantidade[hora]++;
        }
        List<PontoVenda> serie = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            serie.add(new PontoVenda(String.format("%02dh", h), receita[h], quantidade[h]));
        }
        return serie;
    }

    public List<StatusContagem> pedidosPorStatus(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscar(filtro);
        Map<String, Long> contagem = new LinkedHashMap<>();
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s.name(), 0L);
        }
        for (Pedido p : pedidos) {
            contagem.merge(p.getStatus() == null ? "DESCONHECIDO" : p.getStatus(), 1L, Long::sum);
        }

        List<StatusContagem> resultado = new ArrayList<>();
        contagem.forEach((status, qtd) -> resultado.add(new StatusContagem(
                status,
                StatusPedido.de(status).map(StatusPedido::getDescricao).orElse(status),
                qtd,
                percentual(qtd, pedidos.size()))));
        return resultado;
    }

    public List<ProdutoRanking> produtosMaisVendidos(FiltroDashboard filtro, int limite) {
        if (limite < 1 || limite > 50) {
            throw new ParametroInvalidoException("O limite deve estar entre 1 e 50.");
        }
        Map<String, String> tipos = tiposPorProduto();
        Map<String, ProdutoRanking> ranking = new HashMap<>();

        for (Pedido p : buscar(filtro)) {
            if (cancelado(p)) {
                continue;
            }
            for (ItemPedido item : p.getItens()) {
                String tipo = tipoDoItem(item, tipos);
                if (!atendeTipo(filtro, tipo)) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId() : String.valueOf(item.getNomeProduto());
                ProdutoRanking atual = ranking.get(chave);
                long qtd = item.getQuantidade() + (atual == null ? 0 : atual.quantidade());
                BigDecimal rec = valor(item.getSubtotal()).add(atual == null ? BigDecimal.ZERO : atual.receita());
                ranking.put(chave, new ProdutoRanking(item.getProdutoId(), item.getNomeProduto(), tipo, qtd, rec));
            }
        }
        return ranking.values().stream()
                .sorted(Comparator.comparingLong(ProdutoRanking::quantidade).reversed()
                        .thenComparing(Comparator.comparing(ProdutoRanking::receita).reversed()))
                .limit(limite)
                .toList();
    }

    public List<CategoriaVenda> vendasPorCategoria(FiltroDashboard filtro) {
        Map<String, String> tipos = tiposPorProduto();
        Map<String, CategoriaVenda> categorias = new HashMap<>();

        for (Pedido p : buscar(filtro)) {
            if (cancelado(p)) {
                continue;
            }
            for (ItemPedido item : p.getItens()) {
                String tipo = tipoDoItem(item, tipos);
                if (!atendeTipo(filtro, tipo)) {
                    continue;
                }
                CategoriaVenda atual = categorias.get(tipo);
                categorias.put(tipo, new CategoriaVenda(tipo,
                        item.getQuantidade() + (atual == null ? 0 : atual.quantidade()),
                        valor(item.getSubtotal()).add(atual == null ? BigDecimal.ZERO : atual.receita())));
            }
        }
        return categorias.values().stream()
                .sorted(Comparator.comparing(CategoriaVenda::receita).reversed())
                .toList();
    }

    /** Tipos de produto cadastrados, para alimentar o filtro da tela. */
    public List<String> tiposDeProduto() {
        return produtoRepository.findAll().stream()
                .map(Produto::getTipo)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    // ========================
    private List<Pedido> buscar(FiltroDashboard filtro) {
        LocalDateTime de = filtro.inicio().atStartOfDay();
        LocalDateTime ate = filtro.fim().plusDays(1).atStartOfDay();
        return pedidoRepository.buscarPorPeriodo(de, ate).stream()
                .filter(p -> filtro.status() == null || filtro.status().name().equals(p.getStatus()))
                .toList();
    }

    private boolean cancelado(Pedido p) {
        return StatusPedido.CANCELADO.name().equals(p.getStatus());
    }

    private boolean atendeTipo(FiltroDashboard filtro, String tipo) {
        return filtro.tipo() == null || filtro.tipo().equalsIgnoreCase(tipo);
    }

    // Itens antigos nao guardam o tipo: nesse caso busca no cadastro de produtos
    private String tipoDoItem(ItemPedido item, Map<String, String> tipos) {
        if (item.getTipoProduto() != null && !item.getTipoProduto().isBlank()) {
            return item.getTipoProduto();
        }
        String doCadastro = item.getProdutoId() == null ? null : tipos.get(item.getProdutoId());
        return doCadastro == null || doCadastro.isBlank() ? SEM_TIPO : doCadastro;
    }

    private Map<String, String> tiposPorProduto() {
        Map<String, String> tipos = new HashMap<>();
        for (Produto p : produtoRepository.findAll()) {
            if (p.getId() != null && p.getTipo() != null) {
                tipos.put(p.getId(), p.getTipo());
            }
        }
        return tipos;
    }

    private BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal soma(java.util.stream.Stream<BigDecimal> valores) {
        return valores.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentual(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(parte).multiply(CEM).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
