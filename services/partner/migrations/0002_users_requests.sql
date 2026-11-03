-- Lockd In accounts (R50) and partner approval requests (R51).
-- Passwords are stored only as salted PBKDF2 hashes; device tokens only as SHA-256 hashes.
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  pw_hash TEXT NOT NULL,
  pw_salt TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  created_ip TEXT,
  failures INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  label TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS devices_user ON devices(user_id);
-- A partner account can belong to a Lockd In account (set when the partner is added while signed in).
ALTER TABLE accounts ADD COLUMN user_id TEXT;
-- One request to end one session early. The partner opens /r/<id>, enters their PIN and decides.
CREATE TABLE IF NOT EXISTS requests (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  session_id TEXT NOT NULL,
  session_end INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  decision TEXT,
  decided_at INTEGER,
  grant_code TEXT
);
CREATE INDEX IF NOT EXISTS requests_account ON requests(account_id, created_at);
