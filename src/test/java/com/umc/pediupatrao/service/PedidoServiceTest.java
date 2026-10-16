package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock AuditoriaService auditoriaService;

    PedidoService service;

    @BeforeEach
    void preparar() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "gerente01", "x", List.of(new SimpleGrantedAuthority("ROLE_GERENTE"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Produto produto(String id, String nome, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private Pedido pedidoComStatus(StatusPedido status) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setStatus(status.name());
        p.setSubtotal(new BigDecimal("100.00"));
        p.setDescontoPercentual(BigDecimal.ZERO);
        p.setValorDesconto(new BigDecimal("0.00"));
        p.setValorTotal(new BigDecimal("100.00"));
        ItemPedido item = new ItemPedido();
        item.setNomeProduto("Pizza");
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("100.00"));
        item.setSubtotal(new BigDecimal("100.00"));
        p.setItens(List.of(item));
        lenient().when(pedidoRepository.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    private void salvarRetornaArgumento() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void criarPedidoCalculaTotalNoServidorEPreencheEntrada() {
        when(clienteRepository.existsById("c1")).thenReturn(true);
        when(produtoRepository.findById("pz")).thenReturn(Optional.of(produto("pz", "Calabresa", 45.50)));
        when(produtoRepository.findById("rf")).thenReturn(Optional.of(produto("rf", "Refrigerante", 9.00)));
        salvarRetornaArgumento();

        Pedido salvo = service.criarPedido(new PedidoRequest("c1",
                List.of(new ItemPedidoRequest("pz", 2), new ItemPedidoRequest("rf", 1))));

        assertEquals(new BigDecimal("100.00"), salvo.getValorTotal());
        assertEquals("RECEBIDO", salvo.getStatus());
        assertEquals("gerente01", salvo.getResponsavelRegistro());
        assertNotNull(salvo.getDataEntrada());
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("PEDIDO"), any(), any(), any());
    }

    @Test
    void criarPedidoSemItensEhRecusado() {
        when(clienteRepository.existsById("c1")).thenReturn(true);
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", List.of())));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void criarPedidoComQuantidadeInvalidaEhRecusado() {
        when(clienteRepository.existsById("c1")).thenReturn(true);
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", List.of(new ItemPedidoRequest("pz", 0)))));
    }

    @Test
    void criarPedidoParaClienteInexistenteEhRecusado() {
        when(clienteRepository.existsById("x")).thenReturn(false);
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("x", List.of(new ItemPedidoRequest("pz", 1)))));
    }

    @Test
    void fluxoCompletoComSaidaPorEntrega() {
        Pedido pedido = pedidoComStatus(StatusPedido.RECEBIDO);
        salvarRetornaArgumento();

        service.atualizarStatus("p1", "EM_PREPARACAO");
        service.atualizarStatus("p1", "PRONTO");
        assertNull(pedido.getDataSaida());

        service.atualizarStatus("p1", "SAIU_PARA_ENTREGA");
        assertNotNull(pedido.getDataSaida());
        assertEquals("gerente01", pedido.getResponsavelSaida());

        service.atualizarStatus("p1", "FINALIZADO");
        assertEquals("FINALIZADO", pedido.getStatus());
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        pedidoComStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "FINALIZADO"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void naoPermiteSaltarEtapas() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "PRONTO"));
    }

    @Test
    void naoPermiteVoltarStatus() {
        pedidoComStatus(StatusPedido.PRONTO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "RECEBIDO"));
    }

    @Test
    void statusCanceladoNaoPodeSerAlterado() {
        pedidoComStatus(StatusPedido.CANCELADO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "EM_PREPARACAO"));
    }

    @Test
    void cancelarPeloEndpointDeStatusEhRecusado() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "CANCELADO"));
    }

    @Test
    void statusDesconhecidoEhRecusado() {
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "ENTREGUE"));
    }

    @Test
    void cancelarExigeJustificativa() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "   "));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", null));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelarGravaDadosEAuditoriaComJustificativa() {
        Pedido pedido = pedidoComStatus(StatusPedido.EM_PREPARACAO);
        salvarRetornaArgumento();

        service.cancelar("p1", "Cliente desistiu");

        assertEquals("CANCELADO", pedido.getStatus());
        assertEquals("Cliente desistiu", pedido.getJustificativaCancelamento());
        assertEquals("gerente01", pedido.getResponsavelCancelamento());
        assertNotNull(pedido.getDataCancelamento());
        assertEquals(new BigDecimal("100.00"), pedido.getValorTotal());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlteracaoCampo>> campos = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("PEDIDO"), eq("p1"),
                campos.capture(), eq("Cliente desistiu"));
        AlteracaoCampo status = campos.getValue().stream().filter(c -> c.campo().equals("status")).findFirst().orElseThrow();
        assertEquals("EM_PREPARACAO", status.valorAnterior());
        assertEquals("CANCELADO", status.valorNovo());
    }

    @Test
    void naoCancelaDepoisDaSaida() {
        pedidoComStatus(StatusPedido.SAIU_PARA_ENTREGA);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "tarde demais"));
    }

    @Test
    void naoCancelaPedidoRetiradoOuFinalizado() {
        pedidoComStatus(StatusPedido.RETIRADO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "motivo"));
        pedidoComStatus(StatusPedido.FINALIZADO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "motivo"));
    }

    @Test
    void naoCancelaDuasVezes() {
        pedidoComStatus(StatusPedido.CANCELADO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "motivo"));
    }

    @Test
    void descontoRecalculaTotalNoServidor() {
        Pedido pedido = pedidoComStatus(StatusPedido.RECEBIDO);
        salvarRetornaArgumento();

        service.aplicarDesconto("p1", new BigDecimal("15"));

        assertEquals(new BigDecimal("15.00"), pedido.getValorDesconto());
        assertEquals(new BigDecimal("85.00"), pedido.getValorTotal());
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("PEDIDO"), eq("p1"), any(), any());
    }

    @Test
    void descontoAcimaDeVintePorCentoEhRecusado() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("20.01")));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("50")));
    }

    @Test
    void descontoDeVinteEhAceito() {
        Pedido pedido = pedidoComStatus(StatusPedido.PRONTO);
        salvarRetornaArgumento();
        service.aplicarDesconto("p1", new BigDecimal("20"));
        assertEquals(new BigDecimal("80.00"), pedido.getValorTotal());
    }

    @Test
    void descontoZeroOuNegativoEhRecusado() {
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", BigDecimal.ZERO));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("-5")));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", null));
    }

    @Test
    void descontoNaoPodeSerAplicadoDepoisDaSaida() {
        pedidoComStatus(StatusPedido.RETIRADO);
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("10")));
    }

    @Test
    void descontoNaoPodeSerAplicadoEmPedidoCancelado() {
        pedidoComStatus(StatusPedido.CANCELADO);
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("10")));
    }
}
