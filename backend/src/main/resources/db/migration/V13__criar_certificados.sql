-- Regra do certificado por turma: percentual mínimo de aulas vistas e carga horária (opcional)
ALTER TABLE turmas ADD COLUMN percentual_conclusao INT NOT NULL DEFAULT 100;
ALTER TABLE turmas ADD COLUMN carga_horaria_horas INT;
ALTER TABLE turmas ADD CONSTRAINT ck_turmas_percentual_conclusao CHECK (percentual_conclusao BETWEEN 1 AND 100);
ALTER TABLE turmas ADD CONSTRAINT ck_turmas_carga_horaria CHECK (carga_horaria_horas IS NULL OR carga_horaria_horas > 0);

-- Certificado emitido: um por aluno e turma. Os dados impressos ficam congelados na emissão (mudar o
-- título do curso depois não altera um certificado já entregue); o código é público e valida o documento.
CREATE TABLE certificados (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id            BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    turma_id              BIGINT        NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    codigo                VARCHAR(20)   NOT NULL,
    nome_completo         VARCHAR(150)  NOT NULL,
    curso_titulo          VARCHAR(120)  NOT NULL,
    turma_nome            VARCHAR(120)  NOT NULL,
    carga_horaria_horas   INT,
    percentual_concluido  INT           NOT NULL,
    pdf_chave             VARCHAR(200)  NOT NULL,
    emitido_em            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_certificados_codigo UNIQUE (codigo),
    CONSTRAINT uq_certificados_aluno_turma UNIQUE (usuario_id, turma_id)
);
