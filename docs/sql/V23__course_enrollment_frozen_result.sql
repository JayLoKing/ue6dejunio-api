-- V23: the frozen result of an enrollment, written when its academic year is closed.
--
-- The annual result was never stored: every report recomputes it from academic_scores, so editing
-- an old grade silently rewrote history. Closing a year now freezes it on the enrollment:
--
--   * `final_average` the annual average the libreta prints (2 decimals), for effective
--                     enrollments with at least one score. NULL otherwise.
--   * `final_result`  APPROVED when that average rounded HALF_UP to a whole number is >= 51,
--                     FAILED otherwise. NULL for withdrawn enrollments and for enrollments with
--                     no scores at all (no data is not a failure).
--   * `closed_at`     when the year closed, on every enrollment of the year (withdrawn included).
--                     NULL while the year is open; reopening the year clears it and keeps the
--                     frozen average and result until the next close overwrites them.
--
-- Idempotent: IF NOT EXISTS on every column. The CHECK is added only when missing. Run by hand
-- like every other script in this folder.

ALTER TABLE public.course_enrollments
    ADD COLUMN IF NOT EXISTS final_average numeric(5,2) NULL;

ALTER TABLE public.course_enrollments
    ADD COLUMN IF NOT EXISTS final_result varchar(10) NULL;

ALTER TABLE public.course_enrollments
    ADD COLUMN IF NOT EXISTS closed_at timestamp NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_course_enrollments_final_result'
    ) THEN
        ALTER TABLE public.course_enrollments
            ADD CONSTRAINT chk_course_enrollments_final_result
            CHECK (final_result IN ('APPROVED', 'FAILED'));
    END IF;
END $$;

COMMENT ON COLUMN public.course_enrollments.final_average IS
    'Annual average frozen when the year was closed; NULL when never closed or nothing graded.';
COMMENT ON COLUMN public.course_enrollments.final_result IS
    'APPROVED or FAILED, frozen when the year was closed. NULL for withdrawn or ungraded.';
COMMENT ON COLUMN public.course_enrollments.closed_at IS
    'When the enrollment''s year was closed; NULL while the year is open.';
