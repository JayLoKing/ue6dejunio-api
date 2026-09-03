-- Who took a student off the roll, when, and in their own words.
--
-- The row already said WHAT happened (`status`) and under which category (`status_reason`). It did
-- not say who decided it or when, so a teacher reading that one of their students is gone had no
-- way to ask anyone about it. And the "Otro" category had nowhere to put the reason it exists for:
-- the Director picked it, typed an explanation, and the row kept the word "Otro".
--
-- Columns rather than a history table: one current state per student is a plain functional
-- dependency on id_student, which is what every other audited table here already does
-- (attendance, curriculum_plans, academic_scores). A history of changes would be multivalued and
-- would need its own table; correcting a reason overwrites the previous one, and that is accepted.

ALTER TABLE public.students
    ADD COLUMN status_note        text,
    ADD COLUMN status_changed_at  timestamp without time zone,
    ADD COLUMN status_changed_by  uuid;

-- SET NULL, like every other created_by/updated_by in this schema: removing a user account must
-- not block a student's record, and the fact that the change happened outlives whoever made it.
ALTER TABLE public.students
    ADD CONSTRAINT students_status_changed_by_fkey FOREIGN KEY (status_changed_by)
    REFERENCES public.users (id_user) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE SET NULL;
