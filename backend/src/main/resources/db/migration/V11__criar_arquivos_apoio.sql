-- Arquivos de apoio (código-fonte, materiais) de uma aula ou de um módulo: exatamente um dos dois.
-- Aula ou módulo com arquivos não pode ser excluído (409), para não deixar arquivos sem dono no bucket.
CREATE TABLE arquivos_apoio (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aula_id       BIGINT        REFERENCES aulas (id) ON DELETE RESTRICT,
    modulo_id     BIGINT        REFERENCES modulos (id) ON DELETE RESTRICT,
    titulo        VARCHAR(150)  NOT NULL,
    -- Nome sugerido no download
    nome_arquivo  VARCHAR(255)  NOT NULL,
    chave         VARCHAR(200)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    tamanho       BIGINT        NOT NULL,
    criado_em     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_arquivos_apoio_dono CHECK (num_nonnulls(aula_id, modulo_id) = 1),
    CONSTRAINT ck_arquivos_apoio_tamanho CHECK (tamanho > 0),
    CONSTRAINT uq_arquivos_apoio_chave UNIQUE (chave)
);

CREATE INDEX ix_arquivos_apoio_aula ON arquivos_apoio (aula_id);
CREATE INDEX ix_arquivos_apoio_modulo ON arquivos_apoio (modulo_id);
