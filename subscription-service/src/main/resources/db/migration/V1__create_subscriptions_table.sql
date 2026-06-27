SET TIME ZONE 'UTC';

CREATE TABLE subscriptions(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'TRIALING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
