-- Review/export metadata only. No data rows, no credentials, no DDL or DML.
SELECT table_schema,table_name,column_name,data_type,udt_name,is_nullable,column_default
FROM information_schema.columns
WHERE table_schema='public'
AND table_name IN ('patients','access_grants','medical_records','record_versions','prescription_items','vital_signs','record_attachments')
ORDER BY table_name,ordinal_position;

SELECT c.conrelid::regclass AS table_name,c.conname,c.contype,pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname='public'
AND t.relname IN ('patients','access_grants','medical_records','record_versions','prescription_items','vital_signs','record_attachments')
ORDER BY c.conrelid::regclass::text,c.conname;

SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes
WHERE schemaname='public'
AND tablename IN ('patients','access_grants','medical_records','record_versions','prescription_items','vital_signs','record_attachments')
ORDER BY tablename,indexname;

SELECT n.nspname,t.relname,t.relrowsecurity,t.relforcerowsecurity,
       g.tgname,pg_get_triggerdef(g.oid)xx
FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
LEFT JOIN pg_trigger g ON g.tgrelid=t.oid AND NOT g.tgisinternal
WHERE n.nspname='public'
AND t.relname IN ('patients','access_grants','medical_records','record_versions','prescription_items','vital_signs','record_attachments')
ORDER BY t.relname,g.tgname;
