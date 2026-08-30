-- V12 — Hold "one adaptation per student" in the database, and drop what V11 left behind.
--
-- Two unrelated-looking changes travel together because both are about the same table's shape
-- after the PDC work settled, and because a hand-run script is worth running once rather than
-- twice.
--
-- 1. The uniqueness the API promises was only ever enforced by a look-before-you-write in
--    AdaptationService: it asks existsByPlanAndStudent and then inserts. That is a check and then
--    an act, so two concurrent creates both read "no row yet", both insert, and the handed-in plan
--    prints the same child twice. Nothing in the database said no.
--
-- 2. V11 reshaped the plan and left three objects that no code reads or writes: the fourth
--    evaluation criterion on the weekly rows, and the per-subject link on the adaptations. The
--    form evaluates on three criteria, and an adaptation answers to a student for the whole plan,
--    not to one subject of it.
--
-- academic_scores.score_deciding is NOT touched: the rest of the system still grades on that
-- dimension. Only the plan's own copy of it goes.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. One adaptation per plan and student
-- ---------------------------------------------------------------------------
-- Run this first and read what it returns. It must come back empty: the constraint below cannot be
-- created while a duplicate pair exists, and the fix is a decision about which row to keep, not
-- something a migration should make on its own.
--
--   SELECT id_curriculum_plan, id_student, COUNT(*)
--   FROM curriculum_adaptations
--   GROUP BY id_curriculum_plan, id_student
--   HAVING COUNT(*) > 1;

ALTER TABLE curriculum_adaptations
    ADD CONSTRAINT uq_adaptation_plan_student UNIQUE (id_curriculum_plan, id_student);

-- ---------------------------------------------------------------------------
-- 2. What V11 left behind
-- ---------------------------------------------------------------------------
-- The official form evaluates on SER, SABER and HACER. DECIDIR was carried over from the old flat
-- plan and is written by nothing.
ALTER TABLE curriculum_plan_entries
    DROP COLUMN criteria_deciding;

-- An adaptation belongs to a student for the whole month. The column was never populated, and its
-- index only ever indexed nulls.
DROP INDEX IF EXISTS idx_adaptation_plan_subject;

ALTER TABLE curriculum_adaptations
    DROP COLUMN id_plan_subject;

COMMIT;
