package com.umc.pediupatrao.entity;

public enum TipoOperacao {
    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    MUDANCA_STATUS("Mudança de status"),
    DESCONTO_PEDIDO("Desconto em pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),
    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),
    ALTERACAO_PERFIL_USUARIO("Alteração de perfil de usuário");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
