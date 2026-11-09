package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record PontoVenda(String rotulo, BigDecimal receita, long pedidos) {
}
