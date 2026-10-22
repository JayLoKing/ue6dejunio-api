-- V29: single-column indexes on foreign keys that production lacks.
--
-- PostgreSQL never indexes a referencing column by itself. The development database got these
-- indexes from the original hand-written schema; production was built from
-- docker/postgres/schema.sql, which did not carry them. Without them, joins on these columns and
-- the checks a cascade runs on the referencing table scan the whole table. At the school's volume
-- that costs nothing today, and it grows every year the data is kept.
--
-- None of them is redundant: no existing index or UNIQUE constraint leads with the column.
-- attendance.id_course_enrollment leads uq_att_daily and uq_att_session, but both are partial
-- indexes, and a plain equality on the column does not imply their predicate.
--
-- IF NOT EXISTS makes the script safe on a database that already has them (the development one).
-- Run it by hand: the API has no migration tool.

CREATE INDEX IF NOT EXISTS idx_ascore_cg ON academic_scores (id_class_group);
CREATE INDEX IF NOT EXISTS idx_asscore_event ON assessment_scores (id_assessment_event);
CREATE INDEX IF NOT EXISTS idx_att_ce ON attendance (id_course_enrollment);
CREATE INDEX IF NOT EXISTS idx_att_cg ON attendance (id_class_group);
CREATE INDEX IF NOT EXISTS idx_cg_subject ON class_groups (id_subject);
CREATE INDEX IF NOT EXISTS idx_cg_teacher ON class_groups (id_teacher);
CREATE INDEX IF NOT EXISTS idx_ce_course ON course_enrollments (id_course);
CREATE INDEX IF NOT EXISTS idx_course_year ON courses (id_academic_year);
CREATE INDEX IF NOT EXISTS idx_plan_entry_subject ON curriculum_plan_entries (id_plan_subject);
CREATE INDEX IF NOT EXISTS idx_progress_plan ON curriculum_plan_progress (id_curriculum_plan);
CREATE INDEX IF NOT EXISTS idx_plan_subject_cg ON curriculum_plan_subjects (id_class_group);
CREATE INDEX IF NOT EXISTS idx_plan_source ON curriculum_plans (source_plan_id);
