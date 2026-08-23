-- Attendance audit trail.
--
-- Use case 2.53 requires every attendance record to keep its author, date and time; use case 2.54
-- requires the same for a correction. Both write paths (/api/attendance/daily and
-- /api/attendance/session) are upserts over the partial unique indexes uq_att_daily and
-- uq_att_session, so an edit reuses the existing row: created_* are mapped updatable=false in
-- AttendanceEntity to preserve the first author, while updated_* move on every correction.
--
-- Additive and safe on a loaded table: PostgreSQL 11+ does not rewrite the table for an
-- ADD COLUMN with a non-volatile default. Rows written before this migration keep NULL authors,
-- which is honest — their author was never recorded.
--
-- Note: this folder documents the schema, it is not a Flyway/Liquibase source. The project runs
-- spring.jpa.hibernate.ddl-auto=validate, so the DDL is applied by hand and mirrored in
-- src/test/resources/schema-it.sql for the integration tests.

ALTER TABLE attendance
    ADD COLUMN created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    ADD COLUMN updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    ADD COLUMN created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at timestamp DEFAULT CURRENT_TIMESTAMP;
