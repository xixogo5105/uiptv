CREATE TABLE IF NOT EXISTS AccountInfo (
    id INTEGER PRIMARY KEY,
    accountId TEXT NOT NULL UNIQUE,
    expireDate TEXT,
    accountStatus TEXT,
    accountBalance TEXT,
    tariffName TEXT,
    tariffPlan TEXT
);
