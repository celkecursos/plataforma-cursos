-- Refresh tokens: só o hash SHA-256 é gravado. Cada login abre uma família; cada uso gera um novo
-- token na mesma família e marca o anterior como usado. Um token usado que reaparece revoga a família.
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    familia     UUID        NOT NULL,
    hash        VARCHAR(64) NOT NULL,
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,
    revogado_em TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (hash)
);

CREATE INDEX ix_refresh_tokens_familia ON refresh_tokens (familia);
CREATE INDEX ix_refresh_tokens_usuario ON refresh_tokens (usuario_id);
