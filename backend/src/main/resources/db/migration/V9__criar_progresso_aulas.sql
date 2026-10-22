-- Progresso do aluno em cada aula: onde parou no vídeo, se já viu e se marcou para revisar.
-- Aula excluída leva o progresso junto (é dado derivado; não deve impedir a exclusão).
CREATE TABLE progresso_aulas (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    aula_id           BIGINT       NOT NULL REFERENCES aulas (id) ON DELETE CASCADE,
    posicao_segundos  INT          NOT NULL DEFAULT 0,
    vista             BOOLEAN      NOT NULL DEFAULT FALSE,
    vista_em          TIMESTAMPTZ,
    revisar           BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em         TIMESTAMPTZ  NOT NULL,
    atualizado_em     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_progresso_aulas UNIQUE (usuario_id, aula_id),
    CONSTRAINT ck_progresso_aulas_posicao CHECK (posicao_segundos >= 0)
);

CREATE INDEX ix_progresso_aulas_aula ON progresso_aulas (aula_id);
