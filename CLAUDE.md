# CLAUDE.md

このファイルは、Claude Code がこのリポジトリで作業する際のガイドです。

## プロジェクト概要

Candidate Registry System — 候補者情報を管理する業務アプリケーション（MVP）。
候補者一覧画面を中心に、CSVによる一括登録（アップロード）とエクスポート（ダウンロード）を提供する。

- 詳細仕様: [README.md](README.md)
- CSV仕様（取込・出力のルール、バリデーション、エラーコード体系）: [docs/csv-spec.md](docs/csv-spec.md)

機能を追加・変更する前に、関連する仕様がこの2ファイルにないか確認すること。

## 技術スタック

| 分類 | 使用技術 |
|---|---|
| 言語 | Java 21 |
| フレームワーク | Spring Boot 3.4.0 |
| テンプレートエンジン | Thymeleaf + Bootstrap 5 |
| データベース | MySQL 8.4（開発/本番）、H2（テスト用インメモリ） |
| マイグレーション | Flyway |
| ビルド | Maven（`mvnw` を使う。Gradle wrapperは無い） |
| CSV処理 | Apache Commons CSV |
| テスト | JUnit 5 / Spring MockMvc |

## ディレクトリ構成・設計方針

```
src/main/java/com/example/candidate_registry/
├── controller/   # HTTP入出力のみ。業務ロジックを書かない
├── service/      # 業務ロジック・トランザクション制御
├── repository/   # Spring Data JPA
├── entity/       # DBエンティティ
└── dto/          # 画面/API入出力用DTO
```

- 新規機能もこの `controller / service / repository / entity / dto` の層構成に従うこと。Controllerに業務ロジックを直接書かない。
- Flywayマイグレーションは `src/main/resources/db/migration/V{n}__*.sql` に追加する。
  **既に適用済みのVファイル（例: `V1__create_candidate.sql`）は変更しない。** スキーマ変更は新しいバージョン番号のファイルを追加すること。

## 実行方法

```bash
docker compose up -d          # MySQL起動（初回のみ、以降は起動確認のみでOK）
./mvnw spring-boot:run        # アプリ起動
```

- DB接続情報は `application.properties` に直書きされていない。`docker-compose.yml` のコメントにある通り、環境変数
  `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` 経由で渡す前提。
  **接続情報やパスワードを `application.properties` やソースに直書きしないこと。**
- README には `.env.example` を使う手順が書かれているが、現状リポジトリに実体は存在しない。環境変数の管理方法を変える場合はREADMEも合わせて更新する。

## テスト方法

```bash
./mvnw test
```

- テストは `@SpringBootTest` + MockMvc + H2埋め込みDBで実行される（専用のテスト用 `application-test.properties` は無く、Spring Bootのデフォルト自動構成に依存している）。
- CSV関連の機能を変更・追加する場合は、既存の [CsvUploadMockMvcTests.java](src/test/java/com/example/candidate_registry/CsvUploadMockMvcTests.java) にある観点（正常系・部分失敗・ファイル内重複・不正CSV）に倣ってテストを追加すること。
- コードを変更したら、コミット・報告の前に必ず `./mvnw test` を実行して確認する（CIは未設定のため、ここがテストの唯一の実行機会）。

## 開発時に守るべきこと

- **CSV仕様（[docs/csv-spec.md](docs/csv-spec.md)）を正とする。** バリデーションルールやエラーコード（`REQ_MISSING` / `TYPE_MISMATCH` / `RANGE_ERROR` / `LEN_OVER` / `DUP_IN_FILE` / `UNKNOWN_HEADER` / `FILE_LIMIT` / `MALFORMED_CSV`）を変更する場合は、仕様書も同時に更新する。
- 自然キーは `external_ref`。アップサート（存在すれば更新、無ければ新規登録）の前提を崩さない。
- アップサートは行単位トランザクションとし、1行の失敗が他行に波及しない設計を維持する。
- 同一ファイル内で `external_ref` が重複した場合、該当行はすべて失敗として扱う（後勝ちにしない）。
- ログはINFOレベルで件数・エラー情報を記録する方針を踏襲する。
- CSRF対策・アップロードファイルサイズ上限（5MB）などのセキュリティ設定を緩めない。
- `id` / `created_at` / `updated_at` はシステム管理項目であり、CSV入出力の対象に含めない。

## Git運用

- ブランチ: `main`（安定）/ `develop`（開発）。作業ブランチは基本的に `develop` から切る。
- リモート: `github.com/fkazawa/spring-lab`
- CI（GitHub Actions等）は未設定。マージ前の動作確認は手動で `./mvnw test` を実行する。

## 現状の既知のギャップ

- Checkstyle / Spotless などのLintツール、Jacocoなどのカバレッジ計測は未導入。
- CI（GitHub Actions等）は未設定。
- README記載の `.env.example` は実体が存在しない。
