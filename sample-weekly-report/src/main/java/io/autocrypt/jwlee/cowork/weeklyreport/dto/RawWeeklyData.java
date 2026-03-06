package io.autocrypt.jwlee.cowork.weeklyreport.dto;

/**
 * 가공되지 않은 Confluence 원본 데이터를 담는 DTO
 */
public record RawWeeklyData(
    String okrHtml,         // OKR 페이지 원본 HTML
    String meetingHtml      // 주간회의록 페이지 원본 HTML
) {}
