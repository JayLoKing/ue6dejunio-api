-- V20: one index, for the foreign key that has none.
--
-- `pedagogical_report_failures` carries two foreign keys and one UNIQUE constraint over both of
-- them, in that order: (id_pedagogical_report, id_course_enrollment). V17 argued — correctly —
-- that this makes a second index on `id_pedagogical_report` pointless, because the constraint's
-- btree already leads with that column and serves the only read the screen makes.
--
-- The same argument does not reach the second column. A composite btree is only usable for a
-- lookup that supplies its leading column, so `id_course_enrollment` on its own has no index at
-- all, and V17 never said why it did not need one. This corrects that omission.
--
-- WHAT ACTUALLY PAYS FOR IT, and it is not a screen:
-- the FK declares ON DELETE CASCADE from `course_enrollments`. Postgres does not index the
-- referencing side of a foreign key for you, so every delete of a course enrollment — and every
-- update of its primary key — makes the database prove no child row points at it, and without an
-- index that proof is a sequential scan of this whole table. The table is small today, which is
-- exactly why this is cheap to add now and annoying to diagnose later: the scan does not fail, it
-- just quietly grows with the gestiones.
--
-- Not UNIQUE: a single enrolment legitimately appears in as many rows as it has reports, which is
-- one per trimester. Uniqueness across the pair is already the constraint above; this is only a
-- lookup path.
--
-- Idempotent: CREATE INDEX IF NOT EXISTS, so a database that already received this script is
-- unchanged by a second run. Run by hand like every other script in this folder — the API has no
-- migration tool.

CREATE INDEX IF NOT EXISTS ix_pedagogical_report_failure_enrollment
    ON public.pedagogical_report_failures (id_course_enrollment);

COMMENT ON INDEX public.ix_pedagogical_report_failure_enrollment IS
    'Serves the ON DELETE CASCADE from course_enrollments, which would otherwise scan the whole '
    'table on every enrollment delete. The UNIQUE constraint cannot: it leads with the report.';
