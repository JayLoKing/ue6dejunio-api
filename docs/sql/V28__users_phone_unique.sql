-- V28: a phone number belongs to one user at most, enforced by the database.
--
-- The school reaches a teacher or the secretary by phone, and the user list shows the number as the
-- way to call them. Two accounts holding the same number left the Director dialling one person for
-- the other's business. The application now refuses a number that is already on record when a user
-- is created, edited by the Director, edits their own profile, or is created by a Director handover
-- (DuplicateResourceException, 409); this script brings the stored rows in line and keeps them there
-- when two requests race past that check.
--
--   1. Abort if two users would share a number once trimmed. Which account keeps a number is a
--      question about people, not something a script may guess. The error lists every collision;
--      fix them by hand and run V28 again.
--   2. Trim the stored numbers, and turn the blank ones into NULL: the application stores a missing
--      phone as NULL, and a blank string is the same absence spelled differently.
--   3. Add a partial unique index. Partial because a missing number is not a value: any number of
--      users have none, and a plain UNIQUE would let only one of them exist. The predicate keeps
--      the blank case out as well, so a row written before step 2 by an older build cannot collide
--      with another blank one either.
--
-- The trimmed characters (space, tab, CR, LF) are the blanks String.trim() removes in practice.
--
-- Idempotent: once trimmed, the check finds nothing, the UPDATEs touch no row and the index is
-- created only if missing. Run by hand like every other script in this folder.

DO $$
DECLARE
    collisions text;
BEGIN
    SELECT string_agg(
               format('%s: users %s', trimmed, user_ids),
               E'\n' ORDER BY trimmed)
      INTO collisions
      FROM (
            SELECT btrim(phone, E' \t\r\n') AS trimmed,
                   string_agg(id_user::text, ', ' ORDER BY id_user) AS user_ids
              FROM public.users
             WHERE phone IS NOT NULL
               AND btrim(phone, E' \t\r\n') <> ''
             GROUP BY btrim(phone, E' \t\r\n')
            HAVING count(*) > 1
           ) c;

    IF collisions IS NOT NULL THEN
        RAISE EXCEPTION
            E'V28 aborted: these phone numbers belong to more than one user once trimmed.\nDecide by hand which account keeps each number, change the others, then run V28 again:\n%',
            collisions;
    END IF;
END
$$;

UPDATE public.users
   SET phone = NULL
 WHERE phone IS NOT NULL
   AND btrim(phone, E' \t\r\n') = '';

UPDATE public.users
   SET phone = btrim(phone, E' \t\r\n')
 WHERE phone IS NOT NULL
   AND phone <> btrim(phone, E' \t\r\n');

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_phone
    ON public.users (phone)
    WHERE phone IS NOT NULL AND btrim(phone) <> '';

COMMENT ON INDEX public.ux_users_phone IS
    'A phone number belongs to one user at most. Missing and blank numbers are not values and are '
    'left out, so any number of users can have none.';
