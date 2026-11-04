-- V30: a student can be registered before their RUDE, CI, birth date and gender are known.
--
-- The school's "NOMINA DE ESTUDIANTES" spreadsheet lists each student by full name and course and
-- nothing else. Registering from it needs these columns to admit NULL; the staff complete them
-- later, by importing the SIE roster PDF of the course or by editing the student by hand.
--
-- The UNIQUE constraints on rude_code and identity_card stay: PostgreSQL treats NULLs as distinct,
-- so any number of students may lack a code while a code that is present still names one student.
-- gender was already nullable.
--
-- Safe to re-run: DROP NOT NULL on a column that already admits NULL is a no-op.
-- Run it by hand: the API has no migration tool.

ALTER TABLE students ALTER COLUMN rude_code DROP NOT NULL;
ALTER TABLE students ALTER COLUMN identity_card DROP NOT NULL;
ALTER TABLE students ALTER COLUMN birth_date DROP NOT NULL;
