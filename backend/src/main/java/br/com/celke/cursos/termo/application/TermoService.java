package br.com.celke.cursos.termo.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.termo.domain.AceiteTermo;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import br.com.celke.cursos.termo.infra.AceiteTermoRepository;
import br.com.celke.cursos.termo.infra.TermoRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Termos de uso e política de privacidade versionados. A versão vigente de cada tipo é a maior; o
 * usuário precisa ter aceitado todas as vigentes para usar a área do aluno (ver
 * {@code TermoPendenteInterceptor}). Publicar uma versão nova exige um aceite novo de todos.
 */
@Service
public class TermoService {

    private final TermoRepository termos;
    private final AceiteTermoRepository aceites;
    private final Clock clock;

    public TermoService(TermoRepository termos, AceiteTermoRepository aceites, Clock clock) {
        this.termos = termos;
        this.aceites = aceites;
        this.clock = clock;
    }

    /** Publica a próxima versão do tipo. Duas publicações simultâneas: a segunda recebe 409. */
    @Transactional
    public Termo publicar(Long adminId, TipoTermo tipo, String conteudoMarkdown) {
        int versao = termos.findFirstByTipoOrderByVersaoDesc(tipo)
                        .map(Termo::getVersao)
                        .orElse(0)
                + 1;
        try {
            return termos.saveAndFlush(new Termo(tipo, versao, conteudoMarkdown, adminId, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Outra versão deste termo foi publicada ao mesmo tempo. Tente de novo.");
        }
    }

    @Transactional(readOnly = true)
    public List<Termo> vigentes() {
        return termos.vigentes();
    }

    @Transactional(readOnly = true)
    public List<Termo> historico(TipoTermo tipo) {
        return termos.findByTipoOrderByVersaoDesc(tipo);
    }

    @Transactional(readOnly = true)
    public List<Termo> pendentes(Long usuarioId) {
        return termos.pendentes(usuarioId);
    }

    /**
     * Registra o aceite de uma versão vigente; aceitar de novo não muda nada. Versão antiga: 409. Sem
     * transação própria: o aceite simultâneo repetido falha só no próprio {@code save} e é ignorado.
     */
    public void aceitar(Long usuarioId, Long termoId, OrigemRequisicao origem) {
        Termo termo = termos.findById(termoId).orElseThrow(() -> new NaoEncontradoException("Termo não encontrado."));
        boolean vigente = termos.vigentes().stream().anyMatch(t -> t.getId().equals(termo.getId()));
        if (!vigente) {
            throw new ConflitoException("Esta versão não é mais a vigente. Aceite a versão atual.");
        }
        if (aceites.existsByUsuarioIdAndTermoId(usuarioId, termoId)) {
            return;
        }
        try {
            aceites.save(new AceiteTermo(usuarioId, termoId, origem.ip(), origem.userAgent(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Aceite repetido ao mesmo tempo: o primeiro já vale
        }
    }
}
