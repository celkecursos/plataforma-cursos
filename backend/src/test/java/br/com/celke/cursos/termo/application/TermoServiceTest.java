package br.com.celke.cursos.termo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.termo.domain.AceiteTermo;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import br.com.celke.cursos.termo.infra.AceiteTermoRepository;
import br.com.celke.cursos.termo.infra.TermoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.9", "navegador");

    @Mock
    private TermoRepository termos;

    @Mock
    private AceiteTermoRepository aceites;

    private TermoService service;

    @BeforeEach
    void criar() {
        service = new TermoService(termos, aceites, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Termo termo(long id, TipoTermo tipo, int versao) {
        Termo termo = new Termo(tipo, versao, "Texto **v" + versao + "**", 1L, AGORA);
        ReflectionTestUtils.setField(termo, "id", id);
        return termo;
    }

    @Test
    @DisplayName("primeira publicação do tipo é a versão 1; as seguintes somam 1 à maior")
    void versoes() {
        when(termos.findFirstByTipoOrderByVersaoDesc(TipoTermo.TERMOS_USO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(termo(1, TipoTermo.TERMOS_USO, 3)));
        when(termos.saveAndFlush(any(Termo.class))).thenAnswer(i -> i.getArgument(0));

        Termo primeiro = service.publicar(1L, TipoTermo.TERMOS_USO, "  Texto <script>x</script> ");
        Termo quarto = service.publicar(1L, TipoTermo.TERMOS_USO, "Nova");

        assertThat(primeiro.getVersao()).isEqualTo(1);
        assertThat(primeiro.getConteudoHtml()).doesNotContain("<script");
        assertThat(primeiro.getPublicadoEm()).isEqualTo(AGORA);
        assertThat(quarto.getVersao()).isEqualTo(4);
    }

    @Test
    @DisplayName("publicações simultâneas: a segunda recebe 409")
    void publicacaoSimultanea() {
        when(termos.findFirstByTipoOrderByVersaoDesc(TipoTermo.POLITICA_PRIVACIDADE))
                .thenReturn(Optional.empty());
        when(termos.saveAndFlush(any(Termo.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.publicar(1L, TipoTermo.POLITICA_PRIVACIDADE, "T"))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Outra versão deste termo foi publicada ao mesmo tempo. Tente de novo.");
    }

    @Test
    @DisplayName("aceite da versão vigente grava usuário, IP, navegador e instante")
    void aceita() {
        Termo vigente = termo(5, TipoTermo.TERMOS_USO, 2);
        when(termos.findById(5L)).thenReturn(Optional.of(vigente));
        when(termos.vigentes()).thenReturn(List.of(vigente));
        when(aceites.existsByUsuarioIdAndTermoId(7L, 5L)).thenReturn(false);

        service.aceitar(7L, 5L, ORIGEM);

        ArgumentCaptor<AceiteTermo> captor = ArgumentCaptor.forClass(AceiteTermo.class);
        verify(aceites).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(captor.getValue().getTermoId()).isEqualTo(5L);
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.9");
        assertThat(captor.getValue().getAceitoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("aceitar de novo não grava outro aceite; aceite simultâneo repetido é ignorado")
    void aceiteRepetido() {
        Termo vigente = termo(5, TipoTermo.TERMOS_USO, 2);
        when(termos.findById(5L)).thenReturn(Optional.of(vigente));
        when(termos.vigentes()).thenReturn(List.of(vigente));
        when(aceites.existsByUsuarioIdAndTermoId(7L, 5L)).thenReturn(true).thenReturn(false);
        when(aceites.save(any(AceiteTermo.class))).thenThrow(new DataIntegrityViolationException("uq"));

        service.aceitar(7L, 5L, ORIGEM);
        service.aceitar(7L, 5L, ORIGEM);

        verify(aceites).save(any(AceiteTermo.class));
    }

    @Test
    @DisplayName("versão antiga não é aceita (409); termo inexistente responde 404")
    void versaoAntiga() {
        Termo antigo = termo(4, TipoTermo.TERMOS_USO, 1);
        when(termos.findById(4L)).thenReturn(Optional.of(antigo));
        when(termos.findById(9L)).thenReturn(Optional.empty());
        when(termos.vigentes()).thenReturn(List.of(termo(5, TipoTermo.TERMOS_USO, 2)));

        assertThatThrownBy(() -> service.aceitar(7L, 4L, ORIGEM))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Esta versão não é mais a vigente. Aceite a versão atual.");
        assertThatThrownBy(() -> service.aceitar(7L, 9L, ORIGEM))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Termo não encontrado.");
        verify(aceites, never()).save(any());
    }

    @Test
    @DisplayName("termo pendente vira 403 com a lista dos termos")
    void excecao() {
        var excecao = new TermoPendenteException(List.of(termo(5, TipoTermo.TERMOS_USO, 2)));

        assertThat(excecao.getStatus().value()).isEqualTo(403);
        assertThat(excecao.getMessage()).isEqualTo("Aceite os termos atualizados para continuar.");
        assertThat(excecao.propriedades()).containsKey("termosPendentes");
        assertThat((List<?>) excecao.propriedades().get("termosPendentes")).hasSize(1);
    }
}
