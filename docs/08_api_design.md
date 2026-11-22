# RaiseTimeline API 設計書

## 1. 文書情報

| 項目 | 内容 |
| --- | --- |
| 文書名 | RaiseTimeline API 設計書 |
| 版数 | 1.0 |
| 作成日 | 2026-11-22 |
| 最終更新日 | 2026-11-22 |
| ステータス | 作成中（認証の API のみ。他の API は各機能の実装時に追加する） |
| 上位文書 | [要件定義書](01_requirements.md)、[機能一覧](02_features.md) 5.2 |
| 関連文書 | [機能別要件書：ログイン（認証）](features/auth.md)、[ER図・テーブル定義](04_er_diagram.md)、[技術スタック](05_tech_stack.md) |

本書は、バックエンド（Spring Boot）が画面（React）に提供する HTTP API の要求・応答の形を定める。機能一覧 5.2 の API 一覧（案）のうち、実装したものから正式な定義を本書に移す。

### 1.1 変更履歴

| 版数 | 日付 | 変更内容 |
| --- | --- | --- |
| 1.0 | 2026-11-22 | 初版作成。共通の規約（2.）と認証の API（3.）を定義 |

---

## 2. 共通

### 2.1 基本

| 項目 | 内容 |
| --- | --- |
| ベースパス | `/api` |
| 形式 | 要求・応答とも JSON（`Content-Type: application/json`）。画像のアップロードだけ `multipart/form-data`（投稿の API で定義する） |
| 項目名 | キャメルケース（`displayName`） |
| 日時 | ISO 8601（UTC、例：`2026-11-22T06:47:16Z`）。画面で日本時間に変換して表示する |
| オリジン | 画面と API を同じオリジンで配信する（開発時は Vite のプロキシ。[技術スタック](05_tech_stack.md) 4.）。CORS は使わない |

### 2.2 認証（ログイン状態）

ログイン状態は 2 つのトークンで持つ（[auth](features/auth.md) 3.）。どちらもサーバーが `Set-Cookie` で渡し、ブラウザが自動で送る。画面の JavaScript からは読めない（`HttpOnly`）。

| Cookie | 中身 | 有効期限 | 属性 | 送られる先 |
| --- | --- | --- | --- | --- |
| `access_token` | JWT（HS256 で署名。`sub` はユーザー ID） | 15 分 | `HttpOnly`、`SameSite=Lax`、本番は `Secure` | `Path=/api`：すべての API |
| `refresh_token` | ランダムな値（JWT ではない） | 14 日 | `HttpOnly`、`SameSite=Lax`、本番は `Secure` | `Path=/api/auth`：更新・ログアウトの API だけ |

- ログインが必要な API は、`access_token` が無い・期限切れ・改ざんされている場合に **401** を返す
- 画面は 401 を受け取ったら `POST /api/auth/refresh` を 1 回呼び、成功したら元の要求をやり直す。更新も 401 ならログイン画面へ移す
- ログアウトや更新の失敗時は、サーバーが `Max-Age=0` の `Set-Cookie` で 2 つの Cookie を削除する
- サーバーはセッションを作らない（`JSESSIONID` は発行しない）

### 2.3 CSRF 対策

トークンを Cookie で送るため、状態を変える要求（POST / PUT / PATCH / DELETE）は CSRF のトークンを確かめる（[auth](features/auth.md) F-04）。

1. サーバーは応答の `Set-Cookie` で `XSRF-TOKEN` を渡す（JavaScript から読める。最初の GET で発行される）
2. 画面は状態を変える要求のたびに、Cookie の `XSRF-TOKEN` の値を `X-XSRF-TOKEN` ヘッダーに入れて送る。ログイン後に値が変わることがあるので、送るたびに Cookie から読み直す
3. ヘッダーが無い、または値が合わない要求は **403** になる

ログイン・新規登録・更新・ログアウトの API も対象になる。画面は起動時に `GET /api/auth/me` を呼んで `XSRF-TOKEN` を受け取ってから、これらを呼ぶ。

### 2.4 エラー応答

エラーは RFC 9457 の ProblemDetail（`Content-Type: application/problem+json`）で返す。

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "入力内容に誤りがあります",
  "instance": "/api/auth/signup",
  "errors": [
    { "field": "username", "message": "ユーザー名は半角英数字と _ で 3〜15 文字にしてください" },
    { "field": "passwordConfirmation", "message": "パスワードが一致しません" }
  ]
}
```

| 項目 | 内容 |
| --- | --- |
| `detail` | 画面にそのまま表示できる日本語の文言 |
| `type` | 使わない（RFC 9457 の既定値 `about:blank` のため、応答には含まれない） |
| `errors` | 項目ごとのエラー（入力の誤り・重複のときだけ）。`field` は要求の項目名。1 つの項目に複数の誤りがあるときは、同じ `field` で複数の要素になる。画面は該当する入力欄の下に `message` を表示する |

| ステータス | 使う場面 |
| --- | --- |
| 400 | 入力制約の違反（`errors` あり。`detail` は「入力内容に誤りがあります」）、JSON の形式の誤り（`detail` は「要求の形式が正しくありません」） |
| 401 | 未ログイン、トークンの期限切れ・改ざん、ログイン失敗、更新の失敗 |
| 403 | CSRF のトークンが無い・合わない |
| 404 | 対象が存在しない |
| 409 | 一意であるべき値の重複（`errors` あり） |

---

## 3. 認証

| メソッド | パス | 内容 | ログイン | 機能 ID |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/signup` | ユーザー作成（そのままログイン） | 不要 | F-01 |
| POST | `/api/auth/login` | ログイン | 不要 | F-02 |
| POST | `/api/auth/refresh` | トークンの更新 | 不要（`refresh_token` を使う） | F-04 |
| POST | `/api/auth/logout` | ログアウト | 不要（期限切れでも呼べる） | F-03 |
| GET | `/api/auth/me` | ログイン中の利用者の情報 | 必要 | F-04 |

ログインが「不要」の API も、POST は CSRF のトークンが必要（2.3）。

### 3.1 利用者の情報（共通の応答）

新規登録・ログイン・`/me` は、同じ形で利用者の情報を返す。パスワードのハッシュやトークンは本文に含めない。

```json
{ "id": 5, "username": "Hanako", "displayName": "山田花子" }
```

| 項目 | 型 | 内容 |
| --- | --- | --- |
| `id` | number | ユーザー ID |
| `username` | string | @ID（登録時に入力した大文字・小文字のまま） |
| `displayName` | string | 表示名 |

### 3.2 `POST /api/auth/signup`

```json
{
  "username": "Hanako",
  "displayName": "山田花子",
  "email": "hanako@example.com",
  "password": "password123",
  "passwordConfirmation": "password123"
}
```

- 前後の空白を除いてから検証する。`email` は小文字にそろえて保存する
- 入力制約と文言は [auth](features/auth.md) 5.

| 結果 | ステータス | 本文 | Cookie |
| --- | --- | --- | --- |
| 成功 | 201 | 利用者の情報（3.1） | `access_token`・`refresh_token` を発行 |
| 入力制約の違反 | 400 | ProblemDetail（`errors` あり） | — |
| ユーザー名（大文字と小文字を区別しない）・メールの重複 | 409 | ProblemDetail（`errors` に重複した項目をすべて。文言は「このユーザー名は既に使われています」「このメールアドレスは既に登録されています」） | — |

### 3.3 `POST /api/auth/login`

```json
{ "email": "hanako@example.com", "password": "password123" }
```

| 結果 | ステータス | 本文 | Cookie |
| --- | --- | --- | --- |
| 成功 | 200 | 利用者の情報（3.1） | `access_token`・`refresh_token` を発行 |
| メールアドレスまたはパスワードの誤り | 401 | ProblemDetail（`detail`：「メールアドレスまたはパスワードが正しくありません」。どちらが誤りかは区別しない） | — |
| 入力が空など | 400 | ProblemDetail（`errors` あり） | — |

### 3.4 `POST /api/auth/refresh`

本文なし。`refresh_token` の Cookie を使う。

| 結果 | ステータス | 本文 | Cookie |
| --- | --- | --- | --- |
| 成功 | 204 | なし | 2 つとも新しい値で発行し直す（使った `refresh_token` は無効になる） |
| `refresh_token` が無い・期限切れ・無効（使用済み、ログアウト済み） | 401 | ProblemDetail | 2 つとも削除 |

### 3.5 `POST /api/auth/logout`

本文なし。`refresh_token` があれば DB から削除する（無くても成功する）。

| 結果 | ステータス | 本文 | Cookie |
| --- | --- | --- | --- |
| 成功 | 204 | なし | 2 つとも削除 |

### 3.6 `GET /api/auth/me`

| 結果 | ステータス | 本文 |
| --- | --- | --- |
| ログイン中 | 200 | 利用者の情報（3.1） |
| 未ログイン・期限切れ・改ざん | 401 | ProblemDetail |

画面は起動時にこの API で、ログイン状態の確認と `XSRF-TOKEN` の受け取りを行う。
