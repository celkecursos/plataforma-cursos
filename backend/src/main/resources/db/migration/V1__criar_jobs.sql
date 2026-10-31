-- Fila de processamento em segundo plano (e-mails, transcodificação de vídeo...)
CREATE TABLE jobs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    tentativas     INT          NOT NULL DEFAULT 0,
    max_tentativas INT          NOT NULL,
    disponivel_em  TIMESTAMPTZ  NOT NULL,
    reservado_ate  TIMESTAMPTZ,
    ultimo_erro    TEXT,
    criado_em      TIMESTAMPTZ  NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_jobs_status CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'CONCLUIDO', 'ERRO')),
    CONSTRAINT ck_jobs_tentativas CHECK (tentativas >= 0 AND max_tentativas > 0)
);

-- Só os jobs que ainda podem ser reservados entram no índice
CREATE INDEX ix_jobs_reservaveis ON jobs (tipo, disponivel_em)
    WHERE status IN ('PENDENTE', 'PROCESSANDO');
