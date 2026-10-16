package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {
    private static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id,
                List.of(new AlteracaoCampo("cadastro", existente.getNome(), null)), null);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, salvo.getId(),
                    ocultarDadosPessoais(AuditoriaService.diferencas(Map.of(), retrato(salvo))), null);
            return salvo;
        }

        Cliente existente = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        Map<String, String> antes = retrato(existente);

        if (cliente.getEndereco() == null) {
            cliente.setEndereco(existente.getEndereco());
        }
        Cliente salvo = clienteRepository.save(cliente);

        List<AlteracaoCampo> mudancas = ocultarDadosPessoais(AuditoriaService.diferencas(antes, retrato(salvo)));
        if (!mudancas.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), mudancas, null);
        }
        return salvo;
    }

    private Map<String, String> retrato(Cliente c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", c.getNome());
        m.put("telefone", c.getTelefone());
        m.put("cep", c.getCep());
        m.put("logradouro", c.getLogradouro());
        m.put("numero", c.getNumero());
        m.put("complemento", c.getComplemento());
        m.put("bairro", c.getBairro());
        m.put("cidade", c.getCidade());
        m.put("estado", c.getEstado());
        m.put("endereco", c.getEndereco());
        return m;
    }

    private List<AlteracaoCampo> ocultarDadosPessoais(List<AlteracaoCampo> mudancas) {
        return mudancas.stream()
                .map(m -> "telefone".equals(m.campo())
                        ? new AlteracaoCampo(m.campo(),
                                AuditoriaService.mascararTelefone(m.valorAnterior()),
                                AuditoriaService.mascararTelefone(m.valorNovo()))
                        : m)
                .toList();
    }
}
