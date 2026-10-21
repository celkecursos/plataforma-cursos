package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;

    @BeforeEach
    void criar() {
        service = new UsuarioService(repository, passwordEncoder, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("cria o usuário com o e-mail normalizado e a senha em hash")
    void cria() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(false);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Usuario criado = service.criar("Ana", " Ana@Celke.uno ", "senha-da-ana-123", Set.of(Papel.ALUNO));

        assertThat(criado.getEmail()).isEqualTo("ana@celke.uno");
        assertThat(criado.getSenhaHash()).isEqualTo("{argon2}hash");
        assertThat(criado.getPapeis()).containsExactly(Papel.ALUNO);
    }

    @Test
    @DisplayName("rejeita e-mail já cadastrado sem gravar nem calcular hash")
    void emailDuplicado() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(true);

        assertThatThrownBy(() -> service.criar("Ana", "ANA@celke.uno", "senha-da-ana-123", Set.of(Papel.ALUNO)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe um usuário com este e-mail.");
        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("busca por e-mail usa o e-mail normalizado")
    void buscaPorEmail() {
        service.buscarPorEmail(" Ana@Celke.uno ");

        verify(repository).findByEmail("ana@celke.uno");
    }

    @Test
    @DisplayName("regrava o hash quando ele usa algoritmo antigo")
    void regravaHashAntigo() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "$2y$10$antigo", Papel.ALUNO);
        when(passwordEncoder.upgradeEncoding("$2y$10$antigo")).thenReturn(true);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}novo");

        service.atualizarHashSeNecessario(usuario, "senha-da-ana-123");

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("{argon2}novo");
    }

    @Test
    @DisplayName("não regrava o hash que já está no algoritmo atual")
    void mantemHashAtual() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "{argon2}atual", Papel.ALUNO);
        when(passwordEncoder.upgradeEncoding("{argon2}atual")).thenReturn(false);

        service.atualizarHashSeNecessario(usuario, "senha-da-ana-123");

        verify(repository, never()).save(any());
    }
}
