-- Director-configured trimester date ranges, used to derive the trimester of a daily
-- attendance record (attendance rows have no trimester column). Additive: does not alter
-- any existing table. See configurable-trimester-periods change.
CREATE TABLE academic_trimesters (
    id_academic_trimester uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    id_academic_year integer NOT NULL REFERENCES academic_years(id_academic_year) ON DELETE CASCADE,
    trimester integer NOT NULL CHECK (trimester BETWEEN 1 AND 3),
    start_date date NOT NULL,
    end_date date NOT NULL,
    CONSTRAINT uq_trimester_period UNIQUE (id_academic_year, trimester),
    CONSTRAINT chk_trimester_dates CHECK (end_date >= start_date)
);
