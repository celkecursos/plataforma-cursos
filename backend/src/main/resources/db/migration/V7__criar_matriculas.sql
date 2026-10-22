-- Matrícula de um usuário numa turma. "Vencida" não é status: sai das datas comparadas com o relógio.
CREATE TABLE matriculas (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    turma_id         BIGINT       NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    inicio           TIMESTAMPTZ  NOT NULL,
    -- Nulo: acesso sem prazo
    fim              TIMESTAMPTZ,
    status           VARCHAR(20)  NOT NULL,
    forma_pagamento  VARCHAR(20)  NOT NULL,
    -- Identificador da transação no meio de pagamento (webhook futuro)
    transacao        VARCHAR(100),
    arquivada_em     TIMESTAMPTZ,
    cancelada_em     TIMESTAMPTZ,
    criado_em        TIMESTAMPTZ  NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_matriculas_periodo CHECK (fim IS NULL OR fim > inicio),
    CONSTRAINT ck_matriculas_status CHECK (status IN ('ATIVA', 'CANCELADA')),
    CONSTRAINT ck_matriculas_forma_pagamento
        CHECK (forma_pagamento IN ('MANUAL', 'CORTESIA', 'PIX', 'CARTAO', 'BOLETO'))
);

-- Uma matrícula ativa por aluno e turma; canceladas ficam no histórico
CREATE UNIQUE INDEX uq_matriculas_ativa ON matriculas (usuario_id, turma_id) WHERE status = 'ATIVA';
-- A mesma transação não gera duas matrículas
CREATE UNIQUE INDEX uq_matriculas_transacao ON matriculas (forma_pagamento, transacao) WHERE transacao IS NOT NULL;
CREATE INDEX ix_matriculas_turma ON matriculas (turma_id);
