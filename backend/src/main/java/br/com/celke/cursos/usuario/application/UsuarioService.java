package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações de usuário usadas pelos outros módulos (eles nunca acessam o repositório direto). */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Cria um usuário com o e-mail já confirmado: contas criadas por quem administra a plataforma
     * (comando {@code criar-admin}, seeds). A senha já deve ter passado pela {@code PoliticaSenha}.
     */
    @Transactional
    public Usuario criarConfirmado(String nome, String email, String senha, Set<Papel> papeis) {
        return criar(nome, email, senha, papeis, true);
    }

    /** Cadastro feito pelo próprio aluno: o e-mail fica pendente até ele confirmar. */
    @Transactional
    public Usuario cadastrarAluno(String nome, String email, String senha) {
        return criar(nome, email, senha, Set.of(Papel.ALUNO), false);
    }

    /**
     * Conta de aluno criada por uma compra: sem senha e com o e-mail confirmado (o pagamento chegou a
     * ele). O dono cria a senha pelo link enviado no e-mail da matrícula.
     */
    @Transactional
    public Usuario criarAlunoSemSenha(String nome, String email) {
        return criar(nome, email, null, Set.of(Papel.ALUNO), true);
    }

    private Usuario criar(String nome, String email, String senha, Set<Papel> papeis, boolean confirmado) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        String hash = senha == null ? null : passwordEncoder.encode(senha);
        return repository.save(new Usuario(nome, emailNormalizado, hash, papeis, confirmado, clock.instant()));
    }

    @Transactional
    public void confirmarEmail(Usuario usuario) {
        usuario.confirmarEmail(clock.instant());
        repository.save(usuario);
    }

    /** Troca a senha. A nova senha já deve ter passado pela {@code PoliticaSenha}. */
    @Transactional
    public void trocarSenha(Usuario usuario, String novaSenha) {
        usuario.trocarHashSenha(passwordEncoder.encode(novaSenha), clock.instant());
        repository.save(usuario);
    }

    /** A senha informada é a atual do usuário (troca de senha no perfil). */
    public boolean senhaConfere(Usuario usuario, String senha) {
        return senha != null && usuario.possuiSenha() && passwordEncoder.matches(senha, usuario.getSenhaHash());
    }

    @Transactional
    public Usuario renomear(Long usuarioId, String nome) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        usuario.renomear(nome, clock.instant());
        return repository.save(usuario);
    }

    /** Troca a foto de perfil (nula remove); devolve a chave da anterior. */
    @Transactional
    public Optional<String> trocarFoto(Long usuarioId, String chave) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        String anterior = usuario.trocarFoto(chave, clock.instant());
        repository.save(usuario);
        return Optional.ofNullable(anterior);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(Usuario.normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id);
    }

    /** Usuários indexados pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Usuario> buscarPorIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    /** Regrava o hash quando ele usa um algoritmo antigo (ex.: BCrypt vindo do sistema legado). */
    @Transactional
    public void atualizarHashSeNecessario(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.trocarHashSenha(passwordEncoder.encode(senha), clock.instant());
            repository.save(usuario);
        }
    }
}
