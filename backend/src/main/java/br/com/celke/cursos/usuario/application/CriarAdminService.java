package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Cria um administrador. Usado pelo comando {@code criar-admin}, nunca por migration. */
@Service
public class CriarAdminService {

    private final UsuarioService usuarioService;

    public CriarAdminService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public Usuario criar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        String problemaSenha = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN);
        if (problemaSenha != null) {
            throw new IllegalArgumentException(problemaSenha);
        }
        return usuarioService.criarConfirmado(nome, email, senha, Set.of(Papel.ADMIN));
    }
}
