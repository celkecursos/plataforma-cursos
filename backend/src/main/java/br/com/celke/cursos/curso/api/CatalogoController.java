package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.CursoCatalogo;
import br.com.celke.cursos.curso.api.CursoResponses.CursoCatalogoDetalhe;
import br.com.celke.cursos.curso.application.CapaCursoService;
import br.com.celke.cursos.curso.application.CatalogoService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Duration;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo público: rotas sem login, declaradas no {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/cursos")
@Tag(name = "Catálogo")
public class CatalogoController {

    private final CatalogoService catalogoService;
    private final CapaCursoService capaCursoService;

    public CatalogoController(CatalogoService catalogoService, CapaCursoService capaCursoService) {
        this.catalogoService = catalogoService;
        this.capaCursoService = capaCursoService;
    }

    @GetMapping
    @Operation(summary = "Cursos publicados", description = "Em ordem alfabética.")
    public Pagina<CursoCatalogo> listar(@ParameterObject Pageable pagina) {
        return Pagina.de(catalogoService.listar(pagina).map(CursoCatalogo::de));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Curso publicado", description = "Com os módulos e as aulas ativos da turma atual.")
    public CursoCatalogoDetalhe detalhar(@PathVariable String slug) {
        return CursoCatalogoDetalhe.de(catalogoService.detalhar(slug));
    }

    @GetMapping("/{slug}/capa")
    @Operation(summary = "Capa do curso", description = "Redireciona para a imagem (endereço temporário).")
    public ResponseEntity<Void> capa(@PathVariable String slug) {
        URI imagem = capaCursoService
                .url(catalogoService.buscar(slug))
                .map(url -> URI.create(url.toString()))
                .orElseThrow(() -> new NaoEncontradoException("Curso sem capa."));
        // O redirecionamento pode ficar em cache por bem menos tempo que a validade da URL (1 hora)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(imagem)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .build();
    }
}
