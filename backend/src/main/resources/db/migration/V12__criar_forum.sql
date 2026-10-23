-- Fórum da turma: tópicos (da turma ou de uma aula), respostas e anexos de imagem. O Markdown escrito pelo
-- usuário é guardado junto com o HTML já filtrado (contra XSS) gerado a partir dele.
CREATE TABLE forum_topicos (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id               BIGINT        NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    -- Nulo: tópico geral da turma. Aula excluída: o tópico continua, como tópico da turma
    aula_id                BIGINT        REFERENCES aulas (id) ON DELETE SET NULL,
    autor_id               BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    titulo                 VARCHAR(150)  NOT NULL,
    corpo_markdown         TEXT          NOT NULL,
    corpo_html             TEXT          NOT NULL,
    oculto                 BOOLEAN       NOT NULL DEFAULT FALSE,
    fixado                 BOOLEAN       NOT NULL DEFAULT FALSE,
    respondido_instrutor   BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em              TIMESTAMPTZ   NOT NULL,
    atualizado_em          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_forum_topicos_turma ON forum_topicos (turma_id, fixado DESC, criado_em DESC);
CREATE INDEX ix_forum_topicos_aula ON forum_topicos (aula_id);
CREATE INDEX ix_forum_topicos_autor ON forum_topicos (autor_id, criado_em);

CREATE TABLE forum_respostas (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topico_id       BIGINT       NOT NULL REFERENCES forum_topicos (id) ON DELETE CASCADE,
    autor_id        BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    corpo_markdown  TEXT         NOT NULL,
    corpo_html      TEXT         NOT NULL,
    -- Resposta do instrutor (ADM)
    instrutor       BOOLEAN      NOT NULL DEFAULT FALSE,
    oculto          BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em       TIMESTAMPTZ  NOT NULL,
    atualizado_em   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_forum_respostas_topico ON forum_respostas (topico_id, criado_em);
CREATE INDEX ix_forum_respostas_autor ON forum_respostas (autor_id, criado_em);

-- Imagem anexada a um tópico ou a uma resposta (exatamente um dos dois), já processada (JPEG sem EXIF)
CREATE TABLE forum_anexos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topico_id    BIGINT        REFERENCES forum_topicos (id) ON DELETE CASCADE,
    resposta_id  BIGINT        REFERENCES forum_respostas (id) ON DELETE CASCADE,
    autor_id     BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    chave        VARCHAR(200)  NOT NULL,
    tamanho      BIGINT        NOT NULL,
    criado_em    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_forum_anexos_dono CHECK (num_nonnulls(topico_id, resposta_id) = 1),
    CONSTRAINT uq_forum_anexos_chave UNIQUE (chave)
);

CREATE INDEX ix_forum_anexos_topico ON forum_anexos (topico_id);
CREATE INDEX ix_forum_anexos_resposta ON forum_anexos (resposta_id);
