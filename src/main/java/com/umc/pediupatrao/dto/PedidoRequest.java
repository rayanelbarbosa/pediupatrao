package com.umc.pediupatrao.dto;

import java.util.List;

public record PedidoRequest(String clienteId, List<ItemPedidoRequest> itens) {
}
