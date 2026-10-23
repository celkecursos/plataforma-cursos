-- Foto de perfil (JPEG sem metadados no bucket)
ALTER TABLE usuarios ADD COLUMN foto_chave VARCHAR(200);

-- Termos de uso e política de privacidade versionados: a versão vigente de cada tipo é a maior.
-- Uma versão publicada não muda; mudar o texto é publicar outra versão.
CREATE TABLE termos (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo               VARCHAR(30)  NOT NULL,
    versao             INT          NOT NULL,
    conteudo_markdown  TEXT         NOT NULL,
    conteudo_html      TEXT         NOT NULL,
    publicado_por      BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    publicado_em       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_termos_tipo CHECK (tipo IN ('TERMOS_USO', 'POLITICA_PRIVACIDADE')),
    CONSTRAINT ck_termos_versao CHECK (versao > 0),
    CONSTRAINT uq_termos_tipo_versao UNIQUE (tipo, versao)
);

-- Aceite de uma versão por um usuário, com de onde veio (prova do consentimento)
CREATE TABLE aceites_termos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    termo_id    BIGINT       NOT NULL REFERENCES termos (id) ON DELETE RESTRICT,
    ip          VARCHAR(45)  NOT NULL,
    user_agent  VARCHAR(255),
    aceito_em   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_aceites_termos UNIQUE (usuario_id, termo_id)
);
