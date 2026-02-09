package dev.cerios.maugame.websocket.interceptor.result;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void of_creates_success_result() throws Exception {
        Result<String, Exception> result = Result.of("ok");

        assertEquals("ok", result.getOrThrow());
    }

    @Test
    void ofError_creates_error_result() {
        Exception ex = new Exception("boom");
        Result<String, Exception> result = Result.ofError(ex);

        Exception thrown = assertThrows(Exception.class, result::getOrThrow);
        assertSame(ex, thrown);
    }

    @Test
    void map_transforms_value_when_present() throws Exception {
        Result<Integer, Exception> result =
            Result.<String, Exception>of("hello")
                .map(String::length);

        assertEquals(5, result.getOrThrow());
    }

    @Test
    void map_returns_error_when_mapper_returns_null() {
        Result<String, Exception> result =
            Result.<String, Exception>of("hello")
                .map(v -> null);

        Exception ex = assertThrows(Exception.class, result::getOrThrow);
        assertTrue(ex instanceof ResultException);
        assertTrue(ex.getMessage().contains("Value mapped from hello to null."));
    }

    @Test
    void map_does_nothing_when_value_is_absent() {
        Exception ex = new Exception("original");
        Result<String, Exception> original = Result.ofError(ex);

        Result<Integer, Exception> mapped = original.map(String::length);

        assertSame(original, mapped);
    }

    @Test
    void or_returns_self_when_value_present() throws Exception {
        Result<String, Exception> result =
            Result.<String, Exception>of("value")
                .or(() -> Optional.of("other"));

        assertEquals("value", result.getOrThrow());
    }

    @Test
    void or_uses_supplier_when_value_absent_and_optional_present() throws Exception {
        Result<String, Exception> result =
            Result.<String, Exception>ofError(new Exception("fail"))
                .or(() -> Optional.of("recovered"));

        assertEquals("recovered", result.getOrThrow());
    }

    @Test
    void or_keeps_error_when_supplier_returns_empty() {
        Exception ex = new Exception("still failing");

        Result<String, Exception> result =
            Result.<String, Exception>ofError(ex)
                .or(Optional::empty);

        Exception thrown = assertThrows(Exception.class, result::getOrThrow);
        assertSame(ex, thrown);
    }

    @Test
    void getOrThrow_returns_value_when_present() throws Exception {
        Result<Integer, Exception> result = Result.of(42);

        assertEquals(42, result.getOrThrow());
    }

    @Test
    void getOrThrow_throws_exception_when_value_absent() {
        Exception ex = new Exception("error");
        Result<Integer, Exception> result = Result.ofError(ex);

        Exception thrown = assertThrows(Exception.class, result::getOrThrow);
        assertSame(ex, thrown);
    }
}

