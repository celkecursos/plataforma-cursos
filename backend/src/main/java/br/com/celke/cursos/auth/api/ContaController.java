package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.CadastroService;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro, confirmação de e-mail e recuperação de senha. As rotas que recebem um e-mail respondem
 * sempre o mesmo, exista ou não a conta: ninguém descobre por aqui quais e-mails estão cadastrados.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Conta")
public class ContaController {

    static final String MENSAGEM_CADASTRO = "Cadastro recebido. Confira o seu e-mail para continuar.";
    static final String MENSAGEM_REENVIO = "Se houver um cadastro pendente para este e-mail, enviaremos um novo link.";
    static final String MENSAGEM_RECUPERACAO =
            "Se houver uma conta com este e-mail, enviaremos um link para criar uma nova senha.";

    private final CadastroService cadastroService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;

    public ContaController(CadastroService cadastroService, RecuperacaoSenhaService recuperacaoSenhaService) {
        this.cadastroService = cadastroService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    @PostMapping("/cadastro")
    @Operation(summary = "Criar conta de aluno", description = "Envia o link de confirmação para o e-mail.")
    public ResponseEntity<MensagemResponse> cadastrar(@Valid @RequestBody ContaRequests.Cadastro request) {
        cadastroService.cadastrar(request.nome(), request.email(), request.senha());
        return ResponseEntity.accepted().body(new MensagemResponse(MENSAGEM_CADASTRO));
    }

    @PostMapping("/confirmar-email")
    @Operation(summary = "Confirmar o e-mail com o token do link")
    public MensagemResponse confirmarEmail(@Valid @RequestBody ContaRequests.Token request) {
        cadastroService.confirmarEmail(request.token());
        return new MensagemResponse("E-mail confirmado. Você já pode entrar.");
    }

    @PostMapping("/reenviar-confirmacao")
    @Operation(summary = "Reenviar o link de confirmação")
    public ResponseEntity<MensagemResponse> reenviarConfirmacao(
            @Valid @RequestBody ContaRequests.EmailInformado request) {
        cadastroService.reenviarConfirmacao(request.email());
        return ResponseEntity.accepted().body(new MensagemResponse(MENSAGEM_REENVIO));
    }

    @PostMapping("/recuperar-senha")
    @Operation(summary = "Pedir o link para criar uma nova senha")
    public ResponseEntity<MensagemResponse> recuperarSenha(@Valid @RequestBody ContaRequests.EmailInformado request) {
        recuperacaoSenhaService.solicitar(request.email());
        return ResponseEntity.accepted().body(new MensagemResponse(MENSAGEM_RECUPERACAO));
    }

    @PostMapping("/redefinir-senha")
    @Operation(summary = "Criar uma nova senha com o token do link", description = "Encerra todas as sessões.")
    public MensagemResponse redefinirSenha(@Valid @RequestBody ContaRequests.RedefinirSenha request) {
        recuperacaoSenhaService.redefinir(request.token(), request.senha());
        return new MensagemResponse("Senha alterada. Entre com a nova senha.");
    }
}
