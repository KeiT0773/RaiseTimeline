package com.raisetimeline.backend.common;

/**
 * 項目ごとのエラー（API 設計書 2.4 の errors の 1 要素）。
 *
 * @param field   要求の項目名（例：username）
 * @param message 画面にそのまま表示できる文言
 */
public record FieldErrorResponse(String field, String message) {
}
