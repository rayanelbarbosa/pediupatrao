package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "auditoria")
public record Auditoria(
        @Id String id,
        String usuario,
        String perfil,
        LocalDateTime dataHora,
        TipoOperacao operacao,
        String entidade,
        String entidadeId,
        List<AlteracaoCampo> alteracoes,
        String justificativa) {
}
