package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record StatusContagem(String status, String descricao, long quantidade, BigDecimal percentual) {
}
