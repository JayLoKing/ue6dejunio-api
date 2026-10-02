-- V22: which academic year is active, and whether it is closed.
--
-- Until now "the current year" was whatever row held max(year), in three places that each
-- re-derived it. Creating 2027 while 2026 was still running therefore moved the whole system to
-- 2027, course creation included. The Director must be the one who says which year is active, so
-- the fact moves into the data:
--
--   * `active`    exactly one row at a time. A partial unique index on the flag makes a second
--                 active row impossible for any writer, not just for the application.
--   * `closed_at` when the year was closed; NULL while it is open. Kept apart from `active` on
--                 purpose: a year can be active and still open, or closed and no longer active.
--                 Nothing writes it yet.
--
-- Seed: the row with the highest year becomes the active one, which is exactly what the system
-- treated as current before this script, so nothing moves on deploy. Only when no row is active,
-- so a second run never overrides a choice the Director has made since.
--
-- Idempotent: IF NOT EXISTS on every object, and the seed is guarded. Run by hand like every
-- other script in this folder. The key column is `id_academic_year` (V2 called it `id`; the live
-- schema and the entity use the longer name).

ALTER TABLE public.academic_years
    ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT false;

ALTER TABLE public.academic_years
    ADD COLUMN IF NOT EXISTS closed_at timestamptz NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_academic_years_single_active
    ON public.academic_years (active)
    WHERE active;

UPDATE public.academic_years
   SET active = true
 WHERE year = (SELECT max(year) FROM public.academic_years)
   AND NOT EXISTS (SELECT 1 FROM public.academic_years WHERE active);

COMMENT ON COLUMN public.academic_years.active IS
    'The year the system works in when a request names none. At most one row, enforced by '
    'ux_academic_years_single_active. Set by the Director, never by creating a year.';
COMMENT ON COLUMN public.academic_years.closed_at IS
    'When the year was closed; NULL while it is open. Independent of `active`.';
