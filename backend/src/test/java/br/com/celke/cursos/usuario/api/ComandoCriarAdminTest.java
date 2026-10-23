package br.com.celke.cursos.usuario.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.usuario.application.CriarAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ComandoCriarAdminTest {

    @Mock
    private CriarAdminService criarAdminService;

    @Test
    @DisplayName("reconhece o comando só como primeiro argumento")
    void reconheceComando() {
        assertThat(ComandoCriarAdmin.foiPedido(new String[] {"criar-admin", "a@b.c", "Nome"}))
                .isTrue();
        assertThat(ComandoCriarAdmin.foiPedido(new String[] {})).isFalse();
        assertThat(ComandoCriarAdmin.foiPedido(new String[] {"--debug", "criar-admin"}))
                .isFalse();
    }

    @Test
    @DisplayName("sem e-mail e nome mostra o uso e não cria nada")
    void argumentosFaltando() {
        var comando = new ComandoCriarAdmin(criarAdminService);

        assertThatThrownBy(() -> comando.run(new DefaultApplicationArguments("criar-admin", "admin@celke.uno")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Uso: criar-admin <email> \"<nome>\"");
        verify(criarAdminService, never()).criar(any(), any(), any());
    }
}
