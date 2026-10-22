-- Registro de acesso aos vídeos (quem abriu a playlist, de onde e quando). Retenção limitada (LGPD):
-- o worker apaga diariamente o que passou de app.video.retencao-log-acesso (6 meses, o prazo do
-- Marco Civil da Internet para registros de acesso a aplicações).
CREATE TABLE acessos_video (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    aula_id      BIGINT       NOT NULL REFERENCES aulas (id) ON DELETE CASCADE,
    ip           VARCHAR(45)  NOT NULL,
    user_agent   VARCHAR(255),
    acessado_em  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_acessos_video_acessado_em ON acessos_video (acessado_em);
CREATE INDEX ix_acessos_video_usuario ON acessos_video (usuario_id, acessado_em);
