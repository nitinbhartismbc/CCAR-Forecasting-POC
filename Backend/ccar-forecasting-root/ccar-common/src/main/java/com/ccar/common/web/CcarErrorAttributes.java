package com.ccar.common.web;

import com.ccar.common.error.ErrorCode;
import com.ccar.common.error.ProblemDetailFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shapes errors that never reach a controller advice - an exception thrown inside a servlet filter, a
 * {@code response.sendError(..)}, a container-level 404 - into the same problem format, so a client sees one
 * error contract no matter where the failure happened. Rendered by Boot's {@code /error} controller.
 */
public class CcarErrorAttributes extends DefaultErrorAttributes {

    private final String serviceName;

    public CcarErrorAttributes(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> defaults = super.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
        int status = defaults.get("status") instanceof Integer s ? s : 500;
        ErrorCode code = ErrorCode.fromStatus(status);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", code.type().toString());
        body.put("title", code.title());
        body.put("status", status);
        body.put("detail", status >= 500 ? CcarExceptionHandler.GENERIC_SERVER_ERROR : code.title());
        body.put("instance", defaults.get("path"));
        body.put(ProblemDetailFactory.ERROR_CODE, code.name());
        body.put(ProblemDetailFactory.CORRELATION_ID, CorrelationIdFilter.current(webRequest));
        body.put(ProblemDetailFactory.SERVICE, serviceName);
        body.put(ProblemDetailFactory.TIMESTAMP, java.time.Instant.now().toString());
        return body;
    }
}
