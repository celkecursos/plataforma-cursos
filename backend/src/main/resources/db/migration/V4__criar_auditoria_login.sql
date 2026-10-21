-- Toda tentativa de login: quem tentou, de onde e o resultado
CREATE TABLE auditoria_login (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,
    usuario_id  BIGINT       REFERENCES usuarios (id) ON DELETE SET NULL,
    resultado   VARCHAR(30)  NOT NULL,
    ip          VARCHAR(45)  NOT NULL,
    user_agent  VARCHAR(255),
    criado_em   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_auditoria_login_resultado
        CHECK (resultado IN ('SUCESSO', 'EMAIL_DESCONHECIDO', 'SENHA_INVALIDA', 'USUARIO_INATIVO', 'BLOQUEADO'))
);

CREATE INDEX ix_auditoria_login_email ON auditoria_login (email, criado_em);
CREATE INDEX ix_auditoria_login_usuario ON auditoria_login (usuario_id);
