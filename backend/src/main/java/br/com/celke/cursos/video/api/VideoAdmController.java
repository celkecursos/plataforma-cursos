package br.com.celke.cursos.video.api;

import br.com.celke.cursos.video.api.VideoResponses.EnvioVideoResponse;
import br.com.celke.cursos.video.api.VideoResponses.VideoAdm;
import br.com.celke.cursos.video.application.EnvioVideoService;
import br.com.celke.cursos.video.application.VideoAdmService;
import br.com.celke.cursos.video.domain.Video;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/aulas/{aulaId}/video")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Vídeos")
public class VideoAdmController {

    private final VideoAdmService videoAdmService;
    private final EnvioVideoService envioVideoService;

    public VideoAdmController(VideoAdmService videoAdmService, EnvioVideoService envioVideoService) {
        this.videoAdmService = videoAdmService;
        this.envioVideoService = envioVideoService;
    }

    @GetMapping
    @Operation(
            summary = "Vídeo da aula",
            description = "Status do processamento, tamanho antes e depois, duração, variantes e thumbnail.")
    public VideoAdm buscar(@PathVariable Long aulaId) {
        return resposta(videoAdmService.buscarPorAula(aulaId));
    }

    @PostMapping("/envio")
    @Operation(
            summary = "Iniciar o envio do vídeo",
            description = "MP4, MOV, MKV ou WebM. Devolve uma URL pré-assinada por parte, para o navegador enviar"
                    + " direto ao armazenamento. Substitui o vídeo atual quando a conversão do novo terminar.")
    public EnvioVideoResponse iniciarEnvio(
            @PathVariable Long aulaId, @Valid @RequestBody VideoRequests.IniciarEnvio request) {
        return EnvioVideoResponse.de(
                envioVideoService.iniciar(aulaId, request.nomeArquivo(), request.contentType(), request.tamanho()));
    }

    @PostMapping("/envio/conclusao")
    @Operation(
            summary = "Concluir o envio",
            description = "Junta as partes, confere o tamanho e o tipo real do arquivo e coloca o vídeo na fila.")
    public ResponseEntity<VideoAdm> concluirEnvio(
            @PathVariable Long aulaId, @Valid @RequestBody VideoRequests.ConcluirEnvio request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(resposta(envioVideoService.concluir(aulaId, request.enviadas())));
    }

    @PostMapping("/reprocessamento")
    @Operation(summary = "Reprocessar", description = "Só para vídeo com erro cujo original ainda está guardado.")
    public ResponseEntity<VideoAdm> reprocessar(@PathVariable Long aulaId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resposta(envioVideoService.reprocessar(aulaId)));
    }

    @DeleteMapping
    @Operation(
            summary = "Remover o vídeo",
            description = "Apaga o vídeo e os arquivos; não vale durante o processamento.")
    public ResponseEntity<Void> excluir(@PathVariable Long aulaId) {
        envioVideoService.excluir(aulaId);
        return ResponseEntity.noContent().build();
    }

    private VideoAdm resposta(Video video) {
        return VideoAdm.de(video, envioVideoService.urlThumbnail(video));
    }
}
