CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS roles (
    id_role SERIAL PRIMARY KEY,
    name varchar(20) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id_user uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ci varchar(15) NOT NULL UNIQUE,
    names varchar(100) NOT NULL,
    last_names varchar(100) NOT NULL,
    phone varchar(20),
    email varchar(100) NOT NULL UNIQUE,
    password text NOT NULL,
    id_role integer REFERENCES roles(id_role) ON DELETE RESTRICT,
    is_technical boolean DEFAULT false,
    is_active boolean DEFAULT true,
    must_change_password boolean DEFAULT true,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS academic_years (id_academic_year SERIAL PRIMARY KEY, year integer NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS levels (id_level SERIAL PRIMARY KEY, name varchar(100) NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS grades (
    id_grade SERIAL PRIMARY KEY,
    id_level integer NOT NULL REFERENCES levels(id_level) ON DELETE RESTRICT,
    name varchar(50) NOT NULL,
    CONSTRAINT uq_grade_per_level UNIQUE (id_level, name)
);
CREATE TABLE IF NOT EXISTS parallels (id_parallel SERIAL PRIMARY KEY, name char(1) NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS knowledge_areas (
    id_area SERIAL PRIMARY KEY,
    name varchar(80) NOT NULL UNIQUE,
    display_order integer NOT NULL
);

CREATE TABLE IF NOT EXISTS subjects (
    id_subject uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(100) NOT NULL,
    id_area integer NOT NULL REFERENCES knowledge_areas(id_area) ON DELETE RESTRICT,
    is_technical boolean DEFAULT false,
    is_active boolean DEFAULT true,
    CONSTRAINT uq_subject_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS students (
    id_student uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rude_code varchar(20) NOT NULL UNIQUE,
    identity_card varchar(15) NOT NULL UNIQUE,
    names varchar(100) NOT NULL,
    last_names varchar(100) NOT NULL,
    birth_date date NOT NULL,
    gender char(1),
    status varchar(20) DEFAULT 'Effective',
    status_reason text,
    status_note text,
    status_changed_at timestamp,
    status_changed_by uuid,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS courses (
    id_course uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_grade integer NOT NULL REFERENCES grades(id_grade) ON DELETE RESTRICT,
    id_parallel integer NOT NULL REFERENCES parallels(id_parallel) ON DELETE RESTRICT,
    id_academic_year integer NOT NULL REFERENCES academic_years(id_academic_year) ON DELETE RESTRICT,
    id_homeroom_teacher uuid REFERENCES users(id_user) ON DELETE SET NULL,
    is_active boolean DEFAULT true,
    CONSTRAINT uq_course UNIQUE (id_grade, id_parallel, id_academic_year)
);

CREATE TABLE IF NOT EXISTS course_enrollments (
    id_course_enrollment uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_student uuid NOT NULL REFERENCES students(id_student) ON DELETE RESTRICT,
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE RESTRICT,
    enrollment_date date DEFAULT CURRENT_DATE,
    status varchar(20) DEFAULT 'Effective',
    CONSTRAINT uq_course_enrollment UNIQUE (id_student, id_course)
);

CREATE TABLE IF NOT EXISTS class_groups (
    id_class_group uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE RESTRICT,
    id_subject uuid NOT NULL REFERENCES subjects(id_subject) ON DELETE RESTRICT,
    id_teacher uuid REFERENCES users(id_user) ON DELETE SET NULL,
    is_active boolean DEFAULT true,
    CONSTRAINT uq_class_group UNIQUE (id_course, id_subject)
);

CREATE TABLE IF NOT EXISTS curriculum_plans (
    id_curriculum_plan uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE CASCADE,
    plan_number integer NOT NULL CHECK (plan_number BETWEEN 1 AND 12),
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    period_start date NOT NULL,
    period_end date NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'Draft',
    review_observations text,
    holistic_objective text,
    final_product text,
    bibliography text,
    source_plan_id uuid REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    CONSTRAINT uq_plan_course_number UNIQUE (id_course, trimester, plan_number),
    CONSTRAINT check_plan_period CHECK (period_end >= period_start)
);

CREATE TABLE IF NOT EXISTS curriculum_plan_subjects (
    id_plan_subject uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan uuid NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    learning_objective text,
    general_adaptations text,
    display_order integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_subject UNIQUE (id_curriculum_plan, id_class_group)
);

CREATE TABLE IF NOT EXISTS curriculum_plan_entries (
    id_plan_entry uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_plan_subject uuid NOT NULL REFERENCES curriculum_plan_subjects(id_plan_subject) ON DELETE CASCADE,
    week_label varchar(60) NOT NULL,
    contents text,
    practice text, theory text, valuation text, production text,
    resources text,
    periods integer CHECK (periods IS NULL OR periods >= 0),
    criteria_being text, criteria_knowing text, criteria_doing text,
    display_order integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS curriculum_plan_progress (
    id_progress uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan uuid NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    progress_date date NOT NULL DEFAULT CURRENT_DATE,
    advanced_content text,
    percentage numeric(5,2) CHECK (percentage BETWEEN 0 AND 100),
    observations text,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS curriculum_adaptations (
    id_curriculum_adaptation uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan uuid NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    id_student uuid NOT NULL REFERENCES students(id_student) ON DELETE CASCADE,
    condition_type varchar(120),
    adapted_contents text, adapted_methodology text, adapted_criteria text,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    -- One row per student. The service checks before inserting, but a check and then an act is a
    -- race: two concurrent creates both read "no row yet" and the plan prints the child twice.
    CONSTRAINT uq_adaptation_plan_student UNIQUE (id_curriculum_plan, id_student)
);

CREATE TABLE IF NOT EXISTS evaluation_criteria (
    id_criterion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    dimension varchar(20) NOT NULL CHECK (dimension IN ('Being','Knowing','Doing','Deciding')),
    name varchar(150) NOT NULL,
    activity_name varchar(150),
    id_curriculum_plan uuid REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_criterion UNIQUE (id_class_group, trimester, dimension, name)
);

CREATE TABLE IF NOT EXISTS assessment_events (
    id_assessment_event uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_criterion uuid NOT NULL REFERENCES evaluation_criteria(id_criterion) ON DELETE CASCADE,
    title varchar(150) NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

-- A score targets an activity item or a criterion, never both. Because id_assessment_event is
-- nullable now, the old two-column UNIQUE no longer protects direct scores: PostgreSQL treats
-- every NULL as distinct, so duplicates would slip through. Two partial unique indexes replace it.
CREATE TABLE IF NOT EXISTS assessment_scores (
    id_assessment_score uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE CASCADE,
    id_assessment_event uuid REFERENCES assessment_events(id_assessment_event) ON DELETE CASCADE,
    id_criterion uuid REFERENCES evaluation_criteria(id_criterion) ON DELETE CASCADE,
    score numeric(5,2) NOT NULL DEFAULT 0,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_score_target CHECK (
        (id_criterion IS NOT NULL AND id_assessment_event IS NULL)
     OR (id_criterion IS NULL     AND id_assessment_event IS NOT NULL)
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_score_event
    ON assessment_scores (id_course_enrollment, id_assessment_event)
    WHERE id_assessment_event IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_score_criterion
    ON assessment_scores (id_course_enrollment, id_criterion)
    WHERE id_criterion IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_scores_enrollment ON assessment_scores (id_course_enrollment);
CREATE INDEX IF NOT EXISTS ix_events_criterion ON assessment_events (id_criterion);
CREATE INDEX IF NOT EXISTS ix_criteria_group_trimester
    ON evaluation_criteria (id_class_group, trimester);

CREATE TABLE IF NOT EXISTS academic_scores (
    id_academic_score uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE RESTRICT,
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE RESTRICT,
    trimester integer CHECK (trimester BETWEEN 1 AND 3),
    score_being numeric(5,2) DEFAULT 0 CHECK (score_being <= 10),
    score_knowing numeric(5,2) DEFAULT 0 CHECK (score_knowing <= 45),
    score_doing numeric(5,2) DEFAULT 0 CHECK (score_doing <= 40),
    score_deciding numeric(5,2) DEFAULT 0 CHECK (score_deciding <= 5),
    total_score numeric(5,2) GENERATED ALWAYS AS (score_being + score_knowing + score_doing + score_deciding) STORED,
    created_by uuid REFERENCES users(id_user) ON DELETE RESTRICT,
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_academic_score UNIQUE (id_course_enrollment, id_class_group, trimester)
);

CREATE TABLE IF NOT EXISTS attendance (
    id_attendance uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE CASCADE,
    id_class_group uuid REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    date date NOT NULL,
    status varchar(15) NOT NULL CHECK (status IN ('Present','Absent','Excused','Late')),
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_att_daily ON attendance (id_course_enrollment, date) WHERE id_class_group IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_att_session ON attendance (id_course_enrollment, date, id_class_group) WHERE id_class_group IS NOT NULL;

CREATE TABLE IF NOT EXISTS academic_trimesters (
    id_academic_trimester uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_academic_year integer NOT NULL REFERENCES academic_years(id_academic_year) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    start_date date NOT NULL,
    end_date date NOT NULL,
    CONSTRAINT uq_trimester_period UNIQUE (id_academic_year, trimester),
    CONSTRAINT chk_trimester_dates CHECK (end_date >= start_date)
);

-- One row per student, per subject, per trimester: the model judges a student IN a class group,
-- so a single run produces one of these per subject the student sits. See V15.
CREATE TABLE IF NOT EXISTS risk_predictions (
    id_risk_prediction uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_student uuid NOT NULL REFERENCES students(id_student) ON DELETE CASCADE,
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    risk_level varchar(20) NOT NULL
        CHECK (risk_level IN ('RiesgoCritico','EnRiesgo','SinRiesgo','Sobresaliente')),
    p_fail numeric(5,4) NOT NULL CHECK (p_fail BETWEEN 0 AND 1),
    p_outstanding numeric(5,4) NOT NULL CHECK (p_outstanding BETWEEN 0 AND 1),
    is_attended boolean NOT NULL DEFAULT false,
    features_analyzed jsonb, predicted_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_pred UNIQUE (id_student, id_class_group, trimester)
);
CREATE INDEX IF NOT EXISTS ix_risk_pred_group_trimester
    ON risk_predictions (id_class_group, trimester);

-- Class groups whose model inputs changed since the last sweep. One row per subject and trimester:
-- the primary key is what collapses thirty saves into one prediction run.
CREATE TABLE IF NOT EXISTS risk_prediction_queue (
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    -- clock_timestamp(), not CURRENT_TIMESTAMP: the latter is the transaction's start time.
    marked_at timestamp NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (id_class_group, trimester)
);

CREATE TABLE IF NOT EXISTS notifications (
    id_notification uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id uuid REFERENCES users(id_user) ON DELETE CASCADE,
    receiver_id uuid REFERENCES users(id_user) ON DELETE CASCADE,
    type varchar(40) NOT NULL DEFAULT 'CUSTOM',
    -- Free text, and only when the type is CUSTOM: every other type is its own subject.
    subject varchar(150),
    message text NOT NULL,
    -- No foreign key on purpose: a notification must outlive what it points at, or deleting a
    -- plan would erase the record of what the Director said about it.
    resource_type varchar(40), resource_id uuid,
    delivered_at timestamp, read_at timestamp, archived_at timestamp,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_inbox ON notifications (receiver_id, created_at DESC);

-- The informe pedagógico is the one report of the five that is not derived: sections II and the
-- "acciones" column of section IV are prose the teacher writes. Everything else on the sheet is
-- read from the institution, the course and the marks at render time. See V17 for why each of
-- those four things is deliberately not a column here.
CREATE TABLE IF NOT EXISTS pedagogical_reports (
    id_pedagogical_report uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    -- Nullable on purpose: a half-written report has to be savable.
    achievements text,
    difficulties text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pedagogical_report UNIQUE (id_course, trimester)
);

-- Section IV, one row per failing student. The areas they failed and the marks are not here: they
-- come from academic_scores when the sheet is drawn.
CREATE TABLE IF NOT EXISTS pedagogical_report_failures (
    id_pedagogical_report_failure uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_pedagogical_report uuid NOT NULL
        REFERENCES pedagogical_reports(id_pedagogical_report) ON DELETE CASCADE,
    id_course_enrollment uuid NOT NULL
        REFERENCES course_enrollments(id_course_enrollment) ON DELETE CASCADE,
    actions text,
    verification_source text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pedagogical_report_failure UNIQUE (id_pedagogical_report, id_course_enrollment)
);

-- The catalog seed runs once per Spring context, and more than one context is created against the
-- same shared container (ProdProfileHardeningIT activates a second profile). Every statement here
-- is therefore idempotent: re-running the script must be a no-op, not a duplicate-key failure.
INSERT INTO roles (name) VALUES ('Director'), ('Secretary'), ('Teacher') ON CONFLICT DO NOTHING;
INSERT INTO levels (name) VALUES ('Primaria Comunitaria Vocacional') ON CONFLICT DO NOTHING;
INSERT INTO grades (id_level, name)
    SELECT id_level, 'Primero' FROM levels WHERE name = 'Primaria Comunitaria Vocacional'
    ON CONFLICT DO NOTHING;
INSERT INTO parallels (name) VALUES ('A'), ('B'), ('C') ON CONFLICT DO NOTHING;
-- Two of them, because the directory is about one gestión at a time and a single year cannot tell
-- a listing that picked the right one from a listing that has no rule at all. 2026 is the current
-- one everywhere: every helper that does not name a year resolves the latest.
INSERT INTO academic_years (year) VALUES (2025), (2026) ON CONFLICT DO NOTHING;
-- display_order is the order the school's own libreta and centralizador read, opening with
-- COMUNIDAD Y SOCIEDAD and closing with COSMOS Y PENSAMIENTO. Kept in step with V16 so a test
-- asserting the sheet's order is asserting what production actually holds.
INSERT INTO knowledge_areas (name, display_order) VALUES
    ('Comunidad y Sociedad', 1),
    ('Ciencia Tecnología y Producción', 2),
    ('Vida Tierra y Territorio', 3),
    ('Cosmos y Pensamiento', 4)
    ON CONFLICT DO NOTHING;
-- Fixture names, deliberately the short ones the tests look up by. The production rename to the
-- curriculum's wording is a data migration, not a change to what these fixtures are called.
INSERT INTO subjects (name, id_area)
    SELECT 'Matematicas', id_area FROM knowledge_areas WHERE name = 'Ciencia Tecnología y Producción'
    UNION ALL
    SELECT 'Lenguaje', id_area FROM knowledge_areas WHERE name = 'Comunidad y Sociedad'
    ON CONFLICT DO NOTHING;
INSERT INTO academic_trimesters (id_academic_year, trimester, start_date, end_date)
    SELECT id_academic_year, 1, DATE '2026-02-01', DATE '2026-05-31' FROM academic_years WHERE year = 2026
    UNION ALL
    SELECT id_academic_year, 2, DATE '2026-06-01', DATE '2026-08-31' FROM academic_years WHERE year = 2026
    UNION ALL
    SELECT id_academic_year, 3, DATE '2026-09-01', DATE '2026-11-30' FROM academic_years WHERE year = 2026
    ON CONFLICT DO NOTHING;
