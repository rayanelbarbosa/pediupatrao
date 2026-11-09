package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record ProdutoRanking(String produtoId, String nome, String tipo, long quantidade, BigDecimal receita) {
}
