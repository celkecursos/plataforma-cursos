-- Vídeo de uma aula. O original vai para uploads/ e, depois de convertido em HLS pelo worker, é apagado;
-- o HLS publicado fica em videos/<aula>/<job>/. O status descreve o último envio: um vídeo pronto continua
-- publicado (playlist_chave) enquanto um envio novo é processado.
CREATE TABLE videos (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aula_id                BIGINT        NOT NULL REFERENCES aulas (id) ON DELETE RESTRICT,
    status                 VARCHAR(20)   NOT NULL,
    -- Envio em andamento ou aguardando processamento
    upload_chave           VARCHAR(200),
    upload_id              VARCHAR(200),
    nome_original          VARCHAR(255),
    tamanho_original       BIGINT,
    -- Resultado da última conversão bem-sucedida
    playlist_chave         VARCHAR(200),
    thumbnail_chave        VARCHAR(200),
    tamanho_final          BIGINT,
    duracao_segundos       INT,
    largura                INT,
    altura                 INT,
    variantes              VARCHAR(50),
    tempo_processamento_ms BIGINT,
    processado_em          TIMESTAMPTZ,
    erro                   TEXT,
    criado_em              TIMESTAMPTZ   NOT NULL,
    atualizado_em          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_videos_aula UNIQUE (aula_id),
    CONSTRAINT ck_videos_status
        CHECK (status IN ('AGUARDANDO_ENVIO', 'PENDENTE', 'PROCESSANDO', 'PRONTO', 'ERRO')),
    CONSTRAINT ck_videos_tamanhos CHECK (tamanho_original IS NULL OR tamanho_original > 0)
);
