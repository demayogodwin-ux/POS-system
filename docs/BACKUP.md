# Backing up and restoring the POS database

Everything the POS knows (products, sales, users, settings, phone orders) lives
in your Supabase project's PostgreSQL database.

## Automatic backups

Supabase backs the database up every day by itself. See them under
**Database > Backups** in the Supabase dashboard. The Free plan keeps no
downloadable backups, and paid plans keep 7 days or more, so on the Free plan
also make your own copy as described below.

## Back up yourself (command line)

Needs the PostgreSQL client tools (`pg_dump`, `psql`): install PostgreSQL from
postgresql.org and tick only *Command Line Tools*. Use the same host, user and
password as in `.env` (DB_PASSWORD).

```bat
rem Back up
pg_dump "postgresql://postgres.YOUR_PROJECT_REF:YOUR_PASSWORD@aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require" --schema=public --no-owner --no-privileges -f D:\POS-backups\pos_db-2026-10-30.sql

rem Restore (into an empty project, or after dropping the tables)
psql "postgresql://postgres.YOUR_PROJECT_REF:YOUR_PASSWORD@aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require" -f D:\POS-backups\pos_db-2026-10-30.sql
```

`pg_dump` takes a consistent snapshot without stopping the registers. Keep the
file somewhere other than this PC (USB drive, cloud).

Restoring **replaces** the data with the backup's contents. Any sales made
after the backup will be lost. Close the POS on every register first.

## Old XAMPP backups

`.sql` files made with `mysqldump` from the XAMPP version cannot be loaded into
Supabase directly. Ask for them to be converted, or keep XAMPP to read them.
