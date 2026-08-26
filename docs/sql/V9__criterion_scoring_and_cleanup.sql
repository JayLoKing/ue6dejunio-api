-- V9 — Criterion-level scoring, activity grouping, and removal of unused columns.
--
-- Applied manually: the project has no Flyway/Liquibase and runs with
-- spring.jpa.hibernate.ddl-auto=validate, so the schema must already match the entities
-- before the application starts. This file is the record of what was run.
--
-- Order matters. Sections 1-2 are additive and safe to run while the old code is live;
-- section 3 drops columns and must run with the application stopped, together with the
-- deploy that removes those fields from the entities.

-- ============================================================================
-- 1. Activity grouping and direct criterion scoring
-- ============================================================================

-- NULL  = criterion scored directly.
-- SET   = criterion fed by an activity, e.g. "Revision de Cuadernos".
ALTER TABLE evaluation_criteria
    ADD COLUMN IF NOT EXISTS activity_name varchar(150);

ALTER TABLE assessment_scores
    ADD COLUMN IF NOT EXISTS id_criterion uuid
    REFERENCES evaluation_criteria(id_criterion) ON DELETE CASCADE;

-- A direct score has no activity item to point at.
ALTER TABLE assessment_scores
    ALTER COLUMN id_assessment_event DROP NOT NULL;

ALTER TABLE assessment_scores
    ADD CONSTRAINT chk_score_target CHECK (
        (id_criterion IS NOT NULL AND id_assessment_event IS NULL)
     OR (id_criterion IS NULL     AND id_assessment_event IS NOT NULL)
    );

-- ============================================================================
-- 2. Uniqueness and indexes
-- ============================================================================

-- With id_assessment_event nullable, the old two-column UNIQUE stops protecting direct
-- scores: PostgreSQL treats every NULL as distinct, so a student could accumulate any
-- number of duplicate scores on the same criterion. Two partial unique indexes replace it,
-- the same shape already used by uq_att_daily / uq_att_session on attendance.
ALTER TABLE assessment_scores
    DROP CONSTRAINT IF EXISTS uq_assessment_score;

CREATE UNIQUE INDEX IF NOT EXISTS uq_score_event
    ON assessment_scores (id_course_enrollment, id_assessment_event)
    WHERE id_assessment_event IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_score_criterion
    ON assessment_scores (id_course_enrollment, id_criterion)
    WHERE id_criterion IS NOT NULL;

-- PostgreSQL does not index foreign keys automatically, and consolidation joins these
-- three tables on every score the teacher records.
CREATE INDEX IF NOT EXISTS ix_scores_enrollment
    ON assessment_scores (id_course_enrollment);
CREATE INDEX IF NOT EXISTS ix_events_criterion
    ON assessment_events (id_criterion);
CREATE INDEX IF NOT EXISTS ix_criteria_group_trimester
    ON evaluation_criteria (id_class_group, trimester);

-- ============================================================================
-- 3. Unused columns — DESTRUCTIVE, run with the application stopped
-- ============================================================================

-- digital_signature_hash: never read and never written anywhere in the codebase.
-- max_score: written and returned, but no validation ever used it; the ceiling comes from
--   the dimension (Being 10, Knowing 45, Doing 40, Deciding 5), never from the item.
-- description: persisted and returned, consumed by nothing.
ALTER TABLE assessment_scores  DROP COLUMN IF EXISTS digital_signature_hash;
ALTER TABLE academic_scores    DROP COLUMN IF EXISTS digital_signature_hash;
ALTER TABLE assessment_events  DROP COLUMN IF EXISTS description;
ALTER TABLE assessment_events  DROP COLUMN IF EXISTS max_score;
