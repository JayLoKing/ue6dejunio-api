CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rude VARCHAR(20) UNIQUE NOT NULL,
    names VARCHAR(100) NOT NULL,
    last_names VARCHAR(100) NOT NULL,
    birth_date DATE,
    gender CHAR(1) CHECK (gender IN ('M', 'F')),
    status VARCHAR(20) NOT NULL DEFAULT 'Activo'
        CHECK (status IN ('Activo', 'Retirado', 'Transferido')),
    status_reason TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_student_search_name ON students (last_names, names);

CREATE TABLE class_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    teacher_id UUID REFERENCES users(id) ON DELETE SET NULL,
    grade_id INT NOT NULL REFERENCES grades(id),
    parallel_id INT NOT NULL REFERENCES parallels(id),
    academic_year_id INT NOT NULL REFERENCES academic_years(id)
);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_group_id UUID NOT NULL REFERENCES class_groups(id) ON DELETE CASCADE,
    enrollment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    UNIQUE (student_id, class_group_id)
);
