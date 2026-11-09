package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardResumo(
        LocalDate inicio,
        LocalDate fim,
        BigDecimal receitaTotal,
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        BigDecimal ticketMedio,
        BigDecimal descontosConcedidos,
        long itensVendidos,
        long clientesAtendidos) {
}
