-- Conta criada por uma compra nasce sem senha: o dono recebe o link de "criar senha"
ALTER TABLE usuarios ALTER COLUMN senha_hash DROP NOT NULL;

-- Meio de pagamento configurado pelo painel. O tipo não tem CHECK: um meio novo entra só com código.
-- Os segredos ficam cifrados (AES-256-GCM, chave PAGAMENTOS_CHAVE do ambiente, IV por gravação).
CREATE TABLE gateways_pagamento (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(30)  NOT NULL,
    nome           VARCHAR(120) NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Campos não secretos da configuração
    configuracao   JSONB        NOT NULL DEFAULT '{}',
    segredos       BYTEA,
    -- Aleatório, entra na URL do webhook e identifica o gateway (não substitui a autenticação dele)
    webhook_token  VARCHAR(64)  NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_gateways_pagamento_webhook_token UNIQUE (webhook_token)
);

-- Um gateway ativo por vez para os novos checkouts; os desativados continuam recebendo notificações
CREATE UNIQUE INDEX uq_gateways_pagamento_ativo ON gateways_pagamento (ativo) WHERE ativo;

-- O que se vende: uma turma por um produto/oferta do meio de pagamento
CREATE TABLE ofertas (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id         BIGINT       NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    gateway_id       BIGINT       NOT NULL REFERENCES gateways_pagamento (id) ON DELETE RESTRICT,
    -- Id do produto e código da oferta no meio de pagamento (como chegam nas notificações)
    produto_externo  VARCHAR(100) NOT NULL,
    oferta_externa   VARCHAR(100) NOT NULL,
    -- Código do produto no link do checkout, quando diferente do id (Hotmart: pay.hotmart.com/<código>)
    codigo_checkout  VARCHAR(100),
    titulo           VARCHAR(120) NOT NULL,
    preco_centavos   BIGINT       NOT NULL,
    moeda            VARCHAR(3)   NOT NULL,
    -- Nulo: acesso sem prazo
    dias_acesso      INT,
    ativa            BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ  NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_ofertas_externa UNIQUE (gateway_id, produto_externo, oferta_externa),
    CONSTRAINT ck_ofertas_preco CHECK (preco_centavos >= 0),
    CONSTRAINT ck_ofertas_dias CHECK (dias_acesso IS NULL OR dias_acesso > 0)
);

CREATE INDEX ix_ofertas_turma ON ofertas (turma_id);

-- Compra de uma oferta. "codigo" é público (vai no checkout e na página de retorno)
CREATE TABLE pedidos (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo             VARCHAR(22)  NOT NULL,
    oferta_id          BIGINT       NOT NULL REFERENCES ofertas (id) ON DELETE RESTRICT,
    gateway_id         BIGINT       NOT NULL REFERENCES gateways_pagamento (id) ON DELETE RESTRICT,
    -- Nulo até a conta do comprador existir
    usuario_id         BIGINT       REFERENCES usuarios (id) ON DELETE RESTRICT,
    email              VARCHAR(254) NOT NULL,
    nome               VARCHAR(120),
    status             VARCHAR(30)  NOT NULL,
    transacao_externa  VARCHAR(100),
    valor_centavos     BIGINT,
    moeda              VARCHAR(3),
    forma_pagamento    VARCHAR(20),
    matricula_id       BIGINT       REFERENCES matriculas (id) ON DELETE RESTRICT,
    -- CHECKOUT: nasceu no botão "Comprar"; EXTERNO: venda que chegou sem o nosso código (afiliado, link direto)
    origem             VARCHAR(20)  NOT NULL,
    -- Quando ocorreu o evento da última mudança de status (eventos fora de ordem não desfazem os mais novos)
    ultimo_evento_em   TIMESTAMPTZ,
    criado_em          TIMESTAMPTZ  NOT NULL,
    atualizado_em      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_pedidos_codigo UNIQUE (codigo),
    CONSTRAINT uq_pedidos_transacao UNIQUE (gateway_id, transacao_externa),
    CONSTRAINT ck_pedidos_status CHECK (status IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'APROVADO', 'CONCLUIDO',
        'EM_DISPUTA', 'ATRASADO', 'REEMBOLSADO', 'CHARGEBACK', 'CANCELADO', 'EXPIRADO')),
    CONSTRAINT ck_pedidos_origem CHECK (origem IN ('CHECKOUT', 'EXTERNO'))
);

CREATE INDEX ix_pedidos_status ON pedidos (status, atualizado_em);
CREATE INDEX ix_pedidos_email ON pedidos (email);

-- Notificação recebida de um meio de pagamento. O único em (gateway, id_externo) é a idempotência.
CREATE TABLE eventos_pagamento (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gateway_id     BIGINT       NOT NULL REFERENCES gateways_pagamento (id) ON DELETE RESTRICT,
    id_externo     VARCHAR(150) NOT NULL,
    tipo_original  VARCHAR(60)  NOT NULL,
    recebido_em    TIMESTAMPTZ  NOT NULL,
    ocorrido_em    TIMESTAMPTZ,
    transacao      VARCHAR(100),
    -- Corpo recebido, com documento e telefone mascarados (apagado depois da retenção)
    payload        JSONB,
    -- Evento normalizado, lido pelo worker (apagado junto com o payload)
    dados          JSONB,
    status         VARCHAR(20)  NOT NULL,
    erro           TEXT,
    pedido_id      BIGINT       REFERENCES pedidos (id) ON DELETE RESTRICT,
    processado_em  TIMESTAMPTZ,
    CONSTRAINT uq_eventos_pagamento_externo UNIQUE (gateway_id, id_externo),
    CONSTRAINT ck_eventos_pagamento_status CHECK (status IN ('RECEBIDO', 'PROCESSADO', 'IGNORADO', 'ERRO'))
);

CREATE INDEX ix_eventos_pagamento_status ON eventos_pagamento (status, recebido_em);
CREATE INDEX ix_eventos_pagamento_pedido ON eventos_pagamento (pedido_id);

-- Linha do tempo do pedido
CREATE TABLE pedido_historico (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id  BIGINT       NOT NULL REFERENCES pedidos (id) ON DELETE RESTRICT,
    de         VARCHAR(30),
    para       VARCHAR(30)  NOT NULL,
    evento_id  BIGINT       REFERENCES eventos_pagamento (id) ON DELETE RESTRICT,
    motivo     VARCHAR(300),
    em         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_pedido_historico_pedido ON pedido_historico (pedido_id, id);

-- Matrícula feita por um pagamento aponta para o pedido; a forma "OUTRO" cobre PayPal, Google Pay...
ALTER TABLE matriculas ADD COLUMN pedido_id BIGINT REFERENCES pedidos (id) ON DELETE RESTRICT;
ALTER TABLE matriculas DROP CONSTRAINT ck_matriculas_forma_pagamento;
ALTER TABLE matriculas ADD CONSTRAINT ck_matriculas_forma_pagamento
    CHECK (forma_pagamento IN ('MANUAL', 'CORTESIA', 'PIX', 'CARTAO', 'BOLETO', 'OUTRO'));
