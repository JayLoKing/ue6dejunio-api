CREATE TABLE risk_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    trimester INT NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    risk_level VARCHAR(20) NOT NULL,
    probability_score NUMERIC(5,4),
    is_attended BOOLEAN NOT NULL DEFAULT FALSE,
    features_analyzed JSONB,
    predicted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES users(id),
    receiver_id UUID NOT NULL REFERENCES users(id),
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_receiver ON notifications (receiver_id, is_read);
