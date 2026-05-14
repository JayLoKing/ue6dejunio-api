CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ci VARCHAR(15) UNIQUE NOT NULL,
    names VARCHAR(100) NOT NULL,
    last_names VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100) UNIQUE NOT NULL,
    password TEXT NOT NULL,
    role_id INT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role_id);

INSERT INTO roles (name) VALUES ('DIRECTOR'), ('SECRETARIO'), ('DOCENTE');
