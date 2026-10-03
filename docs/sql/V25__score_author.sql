-- V25: every score records who wrote it.
--
-- RF 45 and RNF 5 require each mark to keep its author, but only `attendance` did. A homeroom
-- reassignment moves the class groups to the new teacher, so two teachers legitimately write the
-- same class group within one year, and nothing said which of them wrote which score.
--
--   * assessment_scores.created_by  who typed the score first. Never changes afterwards.
--   * assessment_scores.updated_by  who last wrote it (equals created_by on the first write).
--   * academic_scores.updated_by    who last touched the consolidation row. The row already carried
--                                   created_by; it is rewritten on every score write or delete in
--                                   its class group and trimester, so it names whoever did.
--
-- All three are nullable and reference users with ON DELETE SET NULL, like the four audit columns
-- of `attendance`: removing a user account must not delete or block the marks they wrote.
--
-- NO backfill. Rows that exist today have no known author and nothing in the database can tell
-- them apart, so they keep NULL, which is honest; inventing an author would be false evidence.
-- Writes made by the application from now on fill the columns.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS. Run by hand like every other script in this folder.

ALTER TABLE public.assessment_scores
    ADD COLUMN IF NOT EXISTS created_by uuid REFERENCES public.users(id_user) ON DELETE SET NULL;

ALTER TABLE public.assessment_scores
    ADD COLUMN IF NOT EXISTS updated_by uuid REFERENCES public.users(id_user) ON DELETE SET NULL;

ALTER TABLE public.academic_scores
    ADD COLUMN IF NOT EXISTS updated_by uuid REFERENCES public.users(id_user) ON DELETE SET NULL;

COMMENT ON COLUMN public.assessment_scores.created_by IS
    'Teacher who first typed the score. NULL for rows written before V25 (author never recorded).';
COMMENT ON COLUMN public.assessment_scores.updated_by IS
    'Teacher who last wrote the score. NULL for rows written before V25.';
COMMENT ON COLUMN public.academic_scores.updated_by IS
    'Teacher whose score write or delete last touched this consolidation row. NULL before V25.';
