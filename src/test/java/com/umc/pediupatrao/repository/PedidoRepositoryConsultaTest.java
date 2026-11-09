package com.umc.pediupatrao.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.Query;

/**
 * Sem banco nao da para executar a consulta, mas da para garantir que ela continua
 * escrita como intervalo [inicio, fim) e nao como duas condicoes derivadas no mesmo campo
 * (que o Spring Data recusa em tempo de execucao).
 */
class PedidoRepositoryConsultaTest {

    @Test
    void consultaPorPeriodoUsaIntervaloSemiAberto() throws Exception {
        Method metodo = PedidoRepository.class.getMethod("buscarPorPeriodo", LocalDateTime.class, LocalDateTime.class);
        Query query = metodo.getAnnotation(Query.class);

        assertNotNull(query);
        assertEquals("{ 'dataEntrada': { $gte: ?0, $lt: ?1 } }", query.value());
    }
}
