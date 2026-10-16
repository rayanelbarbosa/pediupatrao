package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock UsuarioRepository usuarioRepository;
    @Mock AuditoriaService auditoriaService;

    UsuarioService service;

    @BeforeEach
    void preparar() {
        service = new UsuarioService(usuarioRepository, new BCryptPasswordEncoder(), auditoriaService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(String id, String nome, String role, String senha) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(nome);
        u.setRole(role);
        u.setPassword(senha);
        return u;
    }

    @Test
    void perfilInvalidoNaCriacaoEhRecusado() {
        assertThrows(RegraNegocioException.class,
                () -> service.salvarUsuario(usuario(null, "novo", "SUPERUSER", "senha1234")));
        assertThrows(RegraNegocioException.class,
                () -> service.salvarUsuario(usuario(null, "novo", "USER", "senha1234")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void senhaCurtaEhRecusada() {
        assertThrows(RegraNegocioException.class,
                () -> service.salvarUsuario(usuario(null, "novo", "ATENDENTE", "123")));
    }

    @Test
    void senhaEhGravadaCriptografada() {
        when(usuarioRepository.findByUsername("novo")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        Usuario salvo = service.salvarUsuario(usuario("idMalicioso", "novo", "atendente", "senha1234"));

        assertNotEquals("senha1234", salvo.getPassword());
        assertEquals("ATENDENTE", salvo.getRole());
        assertEquals(null, salvo.getId());
    }

    @Test
    void administradorNaoAlteraOProprioPerfil() {
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(usuario("1", "admin", "ADMIN", "hash")));
        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("1", usuario(null, "admin", "GERENTE", null)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoRemoveOUltimoAdministrador() {
        when(usuarioRepository.findById("2")).thenReturn(Optional.of(usuario("2", "outro", "ADMIN", "hash")));
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario("2", "outro", "ADMIN", "hash"),
                usuario("3", "g", "GERENTE", "hash")));
        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("2", usuario(null, "outro", "GERENTE", null)));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("2"));
    }

    @Test
    void naoExcluiOProprioUsuario() {
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(usuario("1", "admin", "ADMIN", "hash")));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("1"));
    }

    @Test
    void adminPodeAtribuirPerfilAOutroUsuario() {
        when(usuarioRepository.findById("5")).thenReturn(Optional.of(usuario("5", "joao", "ATENDENTE", "hash")));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        Usuario atualizado = service.atualizarUsuario("5", usuario(null, "joao", "GERENTE", ""));

        assertEquals("GERENTE", atualizado.getRole());
        assertEquals("hash", atualizado.getPassword()); // senha em branco mantem a atual
    }
}
