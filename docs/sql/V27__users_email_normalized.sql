-- V27: one spelling per e-mail address, enforced by the database.
--
-- The web trims and lowercases every address before sending it; the API used to store and compare
-- them exactly as typed. An account stored with capitals (the bootstrap Director, a seed, a
-- request sent by hand) could not sign in from the web, and `Ana@x.com` and `ana@x.com` could be
-- registered as two users, because the plain UNIQUE(email) is case-sensitive. The application now
-- normalizes every address with trim + lowercase (Emails.normalize); this script brings the stored
-- rows in line and keeps them there.
--
--   1. Abort if two users would collide once normalized. Merging accounts is a decision about
--      people (which one keeps the history, which one is closed), not something a script may
--      guess. The error lists every collision; fix them by hand and run V27 again.
--   2. Normalize the stored addresses.
--   3. Add CHECK (email = lower(btrim(email, ...))). Together with the existing UNIQUE(email) it
--      makes a case duplicate impossible: every row must already be lowercase, so two rows that
--      differ only in case cannot both exist. It was chosen over replacing the UNIQUE with a
--      unique index on lower(email) because it leaves the existing constraint, the index the
--      lookups use and the JPA mapping untouched, and it also refuses padded addresses, which an
--      index on lower(email) alone would not.
--
-- The trimmed characters (space, tab, CR, LF) are the blanks String.trim() removes in practice.
--
-- Idempotent: once normalized, the check finds nothing, the UPDATE touches no row and the
-- constraint is added only if missing. Run by hand like every other script in this folder.

DO $$
DECLARE
    collisions text;
BEGIN
    SELECT string_agg(
               format('%s: users %s', normalized, user_ids),
               E'\n' ORDER BY normalized)
      INTO collisions
      FROM (
            SELECT lower(btrim(email, E' \t\r\n')) AS normalized,
                   string_agg(id_user::text || ' (' || email || ')', ', ' ORDER BY id_user) AS user_ids
              FROM public.users
             GROUP BY lower(btrim(email, E' \t\r\n'))
            HAVING count(*) > 1
           ) c;

    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION
            E'V27 aborted: these e-mail addresses belong to more than one user once trimmed and lowercased.\nDecide by hand which account keeps each address, change the others, then run V27 again:\n%',
            collisions;
    END IF;
END
$$;

UPDATE public.users
   SET email = lower(btrim(email, E' \t\r\n'))
 WHERE email <> lower(btrim(email, E' \t\r\n'));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'ck_users_email_normalized'
           AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT ck_users_email_normalized
            CHECK (email = lower(btrim(email, E' \t\r\n')));
    END IF;
END
$$;

COMMENT ON CONSTRAINT ck_users_email_normalized ON public.users IS
    'Addresses are stored trimmed and lowercase (Emails.normalize). With UNIQUE(email) this makes '
    'two accounts that differ only in case impossible.';
