-- refresh_tokens（リフレッシュトークン）。定義は docs/04_er_diagram.md 2. の refresh_tokens と一致させる。
-- 適用済みのこのファイルは編集しない。変更は V3 以降のファイルで行う。
CREATE TABLE refresh_tokens (
  id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- トークンの値の SHA-256（16 進 64 文字）。値そのものは保存しない
  token_hash CHAR(64)    NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- ログイン時に、そのユーザーの期限切れの行を削除するときに使う
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
