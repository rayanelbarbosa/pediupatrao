package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Filtros aceitos pelos endpoints do dashboard. Quando o periodo nao e informado,
 * usa os ultimos 30 dias (incluindo hoje).
 */
public record FiltroDashboard(LocalDate inicio, LocalDate fim, StatusPedido status, String tipo) {

    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final int PERIODO_MAXIMO_DIAS = 366;

    public static FiltroDashboard de(String inicio, String fim, String status, String tipo, LocalDate hoje) {
        LocalDate dataFim = lerData("fim", fim, hoje);
        LocalDate dataInicio = lerData("inicio", inicio, dataFim.minusDays(PERIODO_PADRAO_DIAS - 1));

        if (dataInicio.isAfter(dataFim)) {
            throw new ParametroInvalidoException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(dataInicio, dataFim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new ParametroInvalidoException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }

        StatusPedido statusFiltro = null;
        if (status != null && !status.isBlank()) {
            statusFiltro = StatusPedido.de(status)
                    .orElseThrow(() -> new ParametroInvalidoException("Status inválido: " + status));
        }
        String tipoFiltro = tipo == null || tipo.isBlank() ? null : tipo.trim();
        return new FiltroDashboard(dataInicio, dataFim, statusFiltro, tipoFiltro);
    }

    private static LocalDate lerData(String nome, String valor, LocalDate padrao) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeException e) {
            throw new ParametroInvalidoException("Data inválida em '" + nome + "': use o formato AAAA-MM-DD.");
        }
    }
}
