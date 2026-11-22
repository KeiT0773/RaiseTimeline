-- users（ユーザー）。定義は docs/04_er_diagram.md 2. の users と一致させる。
-- 適用済みのこのファイルは編集しない。変更は V2 以降のファイルで行う。
CREATE TABLE users (
  id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- @ID。入力どおりの大文字・小文字で保存し、一意性は lower(username) の索引で判定する
  username        VARCHAR(15)  NOT NULL,
  display_name    VARCHAR(30)  NOT NULL,
  -- ログイン用。アプリ側で小文字にそろえてから保存する
  email           VARCHAR(255) NOT NULL,
  -- BCrypt でハッシュ化した値。平文は保存しない
  password_digest VARCHAR(255) NOT NULL,
  -- 自己紹介（0〜160 文字）。空文字はアプリ側で NULL にそろえる
  bio             VARCHAR(160),
  -- アイコン画像のストレージ上の保存先。NULL は未設定
  avatar_path     VARCHAR(255),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

  -- 制約名は、登録時の重複エラーがどの項目かを判定するのに使う（アプリ側の検証と二重にかける）
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT ck_users_username_format CHECK (username ~ '^[A-Za-z0-9_]{3,15}$')
);

-- Hanako と hanako を同じユーザー名として扱う（大文字と小文字を区別しない一意性）。
-- プロフィールの URL（/users/:username）の検索にも使われる。
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
