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

CREATE TABLE academic_years (id_academic_year SERIAL PRIMARY KEY, year integer NOT NULL UNIQUE);
CREATE TABLE levels (id_level SERIAL PRIMARY KEY, name varchar(100) NOT NULL UNIQUE);
CREATE TABLE grades (
    id_grade SERIAL PRIMARY KEY,
    id_level integer NOT NULL REFERENCES levels(id_level) ON DELETE RESTRICT,
    name varchar(50) NOT NULL,
    CONSTRAINT uq_grade_per_level UNIQUE (id_level, name)
);
CREATE TABLE parallels (id_parallel SERIAL PRIMARY KEY, name char(1) NOT NULL UNIQUE);
CREATE TABLE subjects (
    id_subject uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(100) NOT NULL,
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
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE courses (
    id_course uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_grade integer NOT NULL REFERENCES grades(id_grade) ON DELETE RESTRICT,
    id_parallel integer NOT NULL REFERENCES parallels(id_parallel) ON DELETE RESTRICT,
    id_academic_year integer NOT NULL REFERENCES academic_years(id_academic_year) ON DELETE RESTRICT,
    id_homeroom_teacher uuid REFERENCES users(id_user) ON DELETE SET NULL,
    is_active boolean DEFAULT true,
    CONSTRAINT uq_course UNIQUE (id_grade, id_parallel, id_academic_year)
);

CREATE TABLE course_enrollments (
    id_course_enrollment uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_student uuid NOT NULL REFERENCES students(id_student) ON DELETE RESTRICT,
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE RESTRICT,
    enrollment_date date DEFAULT CURRENT_DATE,
    status varchar(20) DEFAULT 'Effective',
    CONSTRAINT uq_course_enrollment UNIQUE (id_student, id_course)
);

CREATE TABLE class_groups (
    id_class_group uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course uuid NOT NULL REFERENCES courses(id_course) ON DELETE RESTRICT,
    id_subject uuid NOT NULL REFERENCES subjects(id_subject) ON DELETE RESTRICT,
    id_teacher uuid REFERENCES users(id_user) ON DELETE SET NULL,
    is_active boolean DEFAULT true,
    CONSTRAINT uq_class_group UNIQUE (id_course, id_subject)
);

CREATE TABLE curriculum_plans (
    id_curriculum_plan uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_class_group uuid REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester integer CHECK (trimester BETWEEN 1 AND 3),
    status varchar(30) DEFAULT 'Draft',
    review_observations text,
    title varchar(200) NOT NULL,
    holistic_objective text, learning_objective text, contents text,
    practice_activities text, theory_activities text, valuation_activities text, production_activities text,
    resources text, start_date date, end_date date,
    criteria_being text, criteria_knowing text, criteria_doing text, criteria_deciding text,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL
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
    adapted_contents text, adapted_methodology text, adapted_criteria text,
    created_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by uuid REFERENCES users(id_user) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE evaluation_criteria (
    id_criterion uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_class_group uuid NOT NULL REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    dimension varchar(20) NOT NULL CHECK (dimension IN ('Being','Knowing','Doing','Deciding')),
    name varchar(150) NOT NULL,
    id_curriculum_plan uuid REFERENCES curriculum_plans(id_curriculum_plan) ON DELETE SET NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_criterion UNIQUE (id_class_group, trimester, dimension, name)
);

CREATE TABLE assessment_events (
    id_assessment_event uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_criterion uuid NOT NULL REFERENCES evaluation_criteria(id_criterion) ON DELETE CASCADE,
    title varchar(150) NOT NULL,
    description text,
    max_score numeric(5,2) NOT NULL DEFAULT 100,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE assessment_scores (
    id_assessment_score uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE CASCADE,
    id_assessment_event uuid NOT NULL REFERENCES assessment_events(id_assessment_event) ON DELETE CASCADE,
    score numeric(5,2) NOT NULL DEFAULT 0,
    digital_signature_hash text,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assessment_score UNIQUE (id_course_enrollment, id_assessment_event)
);

CREATE TABLE academic_scores (
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
    digital_signature_hash text,
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_academic_score UNIQUE (id_course_enrollment, id_class_group, trimester)
);

CREATE TABLE attendance (
    id_attendance uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_course_enrollment uuid NOT NULL REFERENCES course_enrollments(id_course_enrollment) ON DELETE CASCADE,
    id_class_group uuid REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    date date NOT NULL,
    status varchar(15) NOT NULL CHECK (status IN ('Present','Absent','Excused','Late'))
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
    id_student uuid REFERENCES students(id_student) ON DELETE CASCADE,
    trimester integer, risk_level varchar(20), probability_score numeric(5,4),
    is_attended boolean DEFAULT false, features_analyzed jsonb, predicted_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_pred UNIQUE (id_student, trimester)
);

CREATE TABLE notifications (
    id_notification uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id uuid REFERENCES users(id_user) ON DELETE CASCADE,
    receiver_id uuid REFERENCES users(id_user) ON DELETE CASCADE,
    message text NOT NULL, is_read boolean DEFAULT false, created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO roles (name) VALUES ('Director'), ('Secretary'), ('Teacher');
INSERT INTO levels (name) VALUES ('Primaria Comunitaria Vocacional');
INSERT INTO grades (id_level, name) VALUES (1, 'Primero');
INSERT INTO parallels (name) VALUES ('A'), ('B'), ('C');
INSERT INTO academic_years (year) VALUES (2026);
INSERT INTO subjects (name) VALUES ('Matematicas'), ('Lenguaje');
INSERT INTO academic_trimesters (id_academic_year, trimester, start_date, end_date)
    SELECT id_academic_year, 1, DATE '2026-02-01', DATE '2026-05-31' FROM academic_years WHERE year = 2026
    UNION ALL
    SELECT id_academic_year, 2, DATE '2026-06-01', DATE '2026-08-31' FROM academic_years WHERE year = 2026
    UNION ALL
    SELECT id_academic_year, 3, DATE '2026-09-01', DATE '2026-11-30' FROM academic_years WHERE year = 2026;
