package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {
            repo.findByUsername("admin").ifPresent(admin -> {
                if (Perfil.de(admin.getRole()).isEmpty()) {
                    admin.setRole(Perfil.ADMIN.name());
                    repo.save(admin);
                }
            });

            criarSeNaoExistir(repo, encoder, "admin", "teste123", Perfil.ADMIN);
            criarSeNaoExistir(repo, encoder, "gerente", "gerente123", Perfil.GERENTE);
            criarSeNaoExistir(repo, encoder, "atendente", "atendente123", Perfil.ATENDENTE);
        };
    }

    private void criarSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder,
            String username, String senha, Perfil perfil) {
        if (repo.findByUsername(username).isEmpty()) {
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(encoder.encode(senha));
            user.setRole(perfil.name());
            repo.save(user);
            System.out.println("Usuário " + username + " criado (" + perfil + ").");
        }
    }
}
