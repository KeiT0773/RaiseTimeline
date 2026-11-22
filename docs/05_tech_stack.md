# RaiseTimeline 技術スタック

## 1. 文書情報

| 項目 | 内容 |
| --- | --- |
| 文書名 | RaiseTimeline 技術スタック |
| 版数 | 2.1 |
| 作成日 | 2026-11-15 |
| 最終更新日 | 2026-11-22 |
| ステータス | 作成中 |
| 上位文書 | [要件定義書](01_requirements.md) |
| 関連文書 | [機能一覧](02_features.md)、[画面設計](03_screens.md)、[ER図・テーブル定義](04_er_diagram.md)、[インフラ構成（案）](06_infra_design.md) |

本書は、要件定義書のシステム構成で「技術スタックに定める」とした、各層で使用する技術（プログラミング言語、フレームワーク、データベース製品、周辺ツール）を定義する。

各技術の版数は、本プロジェクトの雛形を作成した時点（2026-11-22）に導入した版を記す。バックエンドは `backend/build.gradle`、フロントエンドは `frontend/package.json` が正で、版を上げたら本書も更新する。

### 1.1 変更履歴

| 版数 | 日付 | 変更内容 |
| --- | --- | --- |
| 1.0 | 2026-11-15 | 初版作成。TaskManagement と同じ React + Spring Boot + PostgreSQL の構成とし、本プロジェクトで新たに必要なもの（認証、画像の保存、画面の切り替え）を追加 |
| 2.0 | 2026-11-22 | DB アクセスを Spring Data JPA から MyBatis に変更。雛形の作成に合わせて、各技術の版数を目安から導入した版に確定 |
| 2.1 | 2026-11-22 | ログイン状態をセッションから JWT（HttpOnly Cookie）に変更。3. の「認証・認可」を書き換え、「JWT の発行・検証」を追加。8. No.2（セッションの保存先）を不要として削除 |

---

## 2. 選定方針

- フロントエンドは **React（Vite）**、バックエンドは **Java + Spring Boot**、データベースは **PostgreSQL** を使用する（プロジェクト方針として指定）。
- 画像ファイルの保存先は **Amazon S3** とする（プロジェクト方針として指定。[インフラ構成（案）](06_infra_design.md)）。
- 周辺ツールは、上記のフレームワーク・言語と組み合わせるうえで標準的で、学習資料が豊富なものを選ぶ。フレームワークの雛形に同梱されているものがあれば、それを優先して使う。
- 本プロジェクトは Web アプリケーション開発の学習も目的とする（要件定義書 1.）ため、構成をなるべく単純に保ち、必要になるまでライブラリを追加しない。

### 2.1 先行プロジェクトとの違い

TaskManagement と同じ技術を使い、同じ技術でより複雑なアプリ（ログイン、多対多のリレーション、ファイルのアップロード、複数画面）を作ることを学ぶ。

| 項目 | TaskManagement | InquiryManagement | 本プロジェクト |
| --- | --- | --- | --- |
| フロントエンド | React + Vite | Next.js | **React + Vite**（＋ React Router） |
| バックエンド | Java + Spring Boot | Ruby on Rails（API モード） | **Java + Spring Boot**（＋ Spring Security） |
| DB アクセス | Spring Data JPA | Active Record | **MyBatis**（SQL を XML に直接書く） |
| データベース | PostgreSQL | MySQL | **PostgreSQL** |
| 利用者の認証 | なし（個人用） | なし（チームで共有） | **あり**（ユーザー登録・ログイン） |
| 画面の数 | 1（＋詳細の重ね表示） | 1（＋詳細の重ね表示） | **7**（URL で切り替え） |
| ファイルの保存 | なし | なし | **あり**（投稿画像。S3） |
| サーバー構成 | CloudFront + S3 / ECS on Fargate / RDS | CloudFront + EC2 + RDS | **案：ALB + EC2 + RDS + S3**（[インフラ構成（案）](06_infra_design.md)） |

---

## 3. バックエンド

| 項目 | 選定 | 役割・選定理由 |
| --- | --- | --- |
| 言語 | Java 21（LTS） | 長期サポート版であり、教材・記事が最も多い。TaskManagement と同じ |
| フレームワーク | Spring Boot 4.1.3 | プロジェクト方針として指定。雛形は Spring Initializr で作成した。4.2 系が出ているが、MyBatis の Spring Boot 対応版（4.1.0）が 4.1 向けのため 4.1 系にそろえる |
| ビルドツール | Gradle 9.9.2（Gradle Wrapper） | ソースのコンパイル、依存ライブラリの取得、実行ファイルの作成を行う。Wrapper を同梱するため Gradle 本体のインストールは不要 |
| REST API | Spring Web | フロントエンドからの HTTP 要求を受け付け、JSON で応答する。画像のアップロードは `multipart/form-data` で受け取る（Spring Web の標準機能） |
| 認証・認可 | **Spring Security**（7.1 系） | パスワードのハッシュ化（BCrypt。`BCryptPasswordEncoder`）、ログイン時のパスワード照合（`AuthenticationManager`）、CSRF 対策（`csrf.spa()`）、未ログイン時のアクセス拒否（F-04）を担う。要件定義書 非機能要件の「セキュリティ」「認証」を、自作せずにフレームワークの標準機能で満たすため。Spring Boot のスターター（`spring-boot-starter-security`）で入る |
| JWT の発行・検証 | **Spring Security OAuth2 Resource Server**（`spring-boot-starter-oauth2-resource-server`。内部で Nimbus JOSE + JWT を使う） | ログイン状態はサーバーのセッションではなく JWT で持つ（[auth](features/auth.md) 3.）。アクセストークン（JWT、15 分）の署名（HS256）と発行に `JwtEncoder`、要求ごとの検証に `JwtDecoder` と `BearerTokenAuthenticationFilter` を使う。署名・検証を自作しないため。トークンは HttpOnly の Cookie で受け渡す。リフレッシュトークン（14 日）は JWT ではなくランダムな値で、DB（refresh_tokens）にハッシュで保存する。サーバーがセッションを持たないので、サーバーを複数台にしてもログイン状態の共有は要らない |
| DB アクセス | **MyBatis**（`mybatis-spring-boot-starter` 4.1.0。MyBatis 3.5 系） | SQL を `src/main/resources/mapper/` の XML に直接書き、Java 側は `@Mapper` のインターフェースだけを置く。件数の集計・フォロー中タイムライン・ユーザー検索のように JOIN やサブクエリを使う SQL（[ER図・テーブル定義](04_er_diagram.md) 3.）をほぼそのまま書け、どの SQL が実行されるかがコードから読み取れるため。1 ページ分をまとめて取る SQL を書き、N+1 を避ける。パラメーターは `#{}`（プレースホルダ）で渡し、`${}`（文字列の埋め込み）は使わない（SQL インジェクション対策）。Spring Boot の BOM では版数が管理されないため `build.gradle` で指定する |
| 入力チェック | Bean Validation | 「投稿は 0〜280 文字」「ユーザー名は英数字とアンダースコアで 3〜15 文字」などの検証をアノテーションで宣言する |
| DB マイグレーション | Flyway 12.4 | テーブル定義の変更を SQL ファイルとして履歴管理し、アプリ起動時に自動で DB へ反映する。Spring Boot 4 では `spring-boot-starter-flyway` と `flyway-database-postgresql` を依存に加える（TaskManagement で確認済み） |
| 画像の保存（本番） | **AWS SDK for Java 2.x（S3）** | 投稿画像を S3 に保存・削除し、表示用の署名付き URL（期限つきの URL）を発行する。AWS の公式 SDK で、必要な S3 のモジュールだけを依存に加える |
| 画像の保存（開発時） | ローカルのディスク（`backend/storage/`） | 開発時は AWS に接続せずに動かせるよう、保存先を設定（Spring のプロファイル）で切り替える。保存処理はインターフェースにし、ディスク用と S3 用の 2 つの実装を用意する |
| 画像の処理 | 保留（8. No.1） | EXIF（位置情報など）の除去と、サムネイル（縮小版）の作成に使う。候補は Java 標準の `ImageIO` と、その上で動く Thumbnailator（縮小）・TwelveMonkeys ImageIO（WebP の読み込み） |
| コード検査 | Checkstyle + javac `-Xlint:all -Werror` | TaskManagement と同じ構成。ルールファイルも TaskManagement の `backend/config/checkstyle/checkstyle.xml` を流用する。`./gradlew check` で実行される |
| テスト | JUnit 5 + Spring Boot Test + Spring Security Test + MyBatis Spring Boot Test | Spring Boot に同梱（MyBatis のテスト用スターターは MyBatis の Mapper 単位のテスト用）。ログインが必要な API のテストには Spring Security Test（`@WithMockUser` など）を使う。テストは compose の PostgreSQL に接続して実行する |

---

## 4. フロントエンド

| 項目 | 選定 | 役割・選定理由 |
| --- | --- | --- |
| ライブラリ | React 19.3 | プロジェクト方針として指定 |
| 言語 | TypeScript 7.0（Vite の雛形に合わせる） | API がやり取りするデータ（投稿・ユーザー・コメント）の形を型として定義でき、バックエンドとの食い違いを早期に検出できる |
| ビルド・開発サーバー | Vite 8.3 | `react-ts` テンプレートで雛形を生成する。TaskManagement と同じ |
| 画面の切り替え | **React Router 7.18** | 画面が 7 つあり、それぞれに URL がある（`/posts/:id`、`/users/:username`、`/search?q=` など。[画面設計](03_screens.md) 1.）。ブラウザの戻る・進む、URL の共有、再読み込みで同じ画面に戻ることを実現するため。React で最も標準的なルーター |
| バックエンドへの接続（開発時） | Vite の開発サーバーのプロキシ | `/api` で始まる要求を `http://localhost:8080` へ転送する。画面と API が同じオリジンになるため、ログインの Cookie と CSRF のトークンをそのまま扱え、CORS の設定も不要。本番も同じオリジンで配信する（[インフラ構成（案）](06_infra_design.md)） |
| サーバー通信 | fetch API（ブラウザ標準） | 追加ライブラリは入れない。画像の送信は `FormData` を使う |
| スタイル | CSS（CSS Modules、Vite 同梱） | UI ライブラリや Tailwind CSS は使わない。TaskManagement と同じ |
| コード検査・整形 | oxlint + Prettier 3 | Vite の雛形に同梱される oxlint をそのまま使い、整形に Prettier を追加する。設定は TaskManagement と同じ（`react/exhaustive-deps` を `error`、`--deny-warnings`）。`npm run check` で lint・整形・型検査・テストを一括実行する |
| テスト | Vitest 5 + React Testing Library | Vite と相性のよい標準的な組み合わせ。DOM の再現には jsdom を使う |
| パッケージ管理 | npm | Node.js に同梱 |

---

## 5. データベース

| 項目 | 選定 | 役割・選定理由 |
| --- | --- | --- |
| 製品 | PostgreSQL 16 系 | プロジェクト方針として指定。TaskManagement と同じ。ユーザー検索の部分一致に `ILIKE`（大文字と小文字を区別しない LIKE）が使え、データが増えたときは拡張機能 `pg_trgm` で部分一致を速くできる（[ER図・テーブル定義](04_er_diagram.md) 3.） |
| 文字コード | UTF-8 | PostgreSQL の既定。日本語と絵文字を保存できる。`VARCHAR(n)` の n はバイト数ではなく文字数 |
| 日時の型 | `TIMESTAMPTZ`（タイムゾーン付き） | 保存は UTC、表示は日本時間に変換する。相対表示（「3分前」）の計算がずれないようにするため |
| ローカルでの起動 | Docker Desktop + Docker Compose | PC に PostgreSQL を直接インストールせず、コマンド一つで起動・破棄できる。公式イメージ `postgres:16` を使う |

---

## 6. 開発環境

| 項目 | 選定 | 備考 |
| --- | --- | --- |
| OS | Windows 11 | |
| エディタ | Cursor | Java / Spring Boot 用の拡張機能を追加する |
| シェル | Git Bash | ユーザーが打つコマンドは Git Bash 構文で書く（CLAUDE.md 3.） |
| 必要なインストール | JDK 21、Node.js 24（LTS）、Docker Desktop | TaskManagement と同じ。導入済みのものはそのまま使う |
| バージョン管理 | Git + GitHub | リポジトリは [KeiT0773/RaiseTimeline](https://github.com/KeiT0773/RaiseTimeline)（[開発フロー](07_development_workflow.md)） |

### 6.1 開発時のポート

| サーバー | ポート | 根拠 |
| --- | --- | --- |
| フロントエンド（Vite） | 5173 | Vite の既定。`vite.config.ts` に `server.port: 5173, strictPort: true` を書く |
| バックエンド（Spring Boot） | 8080 | Spring Boot の既定。`application.properties` に `server.port` は書かない |
| PostgreSQL（Docker） | 5432 | PostgreSQL の既定 |

ポートが使用中でも別のポートへ逃げない、という規則（CLAUDE.md 6.）を適用する。フック（`.claude/hooks/guard-ports.ps1`）が、別のポートで起動するコマンドを拒否する。TaskManagement と同じポートのため、**両方のプロジェクトを同時には起動できない**。もう一方のサーバーが動いていたら、ユーザーに確認してから止める（スキル `start-dev-servers`）。

---

## 7. リポジトリ構成

1 つのリポジトリにドキュメント・バックエンド・フロントエンドをまとめて管理する。

```
RaiseTimeline/
├── .claude/       Claude Code の設定（フック・スキル）
├── .github/       Issue・PR のテンプレート
├── docs/          要件定義書・設計書
│   └── features/  機能別要件書
├── backend/       Spring Boot アプリケーション
├── frontend/      React アプリケーション
├── infra/         AWS の構成（作成するかは未定）
└── compose.yaml   ローカル開発用の PostgreSQL
```

---

## 8. 決定を保留する事項

| No | 事項 | 現時点の考え | 決める時期 |
| --- | --- | --- | --- |
| 1 | 画像の処理（EXIF の除去・サムネイルの作成）に使うライブラリ | Java 標準の `ImageIO` で読み込んで書き出し直すと EXIF は消える。縮小は Thumbnailator、WebP の読み込みは TwelveMonkeys ImageIO を候補とする。`ImageIO` は WebP の書き出しに対応していないため、WebP は JPEG に変換して保存する案が有力 | 投稿機能（F-17）の実装前 |
| 2 | 画像の表示方法 | S3 を非公開にし、バックエンドが発行する署名付き URL（期限つきの URL）で表示する案。CloudFront を置く場合は CloudFront 経由で配信する案もある | インフラの構成を決めるとき |
| 3 | 本番の配置先 | AWS でサーバーを構築するかは未決定。構築する場合の案は [インフラ構成（案）](06_infra_design.md) | ローカルでの実装が一通り終わったあと |
