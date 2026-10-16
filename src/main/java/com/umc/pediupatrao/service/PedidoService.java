package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {
    private static final String ENTIDADE = "PEDIDO";
    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    private static final int MAX_ITENS = 50;
    private static final int MAX_QUANTIDADE = 99;
    private static final int MAX_JUSTIFICATIVA = 500;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return obter(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Map<String, String> nomesDosClientes() {
        Map<String, String> nomes = new HashMap<>();
        for (Cliente c : clienteRepository.findAll()) {
            nomes.put(c.getId(), c.getNome());
        }
        return nomes;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null || request.clienteId() == null || request.clienteId().isBlank()) {
            throw new RegraNegocioException("Informe o cliente do pedido.");
        }
        if (!clienteRepository.existsById(request.clienteId())) {
            throw new RegraNegocioException("Cliente informado não existe.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(request.clienteId());
        pedido.setItens(montarItens(request.itens()));
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        calcularValores(pedido);
        pedido.setStatus(StatusPedido.RECEBIDO.name());
        pedido.setDataEntrada(LocalDateTime.now(AuditoriaService.FUSO));
        pedido.setResponsavelRegistro(ContextoSeguranca.usuario());

        Pedido salvo = pedidoRepository.save(pedido);

        Map<String, String> depois = retrato(salvo);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, ENTIDADE, salvo.getId(),
                AuditoriaService.diferencas(Map.of(), depois), null);
        return salvo;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarItens(String id, List<ItemPedidoRequest> novosItens) {
        Pedido pedido = obter(id);
        if (statusDe(pedido) != StatusPedido.RECEBIDO) {
            throw new RegraNegocioException("Só é possível alterar os itens de pedidos com status RECEBIDO.");
        }
        Map<String, String> antes = retrato(pedido);

        pedido.setItens(montarItens(novosItens));
        calcularValores(pedido);
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String novoStatus) {
        StatusPedido destino = StatusPedido.de(novoStatus)
                .orElseThrow(() -> new RegraNegocioException("Status inválido: " + novoStatus));
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar um pedido use a operação de cancelamento, que exige justificativa.");
        }

        Pedido pedido = obter(id);
        StatusPedido atual = statusDe(pedido);
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode mais ser alterado.");
        }
        if (!atual.podeIrPara(destino)) {
            throw new RegraNegocioException("Transição inválida: " + atual + " -> " + destino + ".");
        }

        Map<String, String> antes = retrato(pedido);
        pedido.setStatus(destino.name());
        if (destino.isSaida()) {
            pedido.setDataSaida(LocalDateTime.now(AuditoriaService.FUSO));
            pedido.setResponsavelSaida(ContextoSeguranca.usuario());
        }
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null || percentual.signum() <= 0) {
            throw new RegraNegocioException("Informe um percentual de desconto maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto não pode ultrapassar " + DESCONTO_MAXIMO + "% do valor do pedido.");
        }
        if (percentual.scale() > 2) {
            throw new RegraNegocioException("O percentual aceita no máximo duas casas decimais.");
        }

        Pedido pedido = obter(id);
        exigirAntesDaSaida(pedido, "aplicar desconto");

        Map<String, String> antes = retrato(pedido);
        pedido.setDescontoPercentual(percentual);
        calcularValores(pedido);
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new RegraNegocioException("O cancelamento exige uma justificativa.");
        }
        String motivo = justificativa.trim();
        if (motivo.length() > MAX_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa deve ter no máximo " + MAX_JUSTIFICATIVA + " caracteres.");
        }

        Pedido pedido = obter(id);
        exigirAntesDaSaida(pedido, "cancelar");

        Map<String, String> antes = retrato(pedido);
        pedido.setStatus(StatusPedido.CANCELADO.name());
        pedido.setDataCancelamento(LocalDateTime.now(AuditoriaService.FUSO));
        pedido.setResponsavelCancelamento(ContextoSeguranca.usuario());
        pedido.setJustificativaCancelamento(motivo);
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), motivo);
        return salvo;
    }

    private Pedido obter(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    private StatusPedido statusDe(Pedido pedido) {
        return StatusPedido.de(pedido.getStatus())
                .orElseThrow(() -> new RegraNegocioException("Pedido com status desconhecido: " + pedido.getStatus()));
    }

    private void exigirAntesDaSaida(Pedido pedido, String operacao) {
        StatusPedido atual = statusDe(pedido);
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode mais ser alterado.");
        }
        if (!atual.antesDaSaida()) {
            throw new RegraNegocioException("Não é possível " + operacao + " depois da saída para entrega/retirada.");
        }
    }

    private List<ItemPedido> montarItens(List<ItemPedidoRequest> itensRequest) {
        if (itensRequest == null || itensRequest.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa ter ao menos um item.");
        }
        if (itensRequest.size() > MAX_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAX_ITENS + " itens.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoRequest req : itensRequest) {
            if (req == null || req.produtoId() == null || req.produtoId().isBlank()) {
                throw new RegraNegocioException("Item sem produto informado.");
            }
            if (req.quantidade() < 1 || req.quantidade() > MAX_QUANTIDADE) {
                throw new RegraNegocioException("A quantidade de cada item deve estar entre 1 e " + MAX_QUANTIDADE + ".");
            }
            Produto produto = produtoRepository.findById(req.produtoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + req.produtoId()));
            if (Boolean.FALSE.equals(produto.getAtivo()) || produto.getPreco() == null) {
                throw new RegraNegocioException("Produto indisponível: " + produto.getNome());
            }

            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(req.quantidade());
            item.setPrecoUnitario(preco);
            item.setSubtotal(preco.multiply(BigDecimal.valueOf(req.quantidade())));
            itens.add(item);
        }
        return itens;
    }

    private void calcularValores(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = pedido.getDescontoPercentual() == null ? BigDecimal.ZERO : pedido.getDescontoPercentual();
        BigDecimal desconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setDescontoPercentual(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
    }

    private Map<String, String> retrato(Pedido p) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clienteId", p.getClienteId());
        m.put("itens", p.getItens().stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto())
                .collect(Collectors.joining("; ")));
        m.put("subtotal", dinheiro(p.getSubtotal()));
        m.put("descontoPercentual", p.getDescontoPercentual() == null ? null : p.getDescontoPercentual().stripTrailingZeros().toPlainString());
        m.put("valorDesconto", dinheiro(p.getValorDesconto()));
        m.put("valorTotal", dinheiro(p.getValorTotal()));
        m.put("status", p.getStatus());
        m.put("dataSaida", p.getDataSaida() == null ? null : p.getDataSaida().toString());
        m.put("responsavelSaida", p.getResponsavelSaida());
        return m;
    }

    private String dinheiro(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
