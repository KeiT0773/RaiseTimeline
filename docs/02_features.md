# RaiseTimeline 機能一覧

## 1. 文書情報

| 項目 | 内容 |
| --- | --- |
| 文書名 | RaiseTimeline 機能一覧 |
| 版数 | 2.0 |
| 作成日 | 2026-11-14 |
| 最終更新日 | 2026-11-15 |
| ステータス | 作成中 |
| 上位文書 | [要件定義書](01_requirements.md) |
| 関連文書 | [機能別要件書](features/)、[画面設計](03_screens.md)、[ER図・テーブル定義](04_er_diagram.md) |

本書は、本システムの機能の一覧（機能 ID の台帳）と、機能別要件書への索引である。各機能の詳しい振る舞い・入力制約・エラー・受け入れ基準は、大分類ごとの[機能別要件書](features/)に定め、そちらを正とする。

### 1.1 変更履歴

| 版数 | 日付 | 変更内容 |
| --- | --- | --- |
| 1.0 | 2026-11-14 | 初版作成（F-01〜F-17） |
| 1.1 | 2026-11-14 | フォロー・プロフィール機能を MVP に追加（F-16 を MVP に、F-18〜F-21 を追加） |
| 1.2 | 2026-11-14 | 本文の上限を 280 文字に変更。画像添付（F-17）を MVP に変更 |
| 2.0 | 2026-11-15 | 機能の詳細を機能別要件書（features/）へ移し、本書を索引に再構成。ユーザー検索（F-22）・おすすめユーザー（F-23）を追加。ルーティングを画面の URL と API に分けて記載 |

---

## 2. 機能別要件書の一覧

| 大分類 | 機能別要件書 | 機能 ID |
| --- | --- | --- |
| ログイン（認証） | [features/auth.md](features/auth.md) | F-01〜F-04 |
| タイムライン | [features/timeline.md](features/timeline.md) | F-05〜F-07, F-21 |
| 投稿（画像添付を含む） | [features/post.md](features/post.md) | F-08〜F-10, F-17 |
| コメント | [features/comment.md](features/comment.md) | F-11〜F-13 |
| いいね | [features/like.md](features/like.md) | F-14, F-15 |
| フォロー / フォロワー | [features/follow.md](features/follow.md) | F-19, F-20 |
| プロフィール | [features/profile.md](features/profile.md) | F-16, F-18 |
| ユーザー検索 | [features/user-search.md](features/user-search.md) | F-22, F-23 |

機能別要件書は、どれも次の構成でそろえる：文書情報 → 概要 → 業務ルール → 機能詳細 → 入力制約 → 関連する画面・API・テーブル → 権限 → エラーと表示 → 受け入れ基準。

---

## 3. 機能一覧表

機能 ID は追加した順の通し番号で、大分類ごとには並んでいない（既存の ID は変えない）。すべて MVP（最初に作る範囲）とする。

| ID | 大分類 | 機能名 | 概要 | 画面 | 要件書 |
| --- | --- | --- | --- | --- | --- |
| F-01 | ログイン | 新規登録 | ユーザー名・表示名・メール・パスワードでアカウントを作成し、そのままログイン状態にする | SC-02 | [auth](features/auth.md) |
| F-02 | ログイン | ログイン | メールアドレスとパスワードで認証する | SC-01 | [auth](features/auth.md) |
| F-03 | ログイン | ログアウト | セッションを破棄してログイン画面へ戻る | 共通ヘッダー | [auth](features/auth.md) |
| F-04 | ログイン | アクセス制御 | 未ログイン時はログイン画面へ移す。API は 401 | 全画面 | [auth](features/auth.md) |
| F-05 | タイムライン | タイムライン表示 | 「すべて」タブ：全ユーザーの投稿を新しい順に表示する | SC-03 | [timeline](features/timeline.md) |
| F-06 | タイムライン | ページング | 20 件ずつ。「さらに読み込む」で次の 20 件を表示 | SC-03, SC-05, SC-07 | [timeline](features/timeline.md) |
| F-07 | タイムライン | 件数表示 | 各投稿にいいね数・コメント数を表示する | SC-03〜SC-05 | [timeline](features/timeline.md) |
| F-08 | 投稿 | 投稿作成 | テキスト（0〜280 文字）を投稿する。画像が無いときは本文必須 | SC-03 | [post](features/post.md) |
| F-09 | 投稿 | 投稿詳細表示 | 1 件の投稿とそのコメント一覧を表示する | SC-04 | [post](features/post.md) |
| F-10 | 投稿 | 投稿削除 | 自分の投稿を削除する（確認ダイアログあり）。コメント・いいね・画像も削除 | SC-03〜SC-05 | [post](features/post.md) |
| F-11 | コメント | コメント作成 | 投稿にコメント（1〜280 文字）する | SC-04 | [comment](features/comment.md) |
| F-12 | コメント | コメント一覧表示 | 投稿に付いたコメントを古い順に表示する | SC-04 | [comment](features/comment.md) |
| F-13 | コメント | コメント削除 | 自分のコメントを削除する | SC-04 | [comment](features/comment.md) |
| F-14 | いいね | いいね / 取り消し | ボタンでいいねをトグルする。押した直後に件数と表示を更新 | SC-03〜SC-05 | [like](features/like.md) |
| F-15 | いいね | 自分のいいね状態表示 | いいね済みの投稿はハートを塗りつぶしで表示 | SC-03〜SC-05 | [like](features/like.md) |
| F-16 | ユーザー | プロフィール表示 | ユーザーの情報（表示名・自己紹介・各種件数）と投稿一覧を表示する | SC-05 | [profile](features/profile.md) |
| F-17 | 投稿 | 画像添付・表示 | 投稿に画像を最大 4 枚添付する。一覧・詳細で表示し、クリックで拡大表示 | SC-03〜SC-05 | [post](features/post.md) |
| F-18 | ユーザー | プロフィール編集 | 自分の表示名・自己紹介を変更する | SC-06 | [profile](features/profile.md) |
| F-19 | フォロー | フォロー / 解除 | ボタンで他ユーザーのフォローをトグルする | SC-05, SC-07 | [follow](features/follow.md) |
| F-20 | フォロー | フォロー数・フォロワー数表示 | プロフィールにフォロー数・フォロワー数を表示する | SC-05 | [follow](features/follow.md) |
| F-21 | タイムライン | タイムライン切り替え | 「すべて / フォロー中」タブで切り替え。フォロー中はフォローしているユーザー＋自分の投稿 | SC-03 | [timeline](features/timeline.md) |
| F-22 | ユーザー | ユーザー検索 | @ユーザー名・表示名の部分一致でユーザーを探し、結果からフォローできる | SC-07 | [user-search](features/user-search.md) |
| F-23 | ユーザー | おすすめユーザー | 検索語が空のとき、未フォローのユーザーをフォロワーの多い順に最大 10 人表示する | SC-07 | [user-search](features/user-search.md) |

---

## 4. 権限マトリクス

| 操作 | 未ログイン | ログイン中（他人のデータ） | ログイン中（自分のデータ） |
| --- | --- | --- | --- |
| 新規登録・ログイン | ○ | — | — |
| タイムライン閲覧 | × | ○ | ○ |
| 投稿作成 | × | — | ○ |
| 投稿削除 | × | × | ○ |
| コメント作成 | × | ○ | ○ |
| コメント削除 | × | × | ○ |
| いいね / 取り消し | × | ○ | ○ |
| プロフィール閲覧 | × | ○ | ○ |
| プロフィール編集 | × | × | ○ |
| フォロー / 解除 | × | ○ | ×（自分自身はフォロー不可） |
| ユーザー検索・おすすめ | × | ○ | ○ |

「×」の操作は、画面でボタンを隠すだけでなく、サーバー側でも拒否する（要件定義書 5.）。

---

## 5. ルーティング一覧

### 5.1 画面の URL（フロントエンド）

| URL | 画面 | 機能 ID |
| --- | --- | --- |
| `/login` | SC-01 ログイン | F-02 |
| `/signup` | SC-02 新規登録 | F-01 |
| `/`、`/?tab=following` | SC-03 タイムライン（すべて / フォロー中） | F-05〜F-08, F-21 |
| `/posts/:id` | SC-04 投稿詳細 | F-09〜F-15 |
| `/users/:username` | SC-05 プロフィール | F-16, F-19, F-20 |
| `/settings/profile` | SC-06 プロフィール編集 | F-18 |
| `/search`、`/search?q=<検索語>` | SC-07 ユーザー検索 | F-22, F-23 |

### 5.2 API（案）

正式な定義は API 設計書（作成予定）で行う。ベースパスは `/api`。

| メソッド | パス | 内容 | 機能 ID |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | ユーザー作成（そのままログイン） | F-01 |
| POST | `/api/auth/login` | ログイン | F-02 |
| POST | `/api/auth/logout` | ログアウト | F-03 |
| GET | `/api/auth/me` | ログイン中の利用者の情報 | F-04 |
| GET | `/api/posts?tab=all\|following&before=<投稿 ID>` | タイムライン（20 件） | F-05〜F-07, F-21 |
| POST | `/api/posts` | 投稿作成（本文＋画像、`multipart/form-data`） | F-08, F-17 |
| GET | `/api/posts/{id}` | 投稿詳細 | F-09 |
| DELETE | `/api/posts/{id}` | 投稿削除 | F-10 |
| GET | `/api/posts/{id}/comments` | コメント一覧 | F-12 |
| POST | `/api/posts/{id}/comments` | コメント作成 | F-11 |
| DELETE | `/api/comments/{id}` | コメント削除 | F-13 |
| POST | `/api/posts/{id}/like` | いいね | F-14 |
| DELETE | `/api/posts/{id}/like` | いいね取り消し | F-14 |
| GET | `/api/users/{username}` | プロフィール（件数・フォロー状態を含む） | F-16, F-20 |
| GET | `/api/users/{username}/posts?before=<投稿 ID>` | プロフィールの投稿一覧（20 件） | F-16 |
| PATCH | `/api/me/profile` | 自分のプロフィールの更新 | F-18 |
| POST | `/api/users/{username}/follow` | フォロー | F-19 |
| DELETE | `/api/users/{username}/follow` | フォロー解除 | F-19 |
| GET | `/api/users/search?q=<検索語>&page=<n>` | ユーザー検索（20 件） | F-22 |
| GET | `/api/users/recommended` | おすすめユーザー（最大 10 件） | F-23 |
