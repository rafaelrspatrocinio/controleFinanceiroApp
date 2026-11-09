CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT PRIMARY KEY,
    type VARCHAR(16) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    category VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger_settings (
    id INTEGER PRIMARY KEY,
    opening_balance_cents BIGINT NOT NULL CHECK (opening_balance_cents >= 0)
);

INSERT INTO ledger_settings (id, opening_balance_cents) SELECT 1, 0 WHERE NOT EXISTS (SELECT 1 FROM ledger_settings WHERE id = 1);

CREATE TABLE IF NOT EXISTS initialization_state (
    id INTEGER PRIMARY KEY,
    initialized BOOLEAN NOT NULL
);

INSERT INTO initialization_state (id, initialized) SELECT 1, FALSE WHERE NOT EXISTS (SELECT 1 FROM initialization_state WHERE id = 1);
