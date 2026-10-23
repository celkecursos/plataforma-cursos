package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.forum.domain.Anexo;
import br.com.celke.cursos.forum.domain.Resposta;
import br.com.celke.cursos.forum.domain.Topico;
import br.com.celke.cursos.forum.infra.AnexoRepository;
import br.com.celke.cursos.forum.infra.RespostaRepository;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Monta as visões do fórum com o nome dos autores, a contagem de respostas e os anexos (com URL
 * temporária). Quem chama decide se as mensagens ocultas entram (só na moderação).
 */
@Component
public class LeituraForum {

    private final RespostaRepository respostas;
    private final AnexoRepository anexos;
    private final UsuarioService usuarioService;
    private final AnexoForumService anexoForumService;

    public LeituraForum(
            RespostaRepository respostas,
            AnexoRepository anexos,
            UsuarioService usuarioService,
            AnexoForumService anexoForumService) {
        this.respostas = respostas;
        this.anexos = anexos;
        this.usuarioService = usuarioService;
        this.anexoForumService = anexoForumService;
    }

    public Page<TopicoResumo> resumos(Page<Topico> pagina) {
        List<Long> ids = pagina.getContent().stream().map(Topico::getId).toList();
        Map<Long, Long> contagem = ids.isEmpty()
                ? Map.of()
                : respostas.contarVisiveis(ids).stream()
                        .collect(Collectors.toMap(linha -> (Long) linha[0], linha -> (Long) linha[1]));
        Map<Long, Usuario> autores =
                autores(pagina.getContent().stream().map(Topico::getAutorId).toList());
        return pagina.map(
                t -> new TopicoResumo(t, nome(autores, t.getAutorId()), contagem.getOrDefault(t.getId(), 0L)));
    }

    public TopicoDetalhe detalhe(Topico topico, boolean incluirOcultas) {
        List<Resposta> lista = incluirOcultas
                ? respostas.findByTopicoIdOrderByCriadoEmAscIdAsc(topico.getId())
                : respostas.findByTopicoIdAndOcultoFalseOrderByCriadoEmAscIdAsc(topico.getId());
        Set<Long> autorIds = new HashSet<>();
        autorIds.add(topico.getAutorId());
        lista.forEach(r -> autorIds.add(r.getAutorId()));
        Map<Long, Usuario> autores = autores(autorIds);
        Map<Long, List<Anexo>> anexosPorResposta = lista.isEmpty()
                ? Map.of()
                : anexos
                        .findByRespostaIdInOrderById(
                                lista.stream().map(Resposta::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(Anexo::getRespostaId));
        return new TopicoDetalhe(
                topico,
                nome(autores, topico.getAutorId()),
                visoes(anexos.findByTopicoIdOrderById(topico.getId())),
                lista.stream()
                        .map(r -> new RespostaDetalhe(
                                r,
                                nome(autores, r.getAutorId()),
                                visoes(anexosPorResposta.getOrDefault(r.getId(), List.of()))))
                        .toList());
    }

    private List<AnexoVisao> visoes(List<Anexo> lista) {
        return lista.stream()
                .map(a -> new AnexoVisao(
                        a.getId(), anexoForumService.url(a.getChave()).toString()))
                .toList();
    }

    private Map<Long, Usuario> autores(Collection<Long> ids) {
        return usuarioService.buscarPorIds(ids);
    }

    private static String nome(Map<Long, Usuario> autores, Long id) {
        Usuario usuario = autores.get(id);
        return usuario == null ? null : usuario.getNome();
    }

    public record TopicoResumo(Topico topico, String autorNome, long respostas) {}

    public record TopicoDetalhe(
            Topico topico, String autorNome, List<AnexoVisao> anexos, List<RespostaDetalhe> respostas) {}

    public record RespostaDetalhe(Resposta resposta, String autorNome, List<AnexoVisao> anexos) {}

    /** Anexo com URL temporária (vale 10 minutos). */
    public record AnexoVisao(Long id, String url) {}
}
