package com.umc.pediupatrao.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FiltroDashboardTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void aceitaDatasStatusETipo() {
        FiltroDashboard f = FiltroDashboard.de("2026-11-01", "2026-11-05", "cancelado", " Pizza ", HOJE);
        assertEquals(LocalDate.of(2026, 11, 1), f.inicio());
        assertEquals(LocalDate.of(2026, 11, 5), f.fim());
        assertEquals(StatusPedido.CANCELADO, f.status());
        assertEquals("Pizza", f.tipo());
    }

    @Test
    void parametrosVaziosViramPadrao() {
        FiltroDashboard f = FiltroDashboard.de("", " ", "", "", HOJE);
        assertEquals(HOJE, f.fim());
        assertNull(f.status());
        assertNull(f.tipo());
    }

    @Test
    void dataInicialDepoisDaFinalEhRecusada() {
        assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de("2026-11-10", "2026-11-01", null, null, HOJE));
    }

    @Test
    void formatoDeDataInvalidoEhRecusado() {
        assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de("10/11/2026", null, null, null, HOJE));
        assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de("2026-13-45", null, null, null, HOJE));
    }

    @Test
    void periodoMaiorQueUmAnoEhRecusado() {
        assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de("2025-01-01", "2026-11-10", null, null, HOJE));
    }

    @Test
    void statusInvalidoEhRecusado() {
        assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de(null, null, "ENTREGUE", null, HOJE));
    }
}
