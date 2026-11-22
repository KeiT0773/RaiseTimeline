# frontend

RaiseTimeline の画面（React + TypeScript + Vite）。起動手順・全体の構成はリポジトリ直下の [README](../README.md) と [技術スタック](../docs/05_tech_stack.md) 4. を参照。

コマンドは Git Bash で、このディレクトリから実行する。

| コマンド         | 内容                                                                                       |
| ---------------- | ------------------------------------------------------------------------------------------ |
| `npm install`    | 依存を入れる（初回と、`package.json` が変わったとき）                                      |
| `npm run dev`    | 開発サーバーを http://localhost:5173 で起動する。`/api` はバックエンド（8080）へ転送される |
| `npm run check`  | lint（oxlint）・整形（Prettier）・型検査（tsc）・テスト（Vitest）をまとめて実行する        |
| `npm run format` | Prettier で整形する                                                                        |
| `npm run build`  | 本番用にビルドする（`dist/`）                                                              |
