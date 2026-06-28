SET TIME ZONE 'UTC';

CREATE TABLE plans(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    price_amount_cents BIGINT NOT NULL,
    price_currency VARCHAR(255) NOT NULL,
    billing_cycle VARCHAR(255) NOT NULL DEFAULT 'MONTHLY',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
)