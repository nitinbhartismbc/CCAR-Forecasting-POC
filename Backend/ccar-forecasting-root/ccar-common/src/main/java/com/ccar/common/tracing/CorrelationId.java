package com.ccar.common.tracing;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation-id conventions shared by every CCAR service.
 * <ul>
 *   <li>Header {@value #HEADER}: accepted from the caller (UI / gateway), generated when absent or malformed,
 *       forwarded to every downstream call and echoed on every response (including error responses).</li>
 *   <li>SLF4J MDC key {@value #MDC_KEY}: every log line carries it via {@code %X{correlationId}}.</li>
 *   <li>Problem responses expose it as the {@code correlationId} member, so a user can quote it.</li>
 * </ul>
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    /** Request / exchange attribute holding the id, readable after the MDC was cleared (e.g. error dispatch). */
    public static final String ATTRIBUTE = CorrelationId.class.getName();

    /**
     * Accept only short, log-safe ids: an attacker-controlled header must not be able to forge log lines
     * (CR/LF injection) or bloat every log line.
     */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private CorrelationId() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }

    /** The incoming id when it is valid, otherwise a freshly generated one. */
    public static String resolve(String incoming) {
        return isValid(incoming) ? incoming : newId();
    }
}
