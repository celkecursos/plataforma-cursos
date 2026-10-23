package br.com.celke.cursos.arquivo.api;

import br.com.celke.cursos.arquivo.api.ArquivoResponses.Arquivo;
import br.com.celke.cursos.arquivo.api.ArquivoResponses.EnvioResponse;
import br.com.celke.cursos.arquivo.application.ArquivoAdmService;
import br.com.celke.cursos.arquivo.application.DonoArquivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Arquivos de apoio")
public class ArquivoAdmController {

    private final ArquivoAdmService arquivoAdmService;

    public ArquivoAdmController(ArquivoAdmService arquivoAdmService) {
        this.arquivoAdmService = arquivoAdmService;
    }

    @GetMapping("/aulas/{aulaId}/arquivos")
    @Operation(summary = "Arquivos da aula")
    public List<Arquivo> listarDaAula(@PathVariable Long aulaId) {
        return lista(DonoArquivo.aula(aulaId));
    }

    @GetMapping("/modulos/{moduloId}/arquivos")
    @Operation(summary = "Arquivos do módulo")
    public List<Arquivo> listarDoModulo(@PathVariable Long moduloId) {
        return lista(DonoArquivo.modulo(moduloId));
    }

    @PostMapping("/aulas/{aulaId}/arquivos/envio")
    @Operation(
            summary = "Iniciar o envio de um arquivo da aula",
            description = "ZIP, PDF ou TXT até 200 MB. Devolve a URL para enviar direto ao armazenamento.")
    public EnvioResponse iniciarEnvioDaAula(
            @PathVariable Long aulaId, @Valid @RequestBody ArquivoRequests.IniciarEnvio request) {
        return iniciar(DonoArquivo.aula(aulaId), request);
    }

    @PostMapping("/modulos/{moduloId}/arquivos/envio")
    @Operation(
            summary = "Iniciar o envio de um arquivo do módulo",
            description = "ZIP, PDF ou TXT até 200 MB. Devolve a URL para enviar direto ao armazenamento.")
    public EnvioResponse iniciarEnvioDoModulo(
            @PathVariable Long moduloId, @Valid @RequestBody ArquivoRequests.IniciarEnvio request) {
        return iniciar(DonoArquivo.modulo(moduloId), request);
    }

    @PostMapping("/aulas/{aulaId}/arquivos")
    @Operation(summary = "Confirmar um arquivo da aula", description = "Confere o arquivo enviado e o publica.")
    public ResponseEntity<Arquivo> confirmarDaAula(
            @PathVariable Long aulaId, @Valid @RequestBody ArquivoRequests.Confirmar request) {
        return confirmar(DonoArquivo.aula(aulaId), request);
    }

    @PostMapping("/modulos/{moduloId}/arquivos")
    @Operation(summary = "Confirmar um arquivo do módulo", description = "Confere o arquivo enviado e o publica.")
    public ResponseEntity<Arquivo> confirmarDoModulo(
            @PathVariable Long moduloId, @Valid @RequestBody ArquivoRequests.Confirmar request) {
        return confirmar(DonoArquivo.modulo(moduloId), request);
    }

    @DeleteMapping("/arquivos/{id}")
    @Operation(summary = "Remover arquivo", description = "Apaga o registro e o arquivo do armazenamento.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        arquivoAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private List<Arquivo> lista(DonoArquivo dono) {
        return arquivoAdmService.listar(dono).stream().map(Arquivo::de).toList();
    }

    private EnvioResponse iniciar(DonoArquivo dono, ArquivoRequests.IniciarEnvio request) {
        return EnvioResponse.de(arquivoAdmService.iniciarEnvio(dono, request.contentType(), request.tamanho()));
    }

    private ResponseEntity<Arquivo> confirmar(DonoArquivo dono, ArquivoRequests.Confirmar request) {
        Arquivo arquivo =
                Arquivo.de(arquivoAdmService.confirmar(dono, request.chave(), request.titulo(), request.nomeArquivo()));
        return ResponseEntity.created(URI.create("/api/adm/arquivos/" + arquivo.id()))
                .body(arquivo);
    }
}
