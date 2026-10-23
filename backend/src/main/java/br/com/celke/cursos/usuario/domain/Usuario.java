package br.com.celke.cursos.usuario.domain;

import br.com.celke.cursos.shared.auditoria.DadoSensivel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    /** Nome que aparece no lugar do autor depois que a conta é excluída. */
    public static final String NOME_EXCLUIDA = "Conta excluída";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    /** Nulo na conta criada por uma compra, até o dono criar a senha pelo link do e-mail. */
    @DadoSensivel
    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 20)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    /** Nulo enquanto o dono não confirmar o e-mail. */
    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    /** Foto de perfil no bucket ({@code fotos/<usuario>/<uuid>.jpg}); nula sem foto. */
    @Column(name = "foto_chave", length = 200)
    private String fotoChave;

    /** Encerramento das sessões pelo ADM: access tokens emitidos antes disso deixam de valer. */
    @Column(name = "sessoes_encerradas_em")
    private Instant sessoesEncerradasEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {}

    public Usuario(
            String nome, String email, String senhaHash, Set<Papel> papeis, boolean emailConfirmado, Instant agora) {
        this.nome = nome.strip();
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.ativo = true;
        this.papeis = EnumSet.copyOf(papeis);
        this.emailConfirmadoEm = emailConfirmado ? agora : null;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** E-mail sempre em minúsculas e sem espaços nas pontas (o banco também exige minúsculas). */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Confirma o e-mail; confirmar de novo não muda a data da primeira confirmação. */
    public void confirmarEmail(Instant agora) {
        if (emailConfirmadoEm == null) {
            this.emailConfirmadoEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public boolean possuiSenha() {
        return senhaHash != null;
    }

    public boolean isEmailConfirmado() {
        return emailConfirmadoEm != null;
    }

    public void trocarHashSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    /** Troca a foto (nula remove) e devolve a chave da anterior, a apagar do bucket. */
    public String trocarFoto(String chave, Instant agora) {
        String anterior = this.fotoChave;
        this.fotoChave = chave;
        this.atualizadoEm = agora;
        return anterior;
    }

    /** Bloqueia a conta (não entra mais) e encerra as sessões abertas. */
    public void bloquear(Instant agora) {
        this.ativo = false;
        encerrarSessoes(agora);
    }

    public void desbloquear(Instant agora) {
        this.ativo = true;
        this.atualizadoEm = agora;
    }

    public void encerrarSessoes(Instant agora) {
        this.sessoesEncerradasEm = agora;
        this.atualizadoEm = agora;
    }

    /**
     * Exclusão da conta a pedido do titular: tira nome, e-mail, senha e foto, inativa e encerra as
     * sessões. O id fica (matrículas, pedidos, aceites e auditoria apontam para ele). Devolve a chave da
     * foto, a apagar do bucket.
     */
    public String anonimizar(Instant agora) {
        String foto = this.fotoChave;
        this.nome = NOME_EXCLUIDA;
        this.email = "excluida-" + id + "@conta-excluida.invalid";
        this.senhaHash = null;
        this.fotoChave = null;
        bloquear(agora);
        return foto;
    }

    public boolean isExcluida() {
        return email.endsWith("@conta-excluida.invalid");
    }

    public Instant getSessoesEncerradasEm() {
        return sessoesEncerradasEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getEmailConfirmadoEm() {
        return emailConfirmadoEm;
    }

    public String getFotoChave() {
        return fotoChave;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Papel> getPapeis() {
        return Set.copyOf(papeis);
    }
}
