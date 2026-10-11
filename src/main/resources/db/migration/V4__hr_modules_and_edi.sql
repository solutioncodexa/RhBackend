ALTER TABLE rhflow.employees ADD COLUMN IF NOT EXISTS establishment_id BIGINT;
ALTER TABLE rhflow.employees ADD COLUMN IF NOT EXISTS bank_name VARCHAR(120);
ALTER TABLE rhflow.employees ADD COLUMN IF NOT EXISTS bank_bic VARCHAR(20);

ALTER TABLE rhflow.attendance ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE rhflow.attendance ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE rhflow.attendance ADD COLUMN IF NOT EXISTS method VARCHAR(20);
ALTER TABLE rhflow.attendance ADD COLUMN IF NOT EXISTS hours NUMERIC(8,2);

ALTER TABLE rhflow.leave_requests ADD COLUMN IF NOT EXISTS approval_step INTEGER DEFAULT 0;
ALTER TABLE rhflow.employee_requests ADD COLUMN IF NOT EXISTS approval_step INTEGER DEFAULT 0;

ALTER TABLE rhflow.job_offers ADD COLUMN IF NOT EXISTS published BOOLEAN DEFAULT FALSE;
ALTER TABLE rhflow.job_offers ADD COLUMN IF NOT EXISTS public_token VARCHAR(80);

ALTER TABLE rhflow.candidates ADD COLUMN IF NOT EXISTS phone VARCHAR(40);
ALTER TABLE rhflow.candidates ADD COLUMN IF NOT EXISTS cv_text TEXT;

ALTER TABLE rhflow.payslips ADD COLUMN IF NOT EXISTS loan_deduction NUMERIC(14,2) DEFAULT 0;

CREATE TABLE IF NOT EXISTS rhflow.hr_records (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    kind VARCHAR(40) NOT NULL,
    employee_id BIGINT,
    related_id BIGINT,
    title VARCHAR(255),
    code VARCHAR(120),
    status VARCHAR(40),
    notes VARCHAR(4000),
    amount NUMERIC(14,2),
    remaining NUMERIC(14,2),
    monthly_amount NUMERIC(14,2),
    start_date DATE,
    end_date DATE,
    start_time TIME,
    end_time TIME,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    radius_meters DOUBLE PRECISION,
    payload VARCHAR(4000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hr_records_tenant_kind ON rhflow.hr_records (tenant_id, kind);
CREATE INDEX IF NOT EXISTS idx_hr_records_employee ON rhflow.hr_records (tenant_id, employee_id);
