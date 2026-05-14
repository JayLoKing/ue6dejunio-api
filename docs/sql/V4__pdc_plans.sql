CREATE TABLE pdc_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_group_id UUID NOT NULL REFERENCES class_groups(id) ON DELETE CASCADE,
    trimester INT NOT NULL CHECK (trimester BETWEEN 1 AND 3),

    status VARCHAR(30) NOT NULL DEFAULT 'Borrador'
        CHECK (status IN ('Borrador', 'Publicado', 'En revisión', 'Aprobado', 'Con observaciones')),
    review_observations TEXT,

    title VARCHAR(200),
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

    criteria_ser TEXT,
    criteria_saber TEXT,
    criteria_hacer TEXT,
    criteria_decidir TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id)
);
