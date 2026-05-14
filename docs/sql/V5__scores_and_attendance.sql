CREATE TABLE academic_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    trimester INT NOT NULL CHECK (trimester BETWEEN 1 AND 3),

    score_ser NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (score_ser >= 0 AND score_ser <= 10),
    score_saber NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (score_saber >= 0 AND score_saber <= 45),
    score_hacer NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (score_hacer >= 0 AND score_hacer <= 40),
    score_auto NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (score_auto >= 0 AND score_auto <= 5),

    created_by UUID REFERENCES users(id),
    digital_signature_hash TEXT,

    total_score NUMERIC(5,2) GENERATED ALWAYS AS
        (score_ser + score_saber + score_hacer + score_auto) STORED,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    status VARCHAR(15) NOT NULL CHECK (status IN ('Presente', 'Falta', 'Licencia', 'Atraso')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    UNIQUE (enrollment_id, date)
);
