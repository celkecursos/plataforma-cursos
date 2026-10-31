-- Trilha de auditoria: cada inclusão, alteração e exclusão feita pela aplicação, com quem fez, de onde e
-- o antes e o depois dos campos (dados sensíveis mascarados). Sem chave estrangeira: o registro não
-- pode impedir nada nem depender de outra linha continuar existindo.
CREATE TABLE auditoria (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Nulo: ação sem usuário logado (cadastro, worker, agendamento)
    usuario_id   BIGINT,
    operacao     VARCHAR(20)  NOT NULL,
    entidade     VARCHAR(60)  NOT NULL,
    entidade_id  VARCHAR(64),
    antes        JSONB,
    depois       JSONB,
    -- Rota da requisição ("PUT /api/adm/cursos/{id}") ou o job/agendamento que fez a mudança
    origem       VARCHAR(200) NOT NULL,
    ip           VARCHAR(45),
    user_agent   VARCHAR(255),
    request_id   VARCHAR(64),
    em           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_auditoria_operacao CHECK (operacao IN ('INCLUSAO', 'ALTERACAO', 'EXCLUSAO'))
);

CREATE INDEX ix_auditoria_em ON auditoria (em DESC, id DESC);
CREATE INDEX ix_auditoria_entidade ON auditoria (entidade, entidade_id, em DESC);
CREATE INDEX ix_auditoria_usuario ON auditoria (usuario_id, em DESC);

-- Só inclusão: alterar nunca; excluir só pela retenção, que liga app.auditoria_retencao na transação
CREATE FUNCTION auditoria_somente_inclusao() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.auditoria_retencao', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'A auditoria só aceita inclusão (% recusado).', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_auditoria_somente_inclusao
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_somente_inclusao();
