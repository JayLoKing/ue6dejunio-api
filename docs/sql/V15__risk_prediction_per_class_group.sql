-- One prediction per student, per subject, per trimester.
--
-- The table was written for a model that judged a student as a whole. The model that exists judges
-- a student IN A SUBJECT: the label it learned is the result of the next trimester in that same
-- class group, and a single run produces roughly nine rows for one student, not one. Under the old
-- `UNIQUE (id_student, trimester)` those nine rows overwrite each other and the survivor is
-- whichever subject happened to be written last — a number about a subject nobody can name.
--
-- Dropped and recreated rather than altered. Nothing has ever written here: `risk_predictions`
-- appears in no entity, no repository and no query, only in the integration-test truncation list.
-- There are no rows to preserve, and an ALTER chain would leave the shape of this table depending
-- on which of the earlier hand-run scripts a given database actually received.
--
-- Two probabilities instead of one `probability_score`. The model answers with four class
-- probabilities and the school reads two opposite questions off them: is this student about to
-- fail, and is this student about to stand out. Collapsing both into one column names neither.
-- `p_fail` is P(RiesgoCritico), and that category IS failing: it holds the averages at or below 50
-- when the pass mark is 51. `EnRiesgo` (51-66) passes by a hair and is deliberately not added in.

DROP TABLE IF EXISTS public.risk_predictions;

CREATE TABLE public.risk_predictions (
    id_risk_prediction uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    id_student uuid NOT NULL REFERENCES public.students (id_student) ON DELETE CASCADE,

    -- The subject the prediction is about. CASCADE for the same reason as the student: a prediction
    -- is a statement about a student in a class group, and it means nothing once the group is gone.
    id_class_group uuid NOT NULL REFERENCES public.class_groups (id_class_group) ON DELETE CASCADE,

    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),

    -- The four categories the model was trained on, spelled exactly as it answers them, so the
    -- value that arrives over HTTP is the value that is stored and no translation table sits in
    -- between waiting to disagree with the model.
    risk_level varchar(20) NOT NULL
        CHECK (risk_level IN ('RiesgoCritico', 'EnRiesgo', 'SinRiesgo', 'Sobresaliente')),

    p_fail numeric(5, 4) NOT NULL CHECK (p_fail BETWEEN 0 AND 1),
    p_outstanding numeric(5, 4) NOT NULL CHECK (p_outstanding BETWEEN 0 AND 1),

    -- Whether somebody acted on this. The prediction is advice; this column is the school's answer
    -- to it, and it is the only field here a person writes.
    is_attended boolean NOT NULL DEFAULT false,

    -- The exact feature vector the model was given. Kept because the prediction cannot be explained
    -- or reproduced without it: the same student re-predicted a week later has different inputs,
    -- and without this row there is no way to say what changed.
    features_analyzed jsonb,

    predicted_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- An upsert target, not a history. Re-running the model for the same student, subject and
    -- trimester corrects the standing prediction instead of appending a second opinion beside it.
    CONSTRAINT uq_risk_pred UNIQUE (id_student, id_class_group, trimester)
);

-- The read the school actually makes: everyone at risk in one subject this trimester.
CREATE INDEX ix_risk_pred_group_trimester
    ON public.risk_predictions (id_class_group, trimester);
