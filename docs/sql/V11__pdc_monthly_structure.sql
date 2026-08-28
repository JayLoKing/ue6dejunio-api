-- V11 — Reshape the curriculum plan (PDC) to the shape of the official form.
--
-- The form is a MONTHLY plan for a whole course. Inside it, subjects are grouped by knowledge
-- area, each subject carries its own learning objective and a table of weekly rows, and each
-- subject may close with a list of general adaptations plus a table of significant adaptations
-- per student.
--
-- What the schema held instead: one plan per class group per trimester, with a single flat set of
-- content/activity/criteria columns. It could not hold a second month, a second subject, or a
-- second week.
--
-- Safe to restructure in place: curriculum_plans, curriculum_adaptations and
-- curriculum_plan_progress are all empty at the time of writing (verified, 0 rows each).

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Knowledge areas
-- ---------------------------------------------------------------------------
-- subjects.id_area already existed but dangled: no table behind it, no foreign key, and every
-- subject left it NULL. The form groups subjects by area, so the catalogue has to be real.

CREATE TABLE knowledge_areas (
    id_area       SERIAL PRIMARY KEY,
    name          VARCHAR(80) NOT NULL UNIQUE,
    display_order INT NOT NULL
);

INSERT INTO knowledge_areas (name, display_order) VALUES
    ('Cosmos y Pensamiento', 1),
    ('Comunidad y Sociedad', 2),
    ('Vida Tierra y Territorio', 3),
    ('Ciencia Tecnología y Producción', 4);

ALTER TABLE subjects
    ADD CONSTRAINT subjects_area_fkey
        FOREIGN KEY (id_area) REFERENCES knowledge_areas(id_area) ON DELETE RESTRICT;

UPDATE subjects SET id_area = (SELECT id_area FROM knowledge_areas WHERE name = 'Comunidad y Sociedad')
    WHERE name IN ('Comunicacion y Lenguaje', 'Ciencias Sociales', 'Educacion Fisica',
                   'Artes Plasticas', 'Musica');
UPDATE subjects SET id_area = (SELECT id_area FROM knowledge_areas WHERE name = 'Ciencia Tecnología y Producción')
    WHERE name IN ('Matematicas', 'Tecnologia');
UPDATE subjects SET id_area = (SELECT id_area FROM knowledge_areas WHERE name = 'Vida Tierra y Territorio')
    WHERE name IN ('Ciencias Naturales');
UPDATE subjects SET id_area = (SELECT id_area FROM knowledge_areas WHERE name = 'Cosmos y Pensamiento')
    WHERE name IN ('Religion');

-- Every subject must land in an area, or the form cannot group it.
ALTER TABLE subjects ALTER COLUMN id_area SET NOT NULL;

-- The stored names were unaccented shorthand and did not match the printed form, which has to
-- carry the curriculum's own wording. Renaming here keeps the form, the report and the database
-- saying the same thing.
-- 'Ciencias Sociales' and 'Ciencias Naturales' already match the form and are left alone.
UPDATE subjects SET name = 'Comunicación y Lenguajes'          WHERE name = 'Comunicacion y Lenguaje';
UPDATE subjects SET name = 'Educación Física y Deportes'       WHERE name = 'Educacion Fisica';
UPDATE subjects SET name = 'Artes Plásticas y Visuales'        WHERE name = 'Artes Plasticas';
UPDATE subjects SET name = 'Educación Musical'                 WHERE name = 'Musica';
UPDATE subjects SET name = 'Matemática'                        WHERE name = 'Matematicas';
UPDATE subjects SET name = 'Técnica Tecnológica'               WHERE name = 'Tecnologia';
UPDATE subjects SET name = 'Valores, Espiritualidad y Religiones' WHERE name = 'Religion';

-- ---------------------------------------------------------------------------
-- 2. curriculum_plans becomes the monthly header
-- ---------------------------------------------------------------------------
-- The plan belongs to a COURSE, not to one class group: the homeroom teacher's plan covers every
-- subject of the course. A specialist teacher's plan covers one, and says so by having a single
-- row in curriculum_plan_subjects. Same shape either way.

ALTER TABLE curriculum_plans DROP CONSTRAINT cp_cg_fkey;

ALTER TABLE curriculum_plans
    DROP COLUMN id_class_group,
    DROP COLUMN title,
    DROP COLUMN learning_objective,
    DROP COLUMN contents,
    DROP COLUMN practice_activities,
    DROP COLUMN theory_activities,
    DROP COLUMN valuation_activities,
    DROP COLUMN production_activities,
    DROP COLUMN resources,
    DROP COLUMN start_date,
    DROP COLUMN end_date,
    DROP COLUMN criteria_being,
    DROP COLUMN criteria_knowing,
    DROP COLUMN criteria_doing,
    DROP COLUMN criteria_deciding;

ALTER TABLE curriculum_plans
    ADD COLUMN id_course      UUID NOT NULL REFERENCES courses(id_course) ON DELETE CASCADE,
    -- "PLAN DE DESARROLLO CURRICULAR Nº 4" — which plan of the year this is.
    ADD COLUMN plan_number    INT  NOT NULL,
    -- "Del: 03 de agosto  al: 04 de septiembre" — the month the plan runs, which is why a
    -- trimester holds three or four of them.
    ADD COLUMN period_start   DATE NOT NULL,
    ADD COLUMN period_end     DATE NOT NULL,
    ADD COLUMN final_product  TEXT,
    ADD COLUMN bibliography   TEXT,
    -- Teachers of one grade take turns writing the month's plan and the parallels each get a
    -- copy they may adjust. This points a copy back at the plan it came from, so the rotation
    -- stays visible and a copy is never mistaken for independent work.
    ADD COLUMN source_plan_id UUID REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE SET NULL;

ALTER TABLE curriculum_plans ALTER COLUMN trimester SET NOT NULL;
ALTER TABLE curriculum_plans ALTER COLUMN status SET NOT NULL;

ALTER TABLE curriculum_plans
    ADD CONSTRAINT uq_plan_course_number UNIQUE (id_course, trimester, plan_number),
    ADD CONSTRAINT check_plan_period CHECK (period_end >= period_start),
    ADD CONSTRAINT check_plan_number CHECK (plan_number BETWEEN 1 AND 12);

DROP INDEX IF EXISTS idx_plan_cg;
CREATE INDEX idx_plan_course ON curriculum_plans (id_course);
CREATE INDEX idx_plan_source ON curriculum_plans (source_plan_id);

-- ---------------------------------------------------------------------------
-- 3. One block per subject inside the plan
-- ---------------------------------------------------------------------------
CREATE TABLE curriculum_plan_subjects (
    id_plan_subject     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan  UUID NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    -- The class group carries subject, course and teacher at once, so the block knows who owns it.
    id_class_group      UUID NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    learning_objective  TEXT,
    -- "ADAPTACIONES CURRICULARES (estudiantes con dificultades ... o ritmos distintos)": a list of
    -- strategies for the class. Not the same thing as the per-student table below.
    general_adaptations TEXT,
    display_order       INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_subject UNIQUE (id_curriculum_plan, id_class_group)
);

CREATE INDEX idx_plan_subject_plan ON curriculum_plan_subjects (id_curriculum_plan);
CREATE INDEX idx_plan_subject_cg ON curriculum_plan_subjects (id_class_group);

-- ---------------------------------------------------------------------------
-- 4. The weekly rows of a subject's table
-- ---------------------------------------------------------------------------
CREATE TABLE curriculum_plan_entries (
    id_plan_entry     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_plan_subject   UUID NOT NULL REFERENCES curriculum_plan_subjects(id_plan_subject) ON DELETE CASCADE,
    -- Free text on purpose: the form carries "Semana 1" but also "Semanas 3 y 4".
    week_label        VARCHAR(60) NOT NULL,
    contents          TEXT,
    -- The four moments of the formative process, one column each.
    practice          TEXT,
    theory            TEXT,
    valuation         TEXT,
    production        TEXT,
    resources         TEXT,
    periods           INT,
    criteria_being    TEXT,
    criteria_knowing  TEXT,
    criteria_doing    TEXT,
    criteria_deciding TEXT,
    display_order     INT NOT NULL DEFAULT 0,
    CONSTRAINT check_entry_periods CHECK (periods IS NULL OR periods >= 0)
);

CREATE INDEX idx_plan_entry_subject ON curriculum_plan_entries (id_plan_subject);

-- ---------------------------------------------------------------------------
-- 5. Significant adaptations, per student
-- ---------------------------------------------------------------------------
-- "ADAPTACIONES CURRICULARES SIGNIFICATIVAS" is a table of Contenido | Discapacidad, talento
-- extraordinario, TDAH, TEA y otros | Adaptación | Criterio de evaluación, one row per student.
-- curriculum_adaptations already held three of those four columns; the condition was missing, and
-- so was the subject the adaptation applies to.
ALTER TABLE curriculum_adaptations
    ADD COLUMN id_plan_subject UUID REFERENCES curriculum_plan_subjects(id_plan_subject) ON DELETE CASCADE,
    ADD COLUMN condition_type  VARCHAR(120);

CREATE INDEX idx_adaptation_plan_subject ON curriculum_adaptations (id_plan_subject);

COMMIT;
