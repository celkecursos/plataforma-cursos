-- Confirmação de e-mail: nulo enquanto o dono não confirmar. Contas que já existiam foram criadas
-- por quem administra a plataforma e ficam confirmadas.
ALTER TABLE usuarios ADD COLUMN email_confirmado_em TIMESTAMPTZ;
UPDATE usuarios SET email_confirmado_em = criado_em;

-- Tokens de uso único enviados por e-mail (confirmação de e-mail, recuperação de senha).
-- Só o hash SHA-256 é gravado; o token em texto existe apenas no link do e-mail.
CREATE TABLE tokens_uso_unico (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo       VARCHAR(30) NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    expira_em  TIMESTAMPTZ NOT NULL,
    usado_em   TIMESTAMPTZ,
    criado_em  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_tokens_uso_unico_hash UNIQUE (hash),
    CONSTRAINT ck_tokens_uso_unico_tipo CHECK (tipo IN ('CONFIRMACAO_EMAIL', 'RECUPERACAO_SENHA'))
);

CREATE INDEX ix_tokens_uso_unico_usuario_tipo ON tokens_uso_unico (usuario_id, tipo, criado_em);

-- Novo resultado na auditoria: senha certa, e-mail ainda não confirmado
ALTER TABLE auditoria_login DROP CONSTRAINT ck_auditoria_login_resultado;
ALTER TABLE auditoria_login ADD CONSTRAINT ck_auditoria_login_resultado
    CHECK (resultado IN ('SUCESSO', 'EMAIL_DESCONHECIDO', 'SENHA_INVALIDA', 'USUARIO_INATIVO',
                         'EMAIL_NAO_CONFIRMADO', 'BLOQUEADO'));
