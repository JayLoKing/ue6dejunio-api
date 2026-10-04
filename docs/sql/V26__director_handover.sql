-- V26: the handover of the Director role, in two phases.
--
-- The installation starts with one Director and the user form refuses to mint another, so the
-- office could never change hands without editing the database. A handover is the single door that
-- does: the outgoing Director names an incoming one (a new account, or an existing user promoted),
-- both hold the role for a while on purpose, and the outgoing account is closed only when the
-- incoming Director has signed in and replaced the temporary password. Ending with ZERO Directors
-- is the failure this table exists to prevent, so the row is what the application consults to
-- decide whether the outgoing account may be closed.
--
--   * outgoing_id       the Director who opened the handover (and the only one who can cancel it).
--   * incoming_id       the user who becomes Director.
--   * incoming_created  true when the handover created the account, false when it promoted an
--                       existing user. Cancelling closes a created account and restores the
--                       previous role of a promoted one.
--   * previous_role_id  the role a promoted user held before; NULL for a created account.
--   * started_at        when the handover was opened.
--   * closed_at         when the incoming Director set a password of their own (handover done).
--   * cancelled_at      when the outgoing Director called it off. A row is never both.
--
-- A handover is PENDING while closed_at and cancelled_at are both NULL. The partial unique index
-- allows at most one pending row, so two handovers cannot race each other into leaving nobody in
-- charge, whatever the application does.
--
-- All foreign keys are RESTRICT: users are deactivated, never deleted, and the history of who
-- handed the school to whom must survive. Rows are never deleted by the application either.
--
-- Idempotent: IF NOT EXISTS throughout. Run by hand like every other script in this folder.

CREATE TABLE IF NOT EXISTS public.director_handovers (
    id_handover      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    outgoing_id      uuid NOT NULL REFERENCES public.users(id_user) ON DELETE RESTRICT,
    incoming_id      uuid NOT NULL REFERENCES public.users(id_user) ON DELETE RESTRICT,
    incoming_created boolean NOT NULL,
    previous_role_id integer REFERENCES public.roles(id_role) ON DELETE RESTRICT,
    started_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at        timestamp NULL,
    cancelled_at     timestamp NULL,
    CONSTRAINT chk_director_handover_not_closed_and_cancelled
        CHECK (closed_at IS NULL OR cancelled_at IS NULL),
    CONSTRAINT chk_director_handover_distinct_users
        CHECK (outgoing_id <> incoming_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_director_handovers_single_pending
    ON public.director_handovers ((true))
    WHERE closed_at IS NULL AND cancelled_at IS NULL;

COMMENT ON TABLE public.director_handovers IS
    'Handover of the Director role. One pending row at most; closing it deactivates the outgoing Director.';
COMMENT ON COLUMN public.director_handovers.incoming_created IS
    'True when the handover created the incoming account, false when it promoted an existing user.';
COMMENT ON COLUMN public.director_handovers.previous_role_id IS
    'Role a promoted user held before the handover (restored on cancel). NULL for a created account.';
COMMENT ON COLUMN public.director_handovers.closed_at IS
    'Set when the incoming Director replaced the temporary password; the outgoing account is deactivated in the same transaction.';
COMMENT ON COLUMN public.director_handovers.cancelled_at IS
    'Set when the outgoing Director cancelled the handover. Never set together with closed_at.';
COMMENT ON INDEX public.ux_director_handovers_single_pending IS
    'At most one pending handover (closed_at and cancelled_at both NULL).';
