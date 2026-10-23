CREATE TABLE usuarios (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome          VARCHAR(120) NOT NULL,
    -- Gravado sempre em minúsculas, sem espaços nas pontas
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(255) NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email_minusculo CHECK (email = LOWER(email))
);

CREATE TABLE usuario_papeis (
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    papel      VARCHAR(20) NOT NULL,
    PRIMARY KEY (usuario_id, papel),
    CONSTRAINT ck_usuario_papeis_papel CHECK (papel IN ('ALUNO', 'ADMIN'))
);
