-- V24: one homeroom teacher per course per academic year, enforced by the database.
--
-- The service already refuses to make a teacher homeroom of two courses in one gestion, but
-- nothing behind it did: `id_homeroom_teacher` carried no constraint, and POST /courses never
-- applied the rule at all. A partial unique index closes the door for every writer, not just the
-- application:
--
--   * (id_homeroom_teacher, id_academic_year) is unique among ACTIVE courses with a teacher.
--   * An inactive course may keep its teacher, so a deactivated course never blocks anyone.
--   * Courses with no homeroom teacher are unlimited (NULL is excluded from the index).
--   * The same teacher may run one course in each gestion.
--
-- Existing duplicates are NOT cleaned up here. If any exist the script aborts before touching
-- anything and lists them (teacher, year, course ids); decide by hand which course keeps the
-- teacher, fix it, and run the script again. Deleting or reassigning on the Director's behalf
-- would be a decision this script has no right to make.
--
-- Idempotent: the check finds nothing once the index is in place, and the index is IF NOT EXISTS.
-- Run by hand like every other script in this folder.

DO $$
DECLARE
    duplicates text;
BEGIN
    SELECT string_agg(
               format('teacher %s, academic year id %s: courses %s',
                      id_homeroom_teacher, id_academic_year, course_ids),
               E'\n' ORDER BY id_academic_year, id_homeroom_teacher)
      INTO duplicates
      FROM (
            SELECT id_homeroom_teacher,
                   id_academic_year,
                   string_agg(id_course::text, ', ' ORDER BY id_course) AS course_ids
              FROM public.courses
             WHERE is_active
               AND id_homeroom_teacher IS NOT NULL
             GROUP BY id_homeroom_teacher, id_academic_year
            HAVING count(*) > 1
           ) d;

    IF duplicates IS NOT NULL THEN
        RAISE EXCEPTION
            E'V24 aborted: a teacher is homeroom of more than one active course in the same academic year.\nFix these by hand (reassign or deactivate all but one), then run V24 again:\n%',
            duplicates;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_courses_homeroom_per_year
    ON public.courses (id_homeroom_teacher, id_academic_year)
    WHERE is_active AND id_homeroom_teacher IS NOT NULL;

COMMENT ON INDEX public.ux_courses_homeroom_per_year IS
    'One active course per homeroom teacher per academic year. Inactive courses and courses '
    'without a homeroom teacher are exempt. Swaps must vacate one side first.';
