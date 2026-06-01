CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id_role SERIAL PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL
);

CREATE TABLE users (
    id_user UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ci VARCHAR(15) UNIQUE NOT NULL,
    names VARCHAR(100) NOT NULL,
    last_names VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100) UNIQUE NOT NULL,
    password TEXT NOT NULL,
    id_role INT REFERENCES roles(id_role) ON DELETE RESTRICT,
    is_active BOOLEAN DEFAULT TRUE,
    must_change_password BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id_user) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE academic_years (
    id_academic_year SERIAL PRIMARY KEY,
    year INT UNIQUE NOT NULL
);

CREATE TABLE levels (
    id_level SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE grades (
    id_grade SERIAL PRIMARY KEY,
    id_level INT REFERENCES levels(id_level) ON DELETE RESTRICT,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE parallels (
    id_parallel SERIAL PRIMARY KEY,
    name CHAR(1) NOT NULL
);

CREATE TABLE subjects (
    id_subject UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    area VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE students (
    id_student UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rude_code VARCHAR(20) UNIQUE NOT NULL,
    identity_card VARCHAR(15) UNIQUE NOT NULL,
    names VARCHAR(100) NOT NULL,
    last_names VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    gender CHAR(1) CHECK (gender IN ('M', 'F')),
    status VARCHAR(20) DEFAULT 'Effective' CHECK (status IN ('Effective', 'Withdrawn', 'Transferred')),
    status_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE class_groups (
    id_class_group UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_subject UUID REFERENCES subjects(id_subject) ON DELETE CASCADE,
    id_teacher UUID REFERENCES users(id_user) ON DELETE SET NULL,
    id_grade INT REFERENCES grades(id_grade) ON DELETE RESTRICT,
    id_parallel INT REFERENCES parallels(id_parallel) ON DELETE RESTRICT,
    id_academic_year INT REFERENCES academic_years(id_academic_year) ON DELETE RESTRICT
);

CREATE TABLE enrollments (
    id_enrollment UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_student UUID REFERENCES students(id_student) ON DELETE CASCADE,
    id_class_group UUID REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    enrollment_date DATE DEFAULT CURRENT_DATE
);

CREATE TABLE curriculum_plans (
    id_curriculum_plan UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_class_group UUID REFERENCES class_groups(id_class_group) ON DELETE CASCADE,
    trimester INT CHECK (trimester BETWEEN 1 AND 3),
    status VARCHAR(30) DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Published', 'Under Review', 'Approved', 'With Observations')),
    review_observations TEXT,
    title VARCHAR(200) NOT NULL,
    holistic_objective TEXT,
    learning_objective TEXT,
    contents TEXT,
    practice_activities TEXT,
    theory_activities TEXT,
    valuation_activities TEXT,
    production_activities TEXT,
    resources TEXT,
    start_date DATE,
    end_date DATE,
    criteria_being TEXT,
    criteria_knowing TEXT,
    criteria_doing TEXT,
    criteria_deciding TEXT,
    created_by UUID REFERENCES users(id_user) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id_user) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE academic_scores (
    id_academic_score UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_enrollment UUID REFERENCES enrollments(id_enrollment) ON DELETE CASCADE,
    trimester INT CHECK (trimester BETWEEN 1 AND 3),
    score_being NUMERIC(5,2) DEFAULT 0 CHECK (score_being <= 10),
    score_knowing NUMERIC(5,2) DEFAULT 0 CHECK (score_knowing <= 45),
    score_doing NUMERIC(5,2) DEFAULT 0 CHECK (score_doing <= 40),
    score_deciding NUMERIC(5,2) DEFAULT 0 CHECK (score_deciding <= 5),
    created_by UUID REFERENCES users(id_user) ON DELETE RESTRICT,
    digital_signature_hash TEXT,
    total_score NUMERIC(5,2) GENERATED ALWAYS AS (score_being + score_knowing + score_doing + score_deciding) STORED,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE attendance (
    id_attendance UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_enrollment UUID REFERENCES enrollments(id_enrollment) ON DELETE CASCADE,
    date DATE NOT NULL,
    status VARCHAR(15) CHECK (status IN ('Present', 'Absent', 'Excused', 'Late'))
);

CREATE TABLE risk_predictions (
    id_risk_prediction UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_student UUID REFERENCES students(id_student) ON DELETE CASCADE,
    trimester INT,
    risk_level VARCHAR(20) CHECK (risk_level IN ('Green', 'Yellow', 'Orange', 'Red')),
    probability_score NUMERIC(5,4),
    is_attended BOOLEAN DEFAULT FALSE,
    features_analyzed JSONB,
    predicted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id_notification UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID REFERENCES users(id_user) ON DELETE CASCADE,
    receiver_id UUID REFERENCES users(id_user) ON DELETE CASCADE,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO roles (name) VALUES ('Director'), ('Secretary'), ('Teacher');
INSERT INTO levels (name) VALUES ('Primaria Comunitaria Vocacional');
INSERT INTO grades (id_level, name) VALUES (1, '1ro');
INSERT INTO parallels (name) VALUES ('A');
INSERT INTO academic_years (year) VALUES (2026);
INSERT INTO subjects (name, area) VALUES ('Matematicas', 'Ciencias Exactas'), ('Lenguaje', 'Comunicacion');
