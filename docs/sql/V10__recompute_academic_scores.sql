-- V10 — Recompute academic_scores with the two-level average.
--
-- Every consolidated score already in the table was produced by the old single-pass average,
-- which weighed each raw score equally and therefore let a criterion with many activity items
-- outweigh one scored directly. Those rows are only rewritten when a teacher next touches the
-- subject, so until this script runs the gradebook shows the old numbers.
--
-- Run once, with the application stopped, AFTER V9.
-- Take a backup first: this overwrites consolidated scores in place.

-- ============================================================================
-- STEP 1 — Preview. Run this alone and read the result before updating anything.
--          Rows where old and new differ are exactly what the fix changes.
-- ============================================================================

WITH criterion_avg AS (
    SELECT s.id_course_enrollment,
           c.id_class_group,
           c.trimester,
           c.id_criterion,
           c.dimension,
           AVG(s.score) AS criterion_score
    FROM assessment_scores s
    LEFT JOIN assessment_events e ON e.id_assessment_event = s.id_assessment_event
    JOIN evaluation_criteria c ON c.id_criterion = COALESCE(s.id_criterion, e.id_criterion)
    GROUP BY s.id_course_enrollment, c.id_class_group, c.trimester, c.id_criterion, c.dimension
),
dimension_avg AS (
    SELECT id_course_enrollment, id_class_group, trimester, dimension,
           ROUND(AVG(criterion_score), 2) AS dimension_score
    FROM criterion_avg
    GROUP BY id_course_enrollment, id_class_group, trimester, dimension
),
pivoted AS (
    SELECT id_course_enrollment, id_class_group, trimester,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Being'),    0) AS being,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Knowing'),  0) AS knowing,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Doing'),    0) AS doing,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Deciding'), 0) AS deciding
    FROM dimension_avg
    GROUP BY id_course_enrollment, id_class_group, trimester
)
SELECT a.id_academic_score,
       a.trimester,
       a.score_being    AS being_actual,    p.being    AS being_nuevo,
       a.score_knowing  AS knowing_actual,  p.knowing  AS knowing_nuevo,
       a.score_doing    AS doing_actual,    p.doing    AS doing_nuevo,
       a.score_deciding AS deciding_actual, p.deciding AS deciding_nuevo,
       a.total_score    AS total_actual,
       (p.being + p.knowing + p.doing + p.deciding) AS total_nuevo
FROM academic_scores a
JOIN pivoted p
  ON a.id_course_enrollment = p.id_course_enrollment
 AND a.id_class_group       = p.id_class_group
 AND a.trimester            = p.trimester
WHERE (a.score_being, a.score_knowing, a.score_doing, a.score_deciding)
   IS DISTINCT FROM (p.being, p.knowing, p.doing, p.deciding)
ORDER BY a.trimester, a.id_academic_score;

-- ============================================================================
-- STEP 2 — The rewrite. Run only after reading STEP 1.
--
-- If this fails on chk score_doing <= 40 (or any sibling check), do NOT relax the
-- constraint: it means scores were recorded on a 0-100 scale instead of the dimension's.
-- The constraint is catching bad data, which is its job.
-- ============================================================================

BEGIN;

WITH criterion_avg AS (
    SELECT s.id_course_enrollment,
           c.id_class_group,
           c.trimester,
           c.id_criterion,
           c.dimension,
           AVG(s.score) AS criterion_score
    FROM assessment_scores s
    LEFT JOIN assessment_events e ON e.id_assessment_event = s.id_assessment_event
    JOIN evaluation_criteria c ON c.id_criterion = COALESCE(s.id_criterion, e.id_criterion)
    GROUP BY s.id_course_enrollment, c.id_class_group, c.trimester, c.id_criterion, c.dimension
),
dimension_avg AS (
    SELECT id_course_enrollment, id_class_group, trimester, dimension,
           ROUND(AVG(criterion_score), 2) AS dimension_score
    FROM criterion_avg
    GROUP BY id_course_enrollment, id_class_group, trimester, dimension
),
pivoted AS (
    SELECT id_course_enrollment, id_class_group, trimester,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Being'),    0) AS being,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Knowing'),  0) AS knowing,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Doing'),    0) AS doing,
           COALESCE(MAX(dimension_score) FILTER (WHERE dimension = 'Deciding'), 0) AS deciding
    FROM dimension_avg
    GROUP BY id_course_enrollment, id_class_group, trimester
)
UPDATE academic_scores a
SET score_being    = p.being,
    score_knowing  = p.knowing,
    score_doing    = p.doing,
    score_deciding = p.deciding,
    updated_at     = CURRENT_TIMESTAMP
FROM pivoted p
WHERE a.id_course_enrollment = p.id_course_enrollment
  AND a.id_class_group       = p.id_class_group
  AND a.trimester            = p.trimester;

-- A consolidated row whose scores were all deleted must fall back to zero, not keep a stale
-- average: no source rows means no pivoted match above, so it is handled separately here.
UPDATE academic_scores a
SET score_being = 0, score_knowing = 0, score_doing = 0, score_deciding = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM assessment_scores s
    LEFT JOIN assessment_events e ON e.id_assessment_event = s.id_assessment_event
    JOIN evaluation_criteria c ON c.id_criterion = COALESCE(s.id_criterion, e.id_criterion)
    WHERE s.id_course_enrollment = a.id_course_enrollment
      AND c.id_class_group       = a.id_class_group
      AND c.trimester            = a.trimester
);

COMMIT;
