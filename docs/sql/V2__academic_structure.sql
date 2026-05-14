CREATE TABLE academic_years (
    id SERIAL PRIMARY KEY,
    year INT UNIQUE NOT NULL
);

CREATE TABLE levels (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE grades (
    id SERIAL PRIMARY KEY,
    level_id INT NOT NULL REFERENCES levels(id),
    name VARCHAR(50) NOT NULL
);

CREATE TABLE parallels (
    id SERIAL PRIMARY KEY,
    name CHAR(1) NOT NULL UNIQUE
);

CREATE TABLE subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    area VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
