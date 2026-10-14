-- Existing migrations stay immutable. Legacy sessions without a credential version
-- are rejected by the request filter; users must sign in again after deployment.
ALTER TABLE spring_session ALTER COLUMN principal_name TYPE VARCHAR(254);
ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 1
 CHECK (credential_version > 0);
ALTER TABLE quotations ADD COLUMN company_name_snapshot VARCHAR(160);
ALTER TABLE quotations ADD COLUMN customer_name_snapshot VARCHAR(160);
ALTER TABLE quotations ADD COLUMN terms VARCHAR(3000);
ALTER TABLE quotations ADD COLUMN public_revoked_at TIMESTAMPTZ;
-- The original names of legacy sent proposals cannot be reconstructed.
-- Preserve the names available at migration time; never publish old internal notes.
UPDATE quotations q SET company_name_snapshot=c.name, customer_name_snapshot=u.name
 FROM companies c, customers u
 WHERE q.company_id=c.id AND q.customer_id=u.id AND q.status<>'RASCUNHO';
ALTER TABLE quotations ADD CONSTRAINT quotations_sent_parties_check CHECK (
 status='RASCUNHO' OR (company_name_snapshot IS NOT NULL AND customer_name_snapshot IS NOT NULL));
ALTER TABLE quotations ADD CONSTRAINT quotations_revocation_check CHECK (
 public_revoked_at IS NULL OR status<>'RASCUNHO');
