package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    // Intervalo [inicio, fim): escrito a mao porque o Spring Data nao aceita duas condicoes no mesmo campo
    @Query("{ 'dataEntrada': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);

    boolean existsByResponsavelRegistro(String responsavelRegistro);
}
