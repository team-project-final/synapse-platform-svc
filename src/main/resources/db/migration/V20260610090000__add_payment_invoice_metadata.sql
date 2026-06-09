ALTER TABLE payment_history
    ADD COLUMN stripe_invoice_id VARCHAR(255),
    ADD COLUMN invoice_url TEXT,
    ADD COLUMN invoice_pdf_url TEXT;

CREATE UNIQUE INDEX uq_payment_history_stripe_invoice_id
    ON payment_history(stripe_invoice_id)
    WHERE stripe_invoice_id IS NOT NULL;

CREATE INDEX idx_payment_history_tenant_paid_at
    ON payment_history(tenant_id, paid_at DESC, created_at DESC);
