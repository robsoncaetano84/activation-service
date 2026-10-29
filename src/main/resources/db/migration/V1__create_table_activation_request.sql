CREATE TABLE activation_request (
    id              UUID         PRIMARY KEY,
    external_id     VARCHAR(64)  NOT NULL,
    customer_id     VARCHAR(64)  NOT NULL,
    product_code    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_activation_request_external_id UNIQUE (external_id),
    CONSTRAINT ck_activation_request_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_activation_request_status_next_attempt
    ON activation_request (status, next_attempt_at);
	
COMMENT ON COLUMN activation_request.external_id IS 'ID do pedido no sistema';
COMMENT ON COLUMN activation_request.status IS 'PENDING, PROCESSING, COMPLETED ou FAILED';
COMMENT ON COLUMN activation_request.next_attempt_at IS 'Quando podemos tentar enviar de novo';