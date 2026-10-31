-- Curso → turmas → módulos → aulas. Exclusão só de item vazio (ON DELETE RESTRICT): o que tem
-- conteúdo é arquivado ou encerrado pelo status.
CREATE TABLE cursos (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo        VARCHAR(120) NOT NULL,
    slug          VARCHAR(80)  NOT NULL,
    resumo        VARCHAR(300) NOT NULL,
    descricao     TEXT,
    status        VARCHAR(20)  NOT NULL,
    capa_chave    VARCHAR(200),
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_cursos_slug UNIQUE (slug),
    CONSTRAINT ck_cursos_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_cursos_status CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO'))
);

CREATE TABLE turmas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    curso_id      BIGINT       NOT NULL REFERENCES cursos (id) ON DELETE RESTRICT,
    nome          VARCHAR(120) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_turmas_status CHECK (status IN ('PREPARACAO', 'ATIVA', 'ENCERRADA'))
);
CREATE INDEX ix_turmas_curso ON turmas (curso_id);

-- A ordem é única por turma; a restrição é conferida só no commit, para a reordenação trocar todas
-- as posições na mesma transação.
CREATE TABLE modulos (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id      BIGINT       NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    titulo        VARCHAR(150) NOT NULL,
    ordem         INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_modulos_turma_ordem UNIQUE (turma_id, ordem) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_modulos_ordem CHECK (ordem > 0),
    CONSTRAINT ck_modulos_status CHECK (status IN ('ATIVO', 'INATIVO'))
);

CREATE TABLE aulas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    modulo_id     BIGINT       NOT NULL REFERENCES modulos (id) ON DELETE RESTRICT,
    titulo        VARCHAR(150) NOT NULL,
    descricao     TEXT,
    ordem         INT          NOT NULL,
    gratuita      BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_aulas_modulo_ordem UNIQUE (modulo_id, ordem) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_aulas_ordem CHECK (ordem > 0),
    CONSTRAINT ck_aulas_status CHECK (status IN ('ATIVO', 'INATIVO'))
);
