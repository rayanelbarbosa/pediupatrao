package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Carga opcional de dados para demonstrar o dashboard. So roda quando
 * pediupatrao.demodata=true (variavel de ambiente PEDIUPATRAO_DEMODATA=true)
 * e nao repete a carga se ela ja existir. Grava direto nos repositorios,
 * portanto nao gera registros de auditoria.
 */
@Configuration
@ConditionalOnProperty(name = "pediupatrao.demodata", havingValue = "true")
public class DemoDataInitializer {

    private static final String RESPONSAVEL = "demo";

    @Bean
    CommandLineRunner carregarDadosDemo(PedidoRepository pedidos, ProdutoRepository produtos, ClienteRepository clientes) {
        return args -> {
            if (pedidos.existsByResponsavelRegistro(RESPONSAVEL)) {
                System.out.println("Dados de demonstração já carregados.");
                return;
            }

            if (produtos.count() == 0) {
                produto(produtos, "Calabresa", "Pizza", 48.00);
                produto(produtos, "Mussarela", "Pizza", 42.00);
                produto(produtos, "Portuguesa", "Pizza", 52.00);
                produto(produtos, "Frango com catupiry", "Pizza", 54.00);
                produto(produtos, "Esfiha de carne", "Esfiha", 6.50);
                produto(produtos, "Esfiha de queijo", "Esfiha", 6.00);
                produto(produtos, "Coca-Cola 2L", "Bebida", 12.00);
                produto(produtos, "Suco de laranja", "Bebida", 8.00);
                produto(produtos, "X-Burger", "Lanche", 22.00);
            }
            if (clientes.count() == 0) {
                for (int i = 1; i <= 8; i++) {
                    Cliente c = new Cliente();
                    c.setNome("Cliente demonstração " + i);
                    c.setTelefone("1199999000" + i);
                    clientes.save(c);
                }
            }

            List<Produto> catalogo = produtos.findAll().stream().filter(p -> !Boolean.FALSE.equals(p.getAtivo())).toList();
            List<Cliente> base = clientes.findAll();
            Random sorteio = new Random(42);
            LocalDateTime agora = LocalDateTime.now(AuditoriaService.FUSO);

            for (int i = 0; i < 120; i++) {
                int diasAtras = sorteio.nextInt(30);
                int hora = horaDePedido(sorteio);
                LocalDateTime entrada = agora.minusDays(diasAtras).withHour(hora).withMinute(sorteio.nextInt(60)).withSecond(0).withNano(0);
                if (entrada.isAfter(agora)) {
                    entrada = agora.minusMinutes(5 + sorteio.nextInt(50));
                }
                pedidos.save(montarPedido(sorteio, catalogo, base, entrada, agora));
            }
            System.out.println("Dados de demonstração carregados (120 pedidos).");
        };
    }

    private void produto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        repo.save(p);
    }

    // Mais pedidos no fim da tarde e a noite
    private int horaDePedido(Random r) {
        int faixa = r.nextInt(10);
        if (faixa < 2) {
            return 11 + r.nextInt(4);
        }
        if (faixa < 4) {
            return 15 + r.nextInt(3);
        }
        return 18 + r.nextInt(5);
    }

    private Pedido montarPedido(Random r, List<Produto> catalogo, List<Cliente> clientes,
            LocalDateTime entrada, LocalDateTime agora) {
        Pedido pedido = new Pedido();
        pedido.setClienteId(clientes.get(r.nextInt(clientes.size())).getId());
        pedido.setDataEntrada(entrada);
        pedido.setResponsavelRegistro(RESPONSAVEL);

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int quantidadeDeItens = 1 + r.nextInt(3);
        for (int i = 0; i < quantidadeDeItens; i++) {
            // Pizzas (inicio do catalogo) saem mais do que os demais itens
            Produto produto = catalogo.get(r.nextInt(100) < 55 ? r.nextInt(Math.min(4, catalogo.size())) : r.nextInt(catalogo.size()));
            int qtd = produto.getTipo().equals("Esfiha") ? 2 + r.nextInt(5) : 1 + r.nextInt(2);
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setTipoProduto(produto.getTipo());
            item.setQuantidade(qtd);
            item.setPrecoUnitario(preco);
            item.setSubtotal(preco.multiply(BigDecimal.valueOf(qtd)));
            itens.add(item);
            subtotal = subtotal.add(item.getSubtotal());
        }

        BigDecimal percentual = r.nextInt(100) < 15 ? BigDecimal.valueOf(5 + r.nextInt(3) * 5) : BigDecimal.ZERO;
        BigDecimal desconto = subtotal.multiply(percentual).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        pedido.setItens(itens);
        pedido.setSubtotal(subtotal);
        pedido.setDescontoPercentual(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));

        definirStatus(r, pedido, entrada, agora);
        return pedido;
    }

    private void definirStatus(Random r, Pedido pedido, LocalDateTime entrada, LocalDateTime agora) {
        boolean recente = entrada.isAfter(agora.minusHours(3));
        int sorte = r.nextInt(100);
        StatusPedido status;
        if (recente) {
            StatusPedido[] andamento = {StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO};
            status = andamento[r.nextInt(andamento.length)];
        } else if (sorte < 10) {
            status = StatusPedido.CANCELADO;
        } else if (sorte < 75) {
            status = StatusPedido.FINALIZADO;
        } else if (sorte < 88) {
            status = StatusPedido.RETIRADO;
        } else {
            status = StatusPedido.SAIU_PARA_ENTREGA;
        }
        pedido.setStatus(status.name());

        if (status.isSaida() || status == StatusPedido.FINALIZADO) {
            pedido.setDataSaida(entrada.plusMinutes(35 + r.nextInt(30)));
            pedido.setResponsavelSaida(RESPONSAVEL);
        }
        if (status == StatusPedido.CANCELADO) {
            pedido.setDataCancelamento(entrada.plusMinutes(10 + r.nextInt(20)));
            pedido.setResponsavelCancelamento(RESPONSAVEL);
            pedido.setJustificativaCancelamento("Cliente desistiu do pedido");
        }
    }
}
