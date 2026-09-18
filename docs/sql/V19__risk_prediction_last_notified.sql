-- V19: one column, so automation does not turn the teacher's inbox into noise.
--
-- Until RF 30 the model ran when somebody pressed a button, so a teacher heard about a student at
-- most as often as the Director felt like sweeping. Now it runs within minutes of every save, and
-- the announcement fires on a *transition* into a category that demands attention. A student whose
-- level oscillates while their teacher is entering marks — at risk after two, not after five, at
-- risk again after six — crosses that line once per crossing, and each crossing was a message.
--
-- Nothing about the prediction was wrong. Every one of those transitions really happened. The
-- problem is that a teacher who is told four times about the same child in one afternoon stops
-- reading any of it, and the notification that mattered arrives to somebody who has learned to
-- dismiss the bell.
--
-- So the announcement is bounded to once a day per prediction, and this column is the whole of it.
-- The grain comes free: `uq_risk_pred` is already UNIQUE (id_student, id_class_group, trimester),
-- so one row *is* one student in one subject in one trimester.
--
-- What it deliberately is NOT:
--   * Not a notifications table. `notifications` already holds what was sent; this holds only when
--     this row last contributed to one, which is a fact about the prediction.
--   * Not a counter, and not a history of every announcement. What is needed to decide is the last
--     one, and a counter would invite somebody to read meaning into a number nobody maintains.
--   * Not NOT NULL. A prediction that has never been announced has no instant to carry, and a
--     default of "now" or the epoch would both be a lie the query then has to work around.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS, so a database that already received this script is
-- unchanged by a second run.

ALTER TABLE public.risk_predictions
    ADD COLUMN IF NOT EXISTS last_notified_at timestamp;

COMMENT ON COLUMN public.risk_predictions.last_notified_at IS
    'When this prediction last caused a notification to its teacher. NULL means never. Read only '
    'to stop the same student being announced twice in one day; it is not a delivery receipt.';

-- No index. The column is never a search key: it is read for the handful of rows one run is about
-- to announce, always alongside `id_risk_prediction IN (...)`, so the primary key already leads.
