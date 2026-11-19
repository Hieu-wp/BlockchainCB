-- Metadata only. No patient/doctor rows, passwords, tokens, schema changes or migrations.
-- Run against the agreed development/test project using an authorized connection.
BEGIN TRANSACTION READ ONLY;

-- Presence does not by itself prove column/FK correctness or that all modules use this project.
WITH expected(schema_name, table_name) AS (
  VALUES
    ('public','users'), ('public','doctors'), ('public','patients'),
    ('public','specialties'), ('public','access_grants'), ('public','medical_records'),
    ('public','record_versions'), ('public','prescription_items'), ('public','vital_signs'),
    ('public','record_attachments'), ('public','admin_audit_logs'),
    ('doctor_backend','patient_links'), ('doctor_backend','access_grants'),
    ('doctor_backend','medical_records'), ('doctor_backend','record_versions'),
    ('doctor_backend','attachments'), ('doctor_backend','version_attachments'),
    ('doctor_backend','audit_logs'), ('doctor_backend','wallet_bindings'),
    ('doctor_backend','wallet_challenges'), ('doctor_backend','record_anchors'),
    ('doctor_backend','anchor_attempts')
)
SELECT e.schema_name, e.table_name,
       EXISTS (SELECT 1 FROM information_schema.tables t
               WHERE t.table_schema=e.schema_name AND t.table_name=e.table_name
                 AND t.table_type='BASE TABLE') AS visible_to_current_db_role
FROM expected e ORDER BY e.schema_name,e.table_name;

SELECT table_schema,table_name,column_name,data_type,udt_name,is_nullable,
       column_default,is_identity,character_maximum_length,numeric_precision,numeric_scale
FROM information_schema.columns
WHERE table_schema IN ('public','doctor_backend')
  AND table_name IN ('users','doctors','patients','specialties','access_grants','medical_records',
    'record_versions','prescription_items','vital_signs','record_attachments','admin_audit_logs',
    'patient_links','attachments','version_attachments','audit_logs','wallet_bindings',
    'wallet_challenges','record_anchors','anchor_attempts')
ORDER BY table_schema,table_name,ordinal_position;

SELECT n.nspname AS schema_name,t.relname AS table_name,c.conname AS constraint_name,
       c.contype AS constraint_type,pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname IN ('public','doctor_backend')
ORDER BY n.nspname,t.relname,c.conname;

-- Includes unique indexes which may be omitted from a context-only CREATE TABLE export.
SELECT schemaname,tablename,indexname,indexdef
FROM pg_indexes
WHERE schemaname IN ('public','doctor_backend')
ORDER BY schemaname,tablename,indexname;

SELECT n.nspname AS schema_name,t.relname AS table_name,
       t.relrowsecurity AS rls_enabled,t.relforcerowsecurity AS force_rls
FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname IN ('public','doctor_backend') AND t.relkind='r'
ORDER BY n.nspname,t.relname;

SELECT schemaname,tablename,policyname,roles,cmd,qual,with_check
FROM pg_policies
WHERE schemaname IN ('public','doctor_backend')
ORDER BY schemaname,tablename,policyname;

ROLLBACK;
