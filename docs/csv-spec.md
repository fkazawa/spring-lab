# CSV 取り込み・エクスポート仕様（Candidate Registry System）

本文書は、候補者情報の **CSVアップロード（取込）** および **CSVダウンロード（エクスポート）** の仕様を定義する。

* 対象画面：`/candidates`
* 対象API：

    * `POST /api/candidates/csv/upload`
    * `GET  /api/candidates/csv/download`

---

## 1. 用語定義

* **アップサート**：CSVの各行について、`external_ref` が既存レコードに一致すれば更新、一致しなければ新規作成する処理。
* **自然キー**：`external_ref`。外部システム／ファイル上で一意とすることを前提とする。
* **検証（バリデーション）**：行単位の入力チェック。検証エラーは当該行のみ取り込み失敗とする（他行には影響させない）。

---

## 2. スコープ

* 対象項目（CSV取込・出力）：

    * `external_ref`（必須／自然キー）
    * `name`（必須）
    * `age`（任意）
    * `nationality`（任意）
    * `origin`（任意）
    * `notes`（任意）
* システム管理項目（CSVには含めない／出力もしない）：`id`, `created_at`, `updated_at`

---

## 3. ファイル仕様

### 3.1 形式・エンコーディング

* 形式：**CSV（RFC 4180準拠）**
* 文字コード：**UTF-8（BOMなし）**
* 改行コード：`LF`（`\n`）
* 区切り文字：カンマ（`,`）
* ヘッダ行：**あり（必須）**

    * ヘッダ名称は「4. ヘッダ・カラム定義」に準拠すること
    * ヘッダの**順序は任意**（名称でマッピングする）

### 3.2 CSVダイアレクト（引用・エスケープ）

* フィールド内にカンマ・改行・ダブルクォートが含まれる場合、当該フィールドをダブルクォートで囲む
* ダブルクォートは二重化（`"` → `""`）してエスケープ
* 先頭・末尾の空白は取り込み時に **トリム**（全角空白もトリム対象）

### 3.3 サイズ・件数制限

* 最大ファイルサイズ：**5MB**
* 最大行数（ヘッダ除く）：**10,000行**
* 超過時は取り込み失敗（HTTP 413 / 422 相当）とし、処理を行わない

---

## 4. ヘッダ・カラム定義

### 4.1 ヘッダ一覧（論理名＝ヘッダ名）

| ヘッダ名           | 必須 | 型   | 長さ/範囲    | 例                      | 備考                 |
| -------------- | -- | --- | -------- | ---------------------- | ------------------ |
| `external_ref` | 必須 | 文字列 | 1〜64     | `CND-001`              | 自然キー。一意性を推奨        |
| `name`         | 必須 | 文字列 | 1〜100    | `Jane Smith`           | 候補者名               |
| `age`          | 任意 | 整数  | 0〜200    | `31`                   | 空ならNULL            |
| `nationality`  | 任意 | 文字列 | 0〜50     | `Japan`                | 国籍（自由入力、将来ISO化検討可） |
| `origin`       | 任意 | 文字列 | 0〜100    | `Tokyo`                | 出身地・所属など           |
| `notes`        | 任意 | 文字列 | 0〜2000目安 | `Transferred from ...` | 備考（長文可）            |

> **注**：ヘッダ名は **厳密一致**。未知ヘッダは無視する（警告に記録）。
> ヘッダの順序は任意だが、**重複ヘッダは禁止**（重複検出時はファイル全体エラー）。

---

## 5. 正常系の動作

1. ヘッダ検証（必須ヘッダの存在、重複有無）
2. 行ごとに前処理（トリム／空文字→NULL／型変換）
3. 行バリデーション（必須・長さ・型・範囲）
4. アップサート（`external_ref` で既存検索 → 更新 or 新規作成）
5. 結果サマリの生成（成功件数／失敗件数／警告件数）

* 既存→更新時は、CSVの **指定カラムのみ** 上書き（未指定カラムは現値維持）
* `age` が空文字や空白のみの場合は `NULL` として扱う

---

## 6. バリデーション仕様（行単位）

### 6.1 必須・型・範囲

* `external_ref`：未設定はエラー（`REQ_MISSING`）
* `name`：未設定はエラー（`REQ_MISSING`）
* `age`：整数以外はエラー（`TYPE_MISMATCH`）、0〜200以外はエラー（`RANGE_ERROR`）
* 文字列長：上記「4.1 ヘッダ一覧」の長さ上限を超えるとエラー（`LEN_OVER`）

### 6.2 一意性

* DB上の `external_ref` は事実上一意であることを推奨（テーブル制約：`UNIQUE`）
* 同一ファイル内で **同一 `external_ref` が複数回** 登場した場合：

    * **`DUP_IN_FILE`** として後勝ちにはせず、該当重複行を**すべて失敗**とする

### 6.3 許容・正規化

* 前後の空白（全角/半角）はトリム
* `notes` の改行は許容（RFC 4180に従い引用必須）

---

## 7. エラーハンドリング

### 7.1 行エラー（スキップ処理）

* 行単位の検証エラーは当該行のみ**取り込み失敗**としてスキップ
* スキップされた行は **エラーレポートCSV** に記録

### 7.2 ファイル全体エラー（取り込み中断）

以下は**全体エラー**として処理を中断する：

* ヘッダ不備（必須欠落、重複、空ヘッダ）
* ファイルサイズ超過・行数超過
* 解析不能なCSV（壊れた引用、文字化けの可能性など）

### 7.3 エラーコード一覧

| コード              | 意味              |
| ---------------- | --------------- |
| `REQ_MISSING`    | 必須値欠落           |
| `TYPE_MISMATCH`  | 型不一致（数値→文字列等）   |
| `RANGE_ERROR`    | 許容範囲外           |
| `LEN_OVER`       | 文字列長オーバー        |
| `DUP_IN_FILE`    | 同一ファイル内での自然キー重複 |
| `UNKNOWN_HEADER` | 未知ヘッダ（警告）       |
| `FILE_LIMIT`     | サイズ・行数の上限超過     |
| `MALFORMED_CSV`  | CSV構文不正         |

---

## 8. API仕様

### 8.1 アップロード：`POST /api/candidates/csv/upload`

* Content-Type：`multipart/form-data`
* パラメータ：`file`（CSVファイル）
* レスポンス（HTTP 200：部分成功含め成功）：

```json
{
  "totalRows": 3,
  "successCount": 2,
  "failureCount": 1,
  "warnings": [
    {"type":"UNKNOWN_HEADER","message":"Header 'foo' is ignored."}
  ],
  "errorReport": {
    "available": true,
    "downloadUrl": "/api/candidates/csv/upload/errors/20251102-093012.csv"
  }
}
```

* 全体エラー時（HTTP 400/413/422 など）：

```json
{
  "error": "FILE_LIMIT",
  "message": "File too large (max 5MB)."
}
```

#### 8.1.1 エラーレポートCSV仕様

* ヘッダ：`row_number,error_code,error_message,external_ref,name,age,nationality,origin,notes`
* 各失敗行をそのまま記録し、先頭に行番号とエラーコード／メッセージを付与

例：

```csv
row_number,error_code,error_message,external_ref,name,age,nationality,origin,notes
5,REQ_MISSING,"name is required",CND-005,,29,Japan,Tokyo,Experienced sales
7,TYPE_MISMATCH,"age must be integer",CND-007,John Doe,31.5,USA,NY,Invalid age
```

### 8.2 ダウンロード：`GET /api/candidates/csv/download`

* クエリ：現在の一覧検索条件（`name`, `nationality`, `origin`, ページング無効、全件対象）
* レスポンス：

    * Content-Type：`text/csv; charset=utf-8`
    * ファイル名例：`candidate_export_YYYYMMDD_HHMMSS.csv`
* 出力カラム（順序固定）：

    * `external_ref,name,age,nationality,origin,notes`

---

## 9. 例（サンプルCSV）

### 9.1 取り込みサンプル

```csv
external_ref,name,age,nationality,origin,notes
CND-001,Jane Smith,31,Canada,Toronto,Has management experience
CND-002,John Doe,28,USA,New York,"Transferred from ""Branch A"""
CND-003,Kai Lin,,Japan,Osaka,Excellent adaptability
```

### 9.2 ダウンロードサンプル

```csv
external_ref,name,age,nationality,origin,notes
CND-001,Jane Smith,31,Canada,Toronto,Has management experience
CND-002,John Doe,28,USA,New York,"Transferred from ""Branch A"""
```

---

## 10. トランザクション・整合性

* アップサートは **行単位トランザクション** を基本とする（1行失敗が他行に波及しない）
* 同一 `external_ref` の同時更新競合に備え、DBレベルの一意制約＋適切なロック戦略を採用
* **同一ファイル内重複**（`DUP_IN_FILE`）は該当行失敗とし、DB更新は行わない

---

## 11. セキュリティ・運用

* CSRF対策（標準設定）
* アップロード許可MIME：`text/csv`, `application/vnd.ms-excel`（実体は拡張子とシグネチャで再検証）
* ウイルススキャン（必要に応じて）
* 監査ログ：アップロード時刻、実行ユーザー、成功件数、失敗件数、元ファイル名、アップロード元IP をINFOに記録
* エラーレポートCSVは一定期間（例：24h）で削除

---

## 12. 拡張方針（次フェーズ候補）

* `nationality` の標準化（ISO 3166-1 alpha-2/alpha-3 へのマッピング）
* **Dry-runモード**（検証のみでDB更新なし）
* 取り込みジョブのキューイング化（大容量ファイル対応）
* 変更履歴テーブル（監査強化）
* 多言語化（エラーメッセージのロケール対応）

---

## 13. 受け入れ条件（抜粋）

* ヘッダに `external_ref` と `name` が含まれないCSVは全体エラーで中断されること
* 同一ファイル内で `external_ref` が重複する行は、すべて失敗として記録されること
* 部分的に不正な行が含まれても、正しい行は取り込まれること
* ダウンロードCSVは、画面の検索条件を反映した全件が出力されること
* 5MB超過のファイルは受け付けられないこと

---
