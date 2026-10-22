package br.com.celke.cursos.video.api;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.video.application.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playlists HLS da aula para o player (hls.js com o token no cabeçalho). Nunca vão para cache: as URLs
 * dos segmentos são pessoais e vencem.
 */
@RestController
@RequestMapping("/api/aulas/{aulaId}")
@Tag(name = "Vídeo da aula")
public class AulaVideoController {

    static final MediaType HLS = MediaType.parseMediaType("application/vnd.apple.mpegurl");

    private final PlaylistService playlistService;

    public AulaVideoController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping("/video.m3u8")
    @Operation(
            summary = "Master playlist do vídeo",
            description = "Exige acesso à aula (matrícula vigente ou aula gratuita). Lista as variantes; o acesso"
                    + " fica registrado no log.")
    public ResponseEntity<String> master(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId, HttpServletRequest request) {
        return hls(playlistService.master(usuario(jwt), aulaId, OrigemRequisicao.de(request)));
    }

    @GetMapping("/video/{variante:[0-9]+p}.m3u8")
    @Operation(
            summary = "Playlist de uma variante",
            description = "Segmentos por URLs pré-assinadas, válidas pela duração do vídeo mais uma margem.")
    public ResponseEntity<String> variante(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId, @PathVariable String variante) {
        return hls(playlistService.variante(usuario(jwt), aulaId, variante));
    }

    private static ResponseEntity<String> hls(String playlist) {
        return ResponseEntity.ok()
                .contentType(HLS)
                .cacheControl(CacheControl.noStore())
                .body(playlist);
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
