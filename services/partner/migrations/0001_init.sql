-- Lockd In partner service (R49). Keys are stored encrypted (AES-GCM, Worker secret MASTER_KEY).
-- Device tokens and partner PINs are stored only as salted hashes.
CREATE TABLE IF NOT EXISTS accounts (
  id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL,
  key_enc TEXT NOT NULL,
  partner_name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  created_ip TEXT
);
CREATE TABLE IF NOT EXISTS links (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  pin_hash TEXT,
  pin_salt TEXT,
  claimed_at INTEGER,
  failures INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  from_admin INTEGER NOT NULL,
  kind TEXT NOT NULL DEFAULT 'text',
  body TEXT NOT NULL,
  session_id TEXT,
  created_at INTEGER NOT NULL,
  read_by_admin INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS messages_account ON messages(account_id, id);
CREATE TABLE IF NOT EXISTS audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL,
  actor TEXT NOT NULL,
  action TEXT NOT NULL,
  account_id TEXT,
  detail TEXT
);
