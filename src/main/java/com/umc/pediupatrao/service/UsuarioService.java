package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {
    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario salvarUsuario(Usuario novo) {
        String username = normalizarUsername(novo.getUsername());
        Perfil perfil = exigirPerfil(novo.getRole());
        validarSenha(novo.getPassword());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com este nome.");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRole(perfil.name());
        usuario.setPassword(passwordEncoder.encode(novo.getPassword()));
        Usuario salvo = usuarioRepository.save(usuario);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_PERFIL_USUARIO, "USUARIO", salvo.getId(),
                List.of(new AlteracaoCampo("perfil", null, perfil.name())), null);
        return salvo;
    }

    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        String logado = ContextoSeguranca.usuario();
        boolean proprioUsuario = existente.getUsername().equals(logado);

        String username = normalizarUsername(dados.getUsername());
        Perfil novoPerfil = exigirPerfil(dados.getRole());
        String perfilAnterior = existente.getRole();

        if (!username.equals(existente.getUsername())
                && usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com este nome.");
        }
        if (!novoPerfil.name().equals(perfilAnterior)) {
            // Ninguem altera o proprio perfil (evita autoelevacao e perda do ultimo admin)
            if (proprioUsuario) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
            }
            if (Perfil.ADMIN.name().equals(perfilAnterior) && contarAdmins() <= 1) {
                throw new RegraNegocioException("O sistema precisa manter pelo menos um administrador.");
            }
        }
        if (proprioUsuario && !username.equals(logado)) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio nome de usuário enquanto estiver logado.");
        }

        existente.setUsername(username);
        existente.setRole(novoPerfil.name());
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }
        Usuario salvo = usuarioRepository.save(existente);

        if (!novoPerfil.name().equals(perfilAnterior)) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_PERFIL_USUARIO, "USUARIO", id,
                    List.of(new AlteracaoCampo("perfil", perfilAnterior, novoPerfil.name())), null);
        }
        return salvo;
    }

    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(ContextoSeguranca.usuario())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && contarAdmins() <= 1) {
            throw new RegraNegocioException("O sistema precisa manter pelo menos um administrador.");
        }
        usuarioRepository.deleteById(id);
    }

    private long contarAdmins() {
        return usuarioRepository.findAll().stream()
                .filter(u -> Perfil.ADMIN.name().equals(u.getRole()))
                .count();
    }

    private String normalizarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("Informe o nome de usuário.");
        }
        return username.trim();
    }

    private Perfil exigirPerfil(String role) {
        return Perfil.de(role)
                .orElseThrow(() -> new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE."));
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
