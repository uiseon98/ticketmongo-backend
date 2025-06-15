package com.team03.ticketmon._global.util;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {

    @Test
    void testToKstDateString() {
        ZonedDateTime dateTime = ZonedDateTime.of(2025, 6, 14, 9, 0, 0, 0, ZoneOffset.UTC);
        String result = TimeUtil.toKstDateString(dateTime);
        assertEquals("2025-06-14", result);
    }

    @Test
    void testToKstDateTimeString() {
        ZonedDateTime dateTime = ZonedDateTime.of(2025, 6, 14, 9, 0, 0, 0, ZoneOffset.UTC);
        String result = TimeUtil.toKstDateTimeString(dateTime);
        assertEquals("2025-06-14 18:00:00", result); // UTC+9
    }

    @Test
    void testToIso8601KstString() {
        ZonedDateTime dateTime = ZonedDateTime.of(2025, 6, 14, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        String result = TimeUtil.toIso8601KstString(dateTime);
        assertEquals("2025-06-14T00:00:00+09:00", result);
    }

    @Test
    void testFromIso8601ToKst() {
        String iso = "2025-06-13T15:00:00Z"; // UTC 기준
        ZonedDateTime result = TimeUtil.fromIso8601ToKst(iso);
        assertEquals(2025, result.getYear());
        assertEquals(6, result.getMonthValue());
        assertEquals(14, result.getDayOfMonth()); // +9시간 반영
        assertEquals(0, result.getHour());
        assertEquals(ZoneId.of("Asia/Seoul"), result.getZone());
    }

    @Test
    void testToIso8601UtcString() {
        ZonedDateTime dateTime = ZonedDateTime.of(2025, 6, 14, 0, 0, 0, 0, ZoneOffset.UTC);
        String result = TimeUtil.toIso8601String(dateTime);
        assertEquals("2025-06-14T00:00:00Z", result);
    }

    @Test
    void testFromIso8601UtcString() {
        String iso = "2025-06-14T00:00:00Z";
        ZonedDateTime result = TimeUtil.fromIso8601String(iso);
        assertEquals(2025, result.getYear());
        assertEquals(6, result.getMonthValue());
        assertEquals(14, result.getDayOfMonth());
        assertEquals(0, result.getHour());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }
}