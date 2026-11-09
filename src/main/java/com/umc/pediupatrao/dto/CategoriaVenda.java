package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record CategoriaVenda(String tipo, long quantidade, BigDecimal receita) {
}
