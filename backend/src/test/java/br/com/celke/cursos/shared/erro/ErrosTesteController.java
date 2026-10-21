package br.com.celke.cursos.shared.erro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas que só existem nos testes, para provocar cada tipo de erro tratado pelo ManipuladorErros. */
@RestController
@RequestMapping("/teste/erros")
class ErrosTesteController {

    record Dados(
            @NotBlank String nome,
            @NotBlank @Email String email,
            @Min(1) int quantidade) {}

    @PostMapping("/validacao")
    Dados validacao(@Valid @RequestBody Dados dados) {
        return dados;
    }

    @GetMapping("/nao-encontrado")
    void naoEncontrado() {
        throw new NaoEncontradoException("Curso não encontrado.");
    }

    @GetMapping("/conflito")
    void conflito() {
        throw new ConflitoException("Já existe um curso com este slug.");
    }

    @GetMapping("/inesperado")
    void inesperado() {
        throw new IllegalStateException("detalhe interno que não pode vazar");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String somenteAdmin() {
        return "ok";
    }
}
