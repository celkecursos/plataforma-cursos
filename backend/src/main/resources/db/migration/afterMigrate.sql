-- Depois de cada migração (callback do Flyway): o usuário da aplicação (API e worker) só lê e grava
-- dados. Não cria, altera nem apaga tabelas, e não mexe no histórico das migrations. Sem usuário
-- informado (desenvolvimento), a aplicação usa o próprio dono do schema e nada é feito.
DO $$
DECLARE
    usuario TEXT := '${usuario_app}';
    senha   TEXT := '${senha_app}';
BEGIN
    IF usuario = '' OR usuario = current_user THEN
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = usuario) THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', usuario, senha);
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', usuario, senha);
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), usuario);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', usuario);
    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', usuario);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', usuario);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', usuario);
    EXECUTE format('REVOKE ALL ON flyway_schema_history FROM %I', usuario);
END
$$;
