package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {
    @Id
    private String id;
    private String clienteId;
    private List<ItemPedido> itens = new ArrayList<>();

    private BigDecimal subtotal;
    private BigDecimal descontoPercentual;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;

    // Guardado como texto (nome do StatusPedido) para nao quebrar a leitura de pedidos antigos
    private String status;

    private LocalDateTime dataEntrada;
    private String responsavelRegistro;

    private LocalDateTime dataSaida;
    private String responsavelSaida;

    private LocalDateTime dataCancelamento;
    private String responsavelCancelamento;
    private String justificativaCancelamento;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(LocalDateTime dataEntrada) { this.dataEntrada = dataEntrada; }

    public String getResponsavelRegistro() { return responsavelRegistro; }
    public void setResponsavelRegistro(String responsavelRegistro) { this.responsavelRegistro = responsavelRegistro; }

    public LocalDateTime getDataSaida() { return dataSaida; }
    public void setDataSaida(LocalDateTime dataSaida) { this.dataSaida = dataSaida; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public LocalDateTime getDataCancelamento() { return dataCancelamento; }
    public void setDataCancelamento(LocalDateTime dataCancelamento) { this.dataCancelamento = dataCancelamento; }

    public String getResponsavelCancelamento() { return responsavelCancelamento; }
    public void setResponsavelCancelamento(String responsavelCancelamento) { this.responsavelCancelamento = responsavelCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }
}
