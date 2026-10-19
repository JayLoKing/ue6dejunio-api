-- Fresh-only baseline, reconciled with current entity table/column mappings and V9-V28.
-- See docs/deployment.md for provenance and the pending PostgreSQL/Hibernate validation.
-- No historical SQL is replayed. No users, years, enrollments or fixture subjects are seeded.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
               AND n.nspname NOT LIKE 'pg_toast%' AND c.relkind IN ('r','p','v','m','S','f')) THEN
    RAISE EXCEPTION 'Fresh initialization refused: target contains user relations';
  END IF;
END $$;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id_role SERIAL PRIMARY KEY,
    name varchar(20) NOT NULL UNIQUE
);

CREATE TABLE users (
    id_user uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ci varchar(15) NOT NULL UNIQUE,
    names varchar(100) NOT NULL,
    last_names varchar(100) NOT NULL,
    phone varchar(20),
    email varchar(100) NOT NULL UNIQUE
        CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email, E' \t\r\n'))),
    password text NOT NULL,
    id_role integer NOT NULL REFERENCES roles(id_role) ON DELETE RESTRICT,
    is_technical boolean DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    must_change_password boolean NOT NULL DEFAULT true,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_users_phone
    ON users (phone)
    WHERE phone IS NOT NULL AND btrim(phone) <> '';

CREATE TABLE academic_years (
    id_academic_year SERIAL PRIMARY KEY,
    year integer NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT false,
    closed_at timestamp NULL
);
CREATE UNIQUE INDEX ux_academic_years_single_active ON academic_years (active) WHERE active;
CREATE TABLE levels (id_level SERIAL PRIMARY KEY, name varchar(100) NOT NULL UNIQUE);
CREATE TABLE grades (
    id_grade SERIAL PRIMARY KEY,
    id_level integer NOT NULL REFERENCES levels(id_level) ON DELETE RESTRICT,
    name varchar(50) NOT NULL,
    CONSTRAINT uq_grade_per_level UNIQUE (id_level, name)
);
CREATE TABLE parallels (id_parallel SERIAL PRIMARY KEY, name char(1) NOT NULL UNIQUE);
CREATE TABLE knowledge_areas (
    id_area SERIAL PRIMARY KEY,
    name varchar(80) NOT NULL UNIQUE,
    display_order integer NOT NULL
);

CREATE TABLE subjects (
    id_subject uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(100) NOT NULL,
    id_area integer NOT NULL REFERENCES knowledge_areas(id_area) ON DELETE RESTRICT,
    is_technical boolean DEFAULT false,
    is_active boolean DEFAULT true,
    CONSTRAINT uq_subject_name UNIQUE (name)
);

CREATE TABLE students (
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
    status_changed_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE courses (
    id_course uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_grade integer NOT NULL REFERENCES grades(id_grade) ON DELETE RESTRICT,
    id_parallel integer NOT NULL REFERENCES parallels(id_parallel) ON DELETE RESTRICT,
    id_academic_year integer NOT NULL REFERENCES academic_years(id_academic_year) ON DELETE RESTRICT,
    id_homeroom_teacher uuid REFERENCES users(id_user) ON DELETE SET NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_course UNIQUE (id_grade, id_parallel, id_academic_year)
);
CREATE UNIQUE INDEX ux_courses_homeroom_per_year
    ON courses (id_homeroom_teacher, id_academic_year)
    WHERE is_active AND id_homeroom_teacher IS NOT NULL;

CREATE TABLE course_enrollments (
    id_course_enrollment uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_student uuid NOT NULL REFERENCES students(id_student) ON DELETE RESTRICT,
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE RESTRICT,
    enrollment_date date DEFAULT CURRENT_DATE,
    status varchar(20) DEFAULT 'Effective',
    final_average numeric(5,2) NULL,
    final_result varchar(10) NULL CONSTRAINT chk_course_enrollments_final_result CHECK (final_result IN ('APPROVED', 'FAILED')),
    closed_at timestamp NULL,
    CONSTRAINT uq_course_enrollment UNIQUE (id_student, id_course)
);

CREATE TABLE class_groups (
    id_class_group uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE RESTRICT,
    id_subject uuid NOT NULL REFERENCES subjects(id_subject) ON DELETE RESTRICT,
    id_teacher uuid REFERENCES users(id_user) ON DELETE SET NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_class_group UNIQUE (id_course, id_subject)
);

CREATE TABLE curriculum_plans (
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

CREATE TABLE curriculum_plan_subjects (
    id_plan_subject uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan uuid NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    learning_objective text,
    general_adaptations text,
    display_order integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_subject UNIQUE (id_curriculum_plan, id_class_group)
);

CREATE TABLE curriculum_plan_entries (
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

CREATE TABLE curriculum_plan_progress (
    id_progress uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan uuid NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    progress_date date NOT NULL DEFAULT CURRENT_DATE,
    advanced_content text,
    percentage numeric(5,2) CHECK (percentage BETWEEN 0 AND 100),
    observations text,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE curriculum_adaptations (
    id_curriculum_adaptation uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_curriculum_plan uuid NOT NULL REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE CASCADE,
    id_student uuid NOT NULL REFERENCES students(id_student) ON DELETE CASCADE,
    condition_type varchar(120),
    adapted_contents text, adapted_methodology text, adapted_criteria text,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_adaptation_plan_student UNIQUE (id_curriculum_plan, id_student)
);

CREATE TABLE evaluation_criteria (
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

CREATE TABLE assessment_events (
    id_assessment_event uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_criterion uuid NOT NULL REFERENCES evaluation_criteria(id_criterion) ON DELETE CASCADE,
    title varchar(150) NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE assessment_scores (
    id_assessment_score uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE CASCADE,
    id_assessment_event uuid REFERENCES assessment_events(id_assessment_event) ON DELETE CASCADE,
    id_criterion uuid REFERENCES evaluation_criteria(id_criterion) ON DELETE CASCADE,
    score numeric(5,2) NOT NULL DEFAULT 0,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_score_target CHECK (
        (id_criterion IS NOT NULL AND id_assessment_event IS NULL)
     OR (id_criterion IS NULL     AND id_assessment_event IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_score_event
    ON assessment_scores (id_course_enrollment, id_assessment_event)
    WHERE id_assessment_event IS NOT NULL;
CREATE UNIQUE INDEX uq_score_criterion
    ON assessment_scores (id_course_enrollment, id_criterion)
    WHERE id_criterion IS NOT NULL;
CREATE INDEX ix_scores_enrollment ON assessment_scores (id_course_enrollment);
CREATE INDEX ix_events_criterion ON assessment_events (id_criterion);
CREATE INDEX ix_criteria_group_trimester
    ON evaluation_criteria (id_class_group, trimester);

CREATE TABLE academic_scores (
    id_academic_score uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE RESTRICT,
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE RESTRICT,
    trimester integer CHECK (trimester BETWEEN 1 AND 3),
    score_being numeric(5,2) DEFAULT 0 CHECK (score_being BETWEEN 0 AND 10),
    score_knowing numeric(5,2) DEFAULT 0 CHECK (score_knowing BETWEEN 0 AND 45),
    score_doing numeric(5,2) DEFAULT 0 CHECK (score_doing BETWEEN 0 AND 40),
    score_deciding numeric(5,2) DEFAULT 0 CHECK (score_deciding BETWEEN 0 AND 5),
    total_score numeric(5,2) GENERATED ALWAYS AS (score_being + score_knowing + score_doing + score_deciding) STORED,
    created_by uuid REFERENCES users(id_user) ON DELETE RESTRICT,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_academic_score UNIQUE (id_course_enrollment, id_class_group, trimester)
);

CREATE TABLE attendance (
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
CREATE UNIQUE INDEX uq_att_daily ON attendance (id_course_enrollment, date) WHERE id_class_group IS NULL;
CREATE UNIQUE INDEX uq_att_session ON attendance (id_course_enrollment, date, id_class_group) WHERE id_class_group IS NOT NULL;

CREATE TABLE academic_trimesters (
    id_academic_trimester uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_academic_year integer NOT NULL REFERENCES academic_years(id_academic_year) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    start_date date NOT NULL,
    end_date date NOT NULL,
    CONSTRAINT uq_trimester_period UNIQUE (id_academic_year, trimester),
    CONSTRAINT chk_trimester_dates CHECK (end_date >= start_date)
);

CREATE TABLE risk_predictions (
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
    last_notified_at timestamp,
    CONSTRAINT uq_risk_pred UNIQUE (id_student, id_class_group, trimester)
);
CREATE INDEX ix_risk_pred_group_trimester
    ON risk_predictions (id_class_group, trimester);

CREATE TABLE risk_prediction_queue (
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    marked_at timestamp NOT NULL
        DEFAULT (clock_timestamp() AT TIME ZONE 'America/La_Paz'),
    PRIMARY KEY (id_class_group, trimester)
);

CREATE TABLE notifications (
    id_notification uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id uuid REFERENCES users(id_user) ON DELETE CASCADE,
    receiver_id uuid REFERENCES users(id_user) ON DELETE CASCADE,
    type varchar(40) NOT NULL DEFAULT 'CUSTOM',
    subject varchar(150),
    message text NOT NULL,
    resource_type varchar(40), resource_id uuid,
    delivered_at timestamp, read_at timestamp, archived_at timestamp,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_inbox ON notifications (receiver_id, created_at DESC);

CREATE TABLE pedagogical_reports (
    id_pedagogical_report uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    achievements text,
    difficulties text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pedagogical_report UNIQUE (id_course, trimester)
);

CREATE TABLE pedagogical_report_failures (
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
CREATE INDEX ix_pedagogical_report_failure_enrollment
    ON pedagogical_report_failures (id_course_enrollment);

CREATE TABLE director_handovers (
    id_handover uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    outgoing_id uuid NOT NULL REFERENCES users(id_user) ON DELETE RESTRICT,
    incoming_id uuid NOT NULL REFERENCES users(id_user) ON DELETE RESTRICT,
    incoming_created boolean NOT NULL,
    previous_role_id integer REFERENCES roles(id_role) ON DELETE RESTRICT,
    started_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at timestamp NULL,
    cancelled_at timestamp NULL,
    CONSTRAINT chk_director_handover_not_closed_and_cancelled
        CHECK (closed_at IS NULL OR cancelled_at IS NULL),
    CONSTRAINT chk_director_handover_distinct_users CHECK (outgoing_id <> incoming_id)
);
CREATE UNIQUE INDEX ux_director_handovers_single_pending
    ON director_handovers ((true))
    WHERE closed_at IS NULL AND cancelled_at IS NULL;

-- Stable authorization/knowledge-area catalogs only; school configuration is explicit.
INSERT INTO roles (name) VALUES ('Director'), ('Secretary'), ('Teacher');
INSERT INTO knowledge_areas (name, display_order) VALUES
    ('Comunidad y Sociedad', 1),
    ('Ciencia Tecnología y Producción', 2),
    ('Vida Tierra y Territorio', 3),
    ('Cosmos y Pensamiento', 4);
