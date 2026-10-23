-- Segundo fator (TOTP) dos administradores: segredo cifrado (AES-256-GCM, chave TOTP_CHAVE do
-- ambiente). ativado_em nulo = cadastro iniciado e ainda não confirmado com um código.
-- ultimo_passo guarda o passo de 30 s do último código aceito: o mesmo código não serve duas vezes.
CREATE TABLE segundo_fator (
    usuario_id   BIGINT      PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    segredo      BYTEA       NOT NULL,
    ativado_em   TIMESTAMPTZ,
    ultimo_passo BIGINT,
    criado_em    TIMESTAMPTZ NOT NULL
);

-- Códigos de recuperação (uso único) para quem perdeu o celular: só o hash SHA-256
CREATE TABLE codigos_recuperacao (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    hash       VARCHAR(64) NOT NULL,
    usado_em   TIMESTAMPTZ,
    criado_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_codigos_recuperacao_usuario ON codigos_recuperacao (usuario_id);

-- Desafio do login em duas etapas: token de uso único válido por poucos minutos
ALTER TABLE tokens_uso_unico DROP CONSTRAINT ck_tokens_uso_unico_tipo;
ALTER TABLE tokens_uso_unico ADD CONSTRAINT ck_tokens_uso_unico_tipo
    CHECK (tipo IN ('CONFIRMACAO_EMAIL', 'RECUPERACAO_SENHA', 'DESAFIO_SEGUNDO_FATOR'));

ALTER TABLE auditoria_login DROP CONSTRAINT ck_auditoria_login_resultado;
ALTER TABLE auditoria_login ADD CONSTRAINT ck_auditoria_login_resultado
    CHECK (resultado IN ('SUCESSO', 'EMAIL_DESCONHECIDO', 'SENHA_INVALIDA', 'USUARIO_INATIVO',
                         'EMAIL_NAO_CONFIRMADO', 'BLOQUEADO', 'SEGUNDO_FATOR_PENDENTE',
                         'SEGUNDO_FATOR_INVALIDO', 'SEGUNDO_FATOR_CADASTRADO', 'CODIGO_RECUPERACAO'));

-- Encerrar as sessões de um usuário pelo ADM: access tokens emitidos antes disso deixam de valer
ALTER TABLE usuarios ADD COLUMN sessoes_encerradas_em TIMESTAMPTZ;
