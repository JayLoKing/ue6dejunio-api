-- V13 — Give a notification a subject, a thing it is about, and a receipt.
--
-- Until now a notification was a line of text and a boolean. That was enough while the only
-- sender was the PDC screen telling an author their plan was reviewed. It stops being enough as
-- soon as the Director writes one himself: an inbox of bare sentences has no way to say which are
-- about the notebook, which about attendance, and which are a summons to the office.
--
-- Three things go in:
--
--   1. WHAT IT IS       type + subject
--   2. WHAT IT IS ABOUT resource_type + resource_id, so the row is something you can click
--   3. WHAT HAPPENED    delivered_at + read_at, the two receipts the Director asked for
--
-- is_read goes away. It said the same thing read_at says, and two columns holding one fact drift
-- the first time a write updates one and forgets the other.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. What the notification is
-- ---------------------------------------------------------------------------
-- Deliberately not a Postgres enum: the catalog grows every time the school finds another reason
-- to write to someone, and ALTER TYPE ... ADD VALUE cannot run inside a transaction. A varchar
-- with the check in the application is the cheaper place to keep a list that moves.
ALTER TABLE notifications
    ADD COLUMN type    VARCHAR(40) NOT NULL DEFAULT 'CUSTOM',
    -- Free text, and only when the type is CUSTOM. Every other type IS its own subject, so
    -- storing a copy of the label would be a second place for the same words to live.
    ADD COLUMN subject VARCHAR(150);

-- ---------------------------------------------------------------------------
-- 2. What it is about
-- ---------------------------------------------------------------------------
-- A loose reference on purpose, with no foreign key: the target is a plan today and a risk
-- prediction tomorrow, and a notification must outlive what it points at. A message saying "your
-- plan was observed" is still a true record of what the Director said after the plan is deleted;
-- an ON DELETE CASCADE would erase the history instead.
ALTER TABLE notifications
    ADD COLUMN resource_type VARCHAR(40),
    ADD COLUMN resource_id   UUID;

-- ---------------------------------------------------------------------------
-- 3. What happened to it
-- ---------------------------------------------------------------------------
ALTER TABLE notifications
    -- When the receiver's inbox actually carried it back. Answers "did it reach them", which for
    -- "aproximese a direccion urgentemente" is the whole point of sending it.
    ADD COLUMN delivered_at TIMESTAMP,
    ADD COLUMN read_at      TIMESTAMP,
    -- No logic reads this yet. It is here because adding a column to a table already in use is a
    -- second migration for nothing, and the retention decision is "archive, never delete".
    ADD COLUMN archived_at  TIMESTAMP;

UPDATE notifications SET read_at = created_at WHERE is_read = true;

ALTER TABLE notifications
    DROP COLUMN is_read;

-- ---------------------------------------------------------------------------
-- 4. The order the inbox actually reads in
-- ---------------------------------------------------------------------------
-- Every listing is one receiver's newest first, and until now that was a sequential scan.
CREATE INDEX idx_notification_inbox ON notifications (receiver_id, created_at DESC);

COMMIT;
