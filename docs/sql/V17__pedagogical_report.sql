-- V17: the two tables the informe pedagógico stands on.
--
-- Every other report in this system is derived: the centralizador, the libreta, the cuadro de honor
-- and the risk list are all queries over marks somebody already entered. This one is not. Sections
-- II (LOGROS Y DIFICULTADES) and the "Acciones, estrategias y/o adaptaciones curriculares" column of
-- section IV are prose the teacher writes, and there is nowhere in this schema they could be read
-- from. Without these two tables the report cannot exist at all.
--
-- What is deliberately NOT stored here:
--   * Section I (DATOS REFERENCIALES) — school, district, department, gestión, level, year of
--     schooling, parallels, teacher. All of it already lives in `institution`, `courses`, `grades`,
--     `parallels`, `academic_years` and `users`. A copy would be a second version of the school's
--     own name, free to drift from the one the libreta prints.
--   * Section III (ESTADÍSTICA DE APROBADOS Y REPROBADOS, with its V/M/T/% breakdown) — derived
--     from `course_enrollments.status`, `students.gender` and the marks. Storing a count is storing
--     a number that stops being true the moment a mark is corrected.
--   * Section IV's ÁREAS REPROBADAS and CALIFICACIÓN columns — derived from `academic_scores` at
--     render time. The sheet shows several areas and several marks in one cell for one student;
--     that is a rendering shape, not a stored one.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS throughout, so a database that already received this
-- script is unchanged by a second run.

-- One report per course per trimester: the header of the school's own document names exactly one
-- course and one trimester ("INFORME PEDAGOGICO DEL PRIMER TRIMESTRE"), and the teacher who writes
-- it is the homeroom teacher already recorded on the course.
CREATE TABLE IF NOT EXISTS public.pedagogical_reports (
    id_pedagogical_report uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- CASCADE: the report is a statement about one classroom in one trimester, and it means nothing
    -- once that classroom is gone. The course also pins the gestión, so no year column is needed.
    id_course uuid NOT NULL REFERENCES public.courses (id_course) ON DELETE CASCADE,

    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),

    -- Section II, both columns, exactly as the teacher types them. `text` and not `varchar(n)`:
    -- these are several paragraphs of prose in the school's own document, they name outstanding
    -- students one by one, and a length nobody measured would silently truncate a teacher's work.
    --
    -- Nullable, and that is the point of a draft. A teacher opens the report, fills the failing
    -- students first and comes back to the prose; a NOT NULL here would force them to invent
    -- something to save anything at all.
    achievements text,
    difficulties text,

    -- `updated_at` carries a default and no trigger, which is what every other table in this schema
    -- does: the application stamps it on write. There is not a single trigger in `docs/sql`, and
    -- introducing one here would make this the one table whose timestamps the database maintains.
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Edited, never appended. A second report for the same course and trimester is not a second
    -- opinion, it is the same document saved again — and two of them would leave the school with no
    -- way to say which one it signed.
    CONSTRAINT uq_pedagogical_report UNIQUE (id_course, trimester)
);

-- Section IV, one row per failing student. Only the two columns the teacher writes; the student's
-- failed areas and their marks are read from `academic_scores` when the sheet is drawn.
CREATE TABLE IF NOT EXISTS public.pedagogical_report_failures (
    id_pedagogical_report_failure uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- CASCADE: these rows are part of the report, not records of their own. Deleting the report
    -- deletes the paragraph the teacher wrote inside it.
    id_pedagogical_report uuid NOT NULL
        REFERENCES public.pedagogical_reports (id_pedagogical_report) ON DELETE CASCADE,

    -- The enrolment and not the student: the sheet is about this child in this classroom in this
    -- gestión, and a child moved between parallels mid-year holds an enrolment in both.
    id_course_enrollment uuid NOT NULL
        REFERENCES public.course_enrollments (id_course_enrollment) ON DELETE CASCADE,

    -- "Acciones, estrategias y/o adaptaciones curriculares realizadas" and "Fuente de Verificación".
    -- Nullable for the same reason as the prose above: a half-written report has to be savable.
    actions text,
    verification_source text,

    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One line per student on the sheet. The document lists several failed areas and several marks
    -- inside a single student's row, so a second row for the same student would print that student
    -- twice with the actions split between them.
    --
    -- This also serves the only read the screen makes — every failing student of one report — so
    -- there is no separate index on `id_pedagogical_report`: its btree already leads with that
    -- column, and a second index over the same prefix is one more thing to keep written on insert
    -- and nothing to gain on select.
    CONSTRAINT uq_pedagogical_report_failure UNIQUE (id_pedagogical_report, id_course_enrollment)
);
