---
name: quality-review
description: このプロジェクトの実装を品質チェック・レビューする手順とチェックリスト。「品質チェックして」「レビューして」「標準から外れていないか見て」「文書と実装が合っているか確認して」と頼まれたとき、および PR を作る前のセルフレビューで必ず使う。機械チェック → 文書との突き合わせ → 標準からのずれ、の順で確認し、結果を 3 区分で報告する。
---

# 品質レビューの手順

このプロジェクト（Spring Boot / React + Vite / PostgreSQL 16、画像は S3）の実装を、**機械チェック → 文書との突き合わせ → 標準（デファクト）からのずれ** の順で確認する。文書と実装が食い違っていたら **文書を正** として実装を直す（文書どうしが矛盾しているときは 2. の末尾を参照）。

## 1. 機械チェックを回す（Git Bash）

```bash
# フロントエンド：lint（warning もエラー）→ Prettier → tsc → Vitest
cd frontend && npm run check

# バックエンド：Checkstyle → javac -Xlint:all -Werror → テスト（PostgreSQL が必要）
docker compose up -d                 # リポジトリルート
cd backend && ./gradlew check
```

| チェック | 合格条件 | 落ちたとき |
| --- | --- | --- |
| `oxlint --deny-warnings` | 0 件 | ルールを緩めるのではなくコードを直す。`react/exhaustive-deps` の指摘は依存配列の漏れなので必ず直す |
| `prettier --check` | 全通過 | `npm run format`。改行が原因なら 6. の「改行コード」を確認 |
| `tsc -b` | 0 件 | `any` や `!` で黙らせない |
| `vitest run` | 全通過 | 新しい振る舞いにはテストを足す。「修正前に落ち、修正後に通る」ことを確認する |
| Checkstyle | 0 件（`build/reports/checkstyle/`） | 既存コードと衝突するルールならルール側を直してよいが、`NeedBraces` / `UnusedImports` 等の明らかな改善はコードを直す |
| `-Werror` | コンパイル成功 | 警告の原因を直す。`@SuppressWarnings` は理由をコメントに書く |
| テスト（`./gradlew test`） | 全通過 | テストを消して通さない |

Docker Desktop が止まっていて DB を起動できないときは、スキル `start-dev-servers` の手順に従う。DB 無しではバックエンドのテストは実行できないので、「テストは未実行」と明記して報告する。

## 2. 文書との突き合わせ

`docs/` の各文書と実装を、次の観点で読み比べる。根拠となる文書名と章番号を指摘に添える。

| 文書 | 確認する項目 |
| --- | --- |
| [要件定義書](../../../docs/01_requirements.md) 業務ルール・決定事項・受け入れ基準 | 投稿・コメント・いいね・フォローはログインユーザーだけ。削除は作成者本人だけ（**サーバー側で権限を確認**）。いいね・フォローは 1 回まで（DB の一意制約と二重）。自分自身はフォローできない。画像のみの投稿は可。受け入れ基準の各行を実装で満たせるか |
| 要件定義書 入力制約 | 文字数（ユーザー名 3〜15・表示名 1〜30・自己紹介 0〜160・投稿 0〜280・コメント 1〜280・検索語 1〜50）を **前後の空白を除いてから** 判定しているか。空白だけの投稿・コメントを弾いているか |
| 要件定義書 非機能要件 | パスワードは BCrypt。CSRF 対策が有効。本文・自己紹介はエスケープして表示。N+1 が無い（一覧の件数・画像・いいね済みの取得）。画像はファイルの中身で形式を判定し、ランダムな名前で保存し、EXIF を消している |
| [機能別要件書](../../../docs/features/) | 各機能の振る舞い・エラー時の表示文言・受け入れ基準。特に：「フォロー中」タブの対象（フォロー中＋自分）、画像の枚数・形式・サイズ、ユーザー検索の並び順（完全一致 → 前方一致 → その他）と LIKE のワイルドカードのエスケープ、おすすめユーザーの除外条件 |
| [画面設計](../../../docs/03_screens.md) | 画面ごとの要素と動作の表。名前のクリックで SC-05 へ移り、本文のクリックとぶつからない。削除の確認ダイアログ。日時の表示（一覧は相対、詳細は絶対） |
| [ER図・テーブル定義](../../../docs/04_er_diagram.md) | Flyway のマイグレーションの結果がテーブル定義・制約（`UNIQUE`・`CHECK`・外部キーの `ON DELETE CASCADE`）・索引と一致するか |
| [技術スタック](../../../docs/05_tech_stack.md) 3.〜6. | **書かれているライブラリが実際に `build.gradle` / `package.json` に入っているか**（選定済みなのに未導入、またはその逆）。版数の記述が実物と合っているか。ポート（5173 / 8080 / 5432） |
| [API 設計書](../../../docs/08_api_design.md) | パス・メソッド・ステータスコード・要求と応答の項目名が一致するか。エラーは ProblemDetail で、項目ごとのエラーは `errors: [{field, message}]`。認証の Cookie の名前・属性（`HttpOnly`・`SameSite`・`Path`・期限）が 2.2 のとおりか |
| データ設計書・フロントエンド設計書 | 作成後に、この表へ確認する項目を追加する |

文書どうしが矛盾しているときは、**決定事項として明記されている方を正** とし、もう一方の文書を直す。文書を直したら版数・最終更新日・変更履歴の 3 つを更新する（CLAUDE.md 5.）。

## 3. バックエンドのチェックリスト（Spring Boot の標準）

- Controller は HTTP の変換だけ。業務処理とトランザクションは Service、SQL は MyBatis の Mapper（`@Mapper` のインターフェース＋ `src/main/resources/mapper/` の XML）
- SQL のパラメーターは `#{}` で渡す。`${}`（文字列の埋め込み）は SQL インジェクションになるため使わない。並び順などでどうしても必要な場合は、許可した値だけを選ぶ処理を Service に書き、理由をコメントに残す
- 一覧の件数・画像・いいね済みは 1 ページ分をまとめて取得する SQL にし、投稿ごとに Mapper を呼ぶ N+1 にしない（[ER図・テーブル定義](../../../docs/04_er_diagram.md) 3.）
- Service はクラスに `@Transactional(readOnly = true)`、書き込みメソッドだけ `@Transactional` で上書き
- 要求・応答は `record`。エンティティを直接返さない（`password_digest` などを応答に含めない）
- Bean Validation は要求 record に付け、DB の制約（`CHECK` / `NOT NULL` / `UNIQUE`）と二重にかける。正規化（strip、空白 → `null`）は検証の **前** に済ませる
- 文字数の上限・下限は `@Size` ではなく `@CodePointLength`（`common.validation`）で確かめる。`@Size` は絵文字を 2 文字と数え、文書（絵文字も 1 文字）や PostgreSQL の `VARCHAR(n)` と食い違う
- 権限の確認（削除は作成者本人だけ、プロフィール更新はログインユーザー自身だけ）は Service で行い、画面で隠すだけにしない
- エラー応答は `ProblemDetail` に統一する
- 認証（JWT）：アクセストークン・リフレッシュトークンの値を応答の本文・ログに出さない。リフレッシュトークンは DB にハッシュで保存し、更新のたびに作り直す。Cookie は `HttpOnly`。JWT の秘密鍵は本番では環境変数で渡し、ローカル用の値を本番で使わない。サーバーはセッションを作らない（`JSESSIONID` が出ていたら設定の漏れ）
- `application.properties`：`server.port` は書かない。`mybatis.configuration.map-underscore-to-camel-case=true`（列名 `display_name` → `displayName`）
- Flyway：適用済みの `V*.sql` は編集しない。定義変更は次の連番で追加
- 画像の保存先は設定で切り替える（ローカルはディスク、本番は S3）。保存処理はインターフェースの裏に隠し、Service から保存先を意識しない
- 投稿の削除で、DB の行と一緒に画像ファイル（S3 のオブジェクト）も消している
- 依存を増やすときは技術スタック 3. に行を追加する。「必要になるまで追加しない」（技術スタック 2.）
- テストは `@SpringBootTest` + MockMvc で API 単位。クラスに `@Transactional` を付けてロールバックさせ、DB にデータを残さない
- 標準から外れていても **理由がコメントに書かれていれば** 許容する。理由が無ければ指摘する

## 4. フロントエンドのチェックリスト（React の標準）

- 関数コンポーネント + hooks。`React.FC` や class は使わない。`main.tsx` は `createRoot` + `StrictMode`
- 画面の切り替えは React Router。URL（`/`、`/?tab=following`、`/posts/:id`、`/users/:username`、`/settings/profile`、`/search?q=`）が画面設計と一致する
- `useEffect` は依存配列を正しく書き（`exhaustive-deps` 0 件）、非同期処理にはクリーンアップを付ける
- 一覧の `key` は `id`。配列の添字を使わない
- 状態は最小限。状態管理ライブラリは入れない
- API 呼び出しは `src/api/` の薄い関数経由。コンポーネントから `fetch` を直接呼ばない
- 投稿本文・コメント・自己紹介を `dangerouslySetInnerHTML` で表示していない（XSS）
- a11y：失敗文言は `role="alert"`、`<label htmlFor>` + `id`、モーダル（画像ビューアー）は `role="dialog"` + `aria-modal` + `aria-labelledby`、ボタンは `<button type="button">`、画像に `alt`
- スタイルは CSS Modules
- テストは Testing Library の役割・名前（`getByRole`、`findByText`）で要素を取り、クラス名や内部状態を見ない。`fetch` は `vi.stubGlobal` で差し替える
- 設定：`.oxlintrc.json` は `react/rules-of-hooks` と `react/exhaustive-deps` を `error`、`npm run lint` は `--deny-warnings`

## 5. インフラ

インフラは案の段階（[インフラ構成（案）](../../../docs/06_infra_design.md)）。`infra/` を作ったら、TaskManagement / InquiryManagement の quality-review の「インフラのチェックリスト」を元に、この節を書く。

## 6. 横断のチェック

- **改行コード**：ルートの `.gitattributes`（`* text=auto eol=lf`）と `.editorconfig` により LF。`git ls-files --eol | grep 'w/crlf'` が `*.bat` 以外を返したら直す
- README のコマンドは Git Bash 構文（`./gradlew`、スラッシュ区切り）（CLAUDE.md 3.）
- 文書を変えたら版数・最終更新日・変更履歴（CLAUDE.md 5.）。依存や設定を足したら技術スタック文書と README も更新
- 機能 ID・画面 ID を変えたら、機能一覧・機能別要件書・画面設計・Issue テンプレートの 4 か所がそろっているか（CLAUDE.md 5.）
- 秘密情報（DB パスワード、AWS のアクセスキー）をコード・`compose.yaml`・`application.properties` に書いていない

## 7. 報告の形式

結果は次の 3 区分に分けて報告する。各項目に根拠（文書の章番号、または標準の名前）を添える。

1. **文書と実装の食い違い**（文書を正として直すもの）
2. **標準からのずれ**（直すもの／直さないもの。**直さない判断にも理由を書く**）
3. **確認して問題なしと判断したもの**（何を見て問題なしとしたかを 1 行で）

修正は CLAUDE.md 2. の開発フロー（Issue → ブランチ → PR）に従い、PR のマージはユーザーの承認を得てから行う。GitHub のリポジトリそのものは削除しない。
