-- Optional read-only checks for a teammate/DB administrator. This agent did not execute them.
SELECT table_schema,table_name,column_name,data_type,is_nullable
FROM information_schema.columns
WHERE table_schema IN ('public','doctor_backend')
ORDER BY table_schema,table_name,ordinal_position;
SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
FROM pg_constraint WHERE connamespace='public'::regnamespace
ORDER BY conrelid::regclass::text,conname;
SELECT email,COUNT(*) FROM public.users GROUP BY email HAVING COUNT(*)>1;
SELECT user_id,COUNT(*) FROM public.doctors GROUP BY user_id HAVING COUNT(*)>1;
