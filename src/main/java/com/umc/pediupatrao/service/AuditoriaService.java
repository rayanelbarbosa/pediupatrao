package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int LIMITE_PADRAO = 200;

    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void registrar(TipoOperacao operacao, String entidade, String entidadeId,
            List<AlteracaoCampo> alteracoes, String justificativa) {
        Auditoria registro = new Auditoria(
                new ObjectId().toHexString(),
                ContextoSeguranca.usuario(),
                ContextoSeguranca.perfil(),
                LocalDateTime.now(FUSO),
                operacao,
                entidade,
                entidadeId,
                alteracoes == null ? List.of() : List.copyOf(alteracoes),
                justificativa);
        auditoriaRepository.save(registro);
    }

    public static List<AlteracaoCampo> diferencas(Map<String, String> antes, Map<String, String> depois) {
        List<AlteracaoCampo> mudancas = new ArrayList<>();
        for (Map.Entry<String, String> e : depois.entrySet()) {
            String anterior = antes.get(e.getKey());
            String novo = e.getValue();
            if (!Objects.equals(vazioParaNulo(anterior), vazioParaNulo(novo))) {
                mudancas.add(new AlteracaoCampo(e.getKey(), anterior, novo));
            }
        }
        return mudancas;
    }

    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "*".repeat(digitos.length() - 4) + digitos.substring(digitos.length() - 4);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<Auditoria> consultar(String entidade, String entidadeId, String usuario, Integer limite) {
        Query query = new Query();
        if (temTexto(entidade)) {
            query.addCriteria(Criteria.where("entidade").is(entidade.trim().toUpperCase()));
        }
        if (temTexto(entidadeId)) {
            query.addCriteria(Criteria.where("entidadeId").is(entidadeId.trim()));
        }
        if (temTexto(usuario)) {
            query.addCriteria(Criteria.where("usuario").is(usuario.trim()));
        }
        int max = limite == null || limite < 1 ? LIMITE_PADRAO : Math.min(limite, 1000);
        query.with(Sort.by(Sort.Direction.DESC, "dataHora")).limit(max);
        return mongoTemplate.find(query, Auditoria.class);
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
