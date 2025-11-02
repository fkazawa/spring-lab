
# Candidate Registry System

候補者情報を管理する業務アプリケーション。  
候補者一覧画面を中心に、CSVファイルによる一括登録（アップロード）およびエクスポート（ダウンロード）機能を提供する。

---

## 1. 概要

本システムは、人材データの管理を目的としたサンプルアプリケーションである。  
初期バージョン（MVP）は以下の機能を実装する。

- 候補者情報の一覧表示（検索・ソート・ページング対応）

- CSVアップロードによる一括登録・更新（アップサート）

- CSVダウンロードによる一覧エクスポート


> 認証・編集・削除などの機能は次フェーズで追加予定。

---

## 2. 技術スタック

|分類|使用技術|
|---|---|
|言語|Java 21|
|フレームワーク|Spring Boot 3.x|
|テンプレートエンジン|Thymeleaf + Bootstrap 5|
|データベース|MySQL 8.4|
|マイグレーション|Flyway|
|ビルド|Maven|
|テスト|JUnit 5 / Spring MockMvc|

---

## 3. ドメインモデル

### 3.1 テーブル：candidate

|カラム名|型|必須|説明|
|---|---|---|---|
|id|BIGINT (PK)|自動採番|内部ID|
|name|VARCHAR(100)|必須|候補者名|
|age|INT|任意|年齢|
|nationality|VARCHAR(50)|任意|国籍|
|origin|VARCHAR(100)|任意|出身地または所属|
|notes|TEXT|任意|備考|
|external_ref|VARCHAR(64)|ユニーク推奨|CSV連携用キー|
|created_at|TIMESTAMP|自動|登録日時|
|updated_at|TIMESTAMP|自動|更新日時|

### 3.2 SQL（Flyway V1）

```sql
CREATE TABLE candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  age INT NULL,
  nationality VARCHAR(50),
  origin VARCHAR(100),
  notes TEXT,
  external_ref VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_candidate_extref UNIQUE (external_ref)
);
```

---

## 4. CSV仕様

### 4.1 フォーマット（UTF-8, カンマ区切り, ヘッダあり）

```csv
external_ref,name,age,nationality,origin,notes
CND-001,John Doe,28,USA,New York,Transferred from another branch
CND-002,Jane Smith,31,Canada,Toronto,Has management experience
CND-003,Kai Lin,24,Japan,Osaka,Excellent adaptability
```

- **キー**：`external_ref` を自然キーとし、存在すれば更新・無ければ新規登録

- **バリデーション**

    - `name`: 必須、100文字以内

    - `age`: 整数（0〜200）

- **エラーハンドリング**

    - 行単位で検証結果を管理

    - 成功・失敗件数をJSONレスポンスで返却


### 4.2 ダウンロード

- 現在の検索条件を反映した一覧データをCSV出力

- ファイル名例：`candidate_export_YYYYMMDD_HHMMSS.csv`


---

## 5. 画面仕様

|要素|内容|
|---|---|
|URL|`/candidates`|
|機能|一覧表示、検索、ソート、ページング|
|CSV機能|アップロード／ダウンロード|
|UI構成|検索フォーム＋一覧テーブル＋操作ボタン群|
|結果表示|アップロード完了後に成功／失敗件数をモーダルで表示|

---

## 6. API / ルーティング設計

|メソッド|パス|概要|
|---|---|---|
|`GET`|`/candidates`|一覧画面（SSR）|
|`GET`|`/api/candidates`|JSON一覧（検索・ソート・ページング）|
|`POST`|`/api/candidates/csv/upload`|CSVアップロード|
|`GET`|`/api/candidates/csv/download`|CSVダウンロード|

---

## 7. セットアップ手順

```bash
# 1. プロジェクト初期化
git init

# 2. 環境変数設定
cp .env.example .env

# 3. DB起動（例：Docker）
docker compose up -d

# 4. アプリ起動
./gradlew bootRun
# または
./mvnw spring-boot:run
```

---

## 8. 運用・設計方針（MVP）

- アップロード処理は`@Service`層で実装

- トランザクション単位：アップサートごとにコミット

- ログ出力：INFOで件数・エラー情報を記録

- セキュリティ：CSRF対策、ファイルサイズ上限（5MB以下）


---

## 9. テスト観点

|項目|内容|
|---|---|
|CSV正常系|新規登録・更新・重複自然キー処理|
|CSV異常系|必須欠落・型不正・フォーマットエラー|
|検索機能|名前／国籍／出身地によるフィルタリング|
|ソート|名前・年齢昇降順の確認|
|ページング|指定件数・最終ページ処理|
|出力確認|CSVの件数・エンコーディング一致|

---

## 10. ディレクトリ構成

```
.
├── README.md
├── docs/
│   ├── csv-spec.md
│   ├── api-design.md
│   └── concept.md
├── src/
│   ├── main/java/.../candidate/
│   ├── main/resources/templates/candidates/index.html
│   ├── main/resources/db/migration/V1__create_candidate.sql
│   └── test/java/.../candidate/
└── sample/
    └── candidate_sample.csv
```

---

## 11. 今後の拡張予定

- 認証／権限管理（ログインユーザー単位での操作制限）

- 編集・詳細画面の追加

- 履歴管理（変更ログ／監査ログ）

- 多言語対応

- REST APIの一般公開（JSONエクスポート）


---

### ライセンス

MIT License（教育・PoC用途）

---
