-- V16: order the knowledge areas the way the school's own sheets read.
--
-- V11 seeded display_order in the order the areas happened to be written down. The libreta and the
-- centralizador the school signs both open with COMUNIDAD Y SOCIEDAD and close with COSMOS Y
-- PENSAMIENTO, and a teacher checking a printed sheet against the screen reads them row by row.
-- An order that only this system believes in makes that check impossible.
--
-- The order lives in the data, not in code, so the Director can change it without a deploy. That
-- is also why this is an UPDATE of display_order rather than a sort hard-coded in the query.
--
-- Idempotent: matches by name, and the names are UNIQUE.

UPDATE knowledge_areas SET display_order = 1 WHERE name = 'Comunidad y Sociedad';
UPDATE knowledge_areas SET display_order = 2 WHERE name = 'Ciencia Tecnología y Producción';
UPDATE knowledge_areas SET display_order = 3 WHERE name = 'Vida Tierra y Territorio';
UPDATE knowledge_areas SET display_order = 4 WHERE name = 'Cosmos y Pensamiento';
