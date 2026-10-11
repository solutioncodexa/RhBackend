CREATE TABLE IF NOT EXISTS payroll_rule_sets (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    effective_from DATE,
    effective_to DATE,
    smig_monthly NUMERIC(12,2),
    legal_weekly_hours NUMERIC(8,2)
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE,
    plan VARCHAR(20) NOT NULL,
    expires_at DATE,
    max_employees INT,
    max_users INT,
    feature_ollama BOOLEAN,
    feature_whatsapp BOOLEAN,
    active BOOLEAN,
    price_mad NUMERIC(12,2)
);
