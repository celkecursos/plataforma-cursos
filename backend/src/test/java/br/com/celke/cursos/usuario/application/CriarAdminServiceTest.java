package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CriarAdminServiceTest {

    @Mock
    private UsuarioService usuarioService;

    private CriarAdminService service;

    @BeforeEach
    void criar() {
        service = new CriarAdminService(usuarioService);
    }

    @Test
    @DisplayName("cria o usuário só com o papel ADMIN")
    void criaAdmin() {
        service.criar("Admin", "admin@celke.uno", "senha-longa-do-admin");

        verify(usuarioService).criar("Admin", "admin@celke.uno", "senha-longa-do-admin", Set.of(Papel.ADMIN));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "nome em branco,      '  ',  admin@celke.uno, senha-longa-do-admin, O nome é obrigatório.",
        "e-mail sem arroba,   Admin, admin.celke.uno, senha-longa-do-admin, E-mail inválido.",
        "senha curta,         Admin, admin@celke.uno, curta-11chr,          A senha deve ter pelo menos 12 caracteres.",
    })
    void rejeitaDadosInvalidos(String caso, String nome, String email, String senha, String mensagem) {
        assertThatThrownBy(() -> service.criar(nome, email, senha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensagem);
        verify(usuarioService, never()).criar(any(), any(), any(), any());
    }
}
