package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {
    @Mock ClienteRepository clienteRepository;
    @Mock AuditoriaService auditoriaService;

    ClienteService service;

    @BeforeEach
    void preparar() {
        service = new ClienteService(clienteRepository, auditoriaService);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Cliente cliente(String id, String logradouro, String telefone) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setNome("Maria");
        c.setLogradouro(logradouro);
        c.setTelefone(telefone);
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<AlteracaoCampo> capturarCampos(TipoOperacao tipo) {
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(tipo), eq("CLIENTE"), any(), captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void alteracaoDeEnderecoGeraAuditoriaComValorAnteriorENovo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua das Flores", "11988887777")));

        service.salvar(cliente("c1", "Av. Brasil", "11988887777"));

        List<AlteracaoCampo> campos = capturarCampos(TipoOperacao.ALTERACAO_CLIENTE);
        assertEquals(1, campos.size());
        assertEquals("logradouro", campos.get(0).campo());
        assertEquals("Rua das Flores", campos.get(0).valorAnterior());
        assertEquals("Av. Brasil", campos.get(0).valorNovo());
    }

    @Test
    void alteracaoDeTelefoneNaoGuardaONumeroCompleto() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A", "11988887777")));

        service.salvar(cliente("c1", "Rua A", "11955554444"));

        List<AlteracaoCampo> campos = capturarCampos(TipoOperacao.ALTERACAO_CLIENTE);
        AlteracaoCampo tel = campos.get(0);
        assertEquals("telefone", tel.campo());
        assertTrue(tel.valorAnterior().endsWith("7777"));
        assertTrue(tel.valorNovo().endsWith("4444"));
        assertFalse(tel.valorAnterior().contains("1198"));
        assertFalse(tel.valorNovo().contains("1195"));
    }

    @Test
    void mudancaSoNosUltimosDigitosDoTelefoneContinuaSendoDetectada() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A", "11988887777")));
        service.salvar(cliente("c1", "Rua A", "11988880000"));
        assertEquals(1, capturarCampos(TipoOperacao.ALTERACAO_CLIENTE).size());
    }

    @Test
    void semMudancaNaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A", "11988887777")));
        service.salvar(cliente("c1", "Rua A", "11988887777"));
        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void novoClienteGeraAuditoriaDeInclusao() {
        service.salvar(cliente(null, "Rua B", "11977776666"));
        List<AlteracaoCampo> campos = capturarCampos(TipoOperacao.INCLUSAO_CLIENTE);
        assertTrue(campos.stream().anyMatch(c -> c.campo().equals("logradouro") && c.valorAnterior() == null));
    }
}
