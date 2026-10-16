package com.umc.pediupatrao.entity;

import java.util.Arrays;
import java.util.Optional;

public enum Perfil {
    ADMIN,
    GERENTE,
    ATENDENTE;

    public static Optional<Perfil> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(valor.trim()))
                .findFirst();
    }
}
