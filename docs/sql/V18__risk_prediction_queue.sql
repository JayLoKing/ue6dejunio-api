-- V18: the queue that turns "predict on every change" into something the model survives.
--
-- RF 30 asks that the risk classification run whenever a mark or an attendance is recorded. Taken
-- literally, that is one HTTP call to the model per keystroke a teacher saves: a classroom of
-- thirty students entered one after another is thirty batches of one vector. The model is a
-- FastAPI process on loopback that loads TensorFlow on its first call; it would spend the morning
-- being woken up to answer questions that the next save invalidates a second later.
--
-- So the write does not predict. It leaves a mark here, and a scheduled sweep drains the marks.
-- Thirty saves in the same subject collapse into one row, and therefore into one prediction run.
-- That is the whole purpose of this table: coalescing. The primary key is what does it — the
-- second save of the same subject and trimester conflicts with the first and adds no second row.
--
-- One window stays open and is worth naming rather than implying it is closed. A change committing
-- between the sweep's read of this table and its read of the marks can be cleared without having
-- been predicted; it lasts as long as one commit. What it costs is that one subject staying a
-- sweep stale, and the teacher's next save re-queues it. Closing it completely means claiming rows
-- with FOR UPDATE SKIP LOCKED, which is a lock held across an HTTP call to the model.
--
-- Why a table and not an in-memory set: a restart with marks in memory loses them, and losing a
-- mark is silent. The mark is gone, the sweep finds nothing, and a student whose marks changed is
-- never re-predicted — with no error anywhere to say so. A row survives the restart.
--
-- What is deliberately NOT stored here:
--   * Which student changed. The model's unit is the whole class group for a trimester, because
--     `IRiskFeatureDomain` reads marks, planned criteria and attendance for a collection of class
--     groups at once. Narrowing to one student would make the sweep read the group anyway.
--   * What changed, or who changed it. This is not an audit trail — `risk_predictions` already
--     keeps `features_analyzed`, the exact vector each prediction was made on. A second, thinner
--     record of the same history would be free to disagree with it.
--   * Any attempt counter. A sweep that fails because the model is down must leave its marks
--     standing so the next tick retries them; a counter that eventually gives up would drop a
--     classroom quietly, which is the one outcome this table exists to prevent.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS, so a database that already received this script is
-- unchanged by a second run.

CREATE TABLE IF NOT EXISTS public.risk_prediction_queue (
    -- CASCADE: a mark is a request to re-predict one class group. Once the group is gone there is
    -- nothing left to predict, and a row pointing at it could never be drained.
    id_class_group uuid NOT NULL REFERENCES public.class_groups (id_class_group) ON DELETE CASCADE,

    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),

    -- When the most recent un-swept change arrived. It is not decoration: the sweep clears only
    -- the marks it actually swept, by comparing against the instant it read them. A save that lands
    -- while the model is answering carries a later `marked_at`, survives the clear, and is picked
    -- up by the following tick. Without this column that save would be deleted unpredicted.
    --
    -- It is therefore *refreshed* by a later save on the same row — ON CONFLICT DO UPDATE, never
    -- DO NOTHING. Keeping the oldest instant instead would reopen exactly the hole this column
    -- exists to close: a subject already queued, swept, and cleared on an instant later than a
    -- second change that arrived mid-sweep and left the row untouched. Refreshing cannot starve
    -- anything either — a row that keeps moving forward is still returned by every sweep and still
    -- predicted; it is only the deletion that waits for the edits to stop.
    --
    -- clock_timestamp() and not CURRENT_TIMESTAMP: the latter is the *transaction's* start time in
    -- Postgres, so a long write would stamp its mark with an instant from before the sweep read,
    -- and be cleared having never been seen.
    marked_at timestamp NOT NULL DEFAULT clock_timestamp(),

    -- One standing request per subject and trimester. This is the coalescing, stated as a
    -- constraint instead of as code that has to remember to check.
    PRIMARY KEY (id_class_group, trimester)
);

-- No extra index on `marked_at`. The table holds at most one row per class group per trimester and
-- is emptied on every sweep, so it is a few hundred rows at its theoretical worst and usually
-- fewer than ten. An index would be read past, not used, and would cost every mark a write.

COMMENT ON TABLE public.risk_prediction_queue IS
    'Class groups whose model inputs changed since the last sweep. Drained by the scheduled risk '
    'sweep; a row means "re-predict this subject for this trimester", not "this is at risk".';
