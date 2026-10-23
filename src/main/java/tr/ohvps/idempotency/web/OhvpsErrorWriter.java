package tr.ohvps.idempotency.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.util.UrlPathHelper;

/**
 * Writes errors produced by the filter itself in the ÖHVPS error body format.
 * The error codes are examples and should be mapped to the HHS's own error catalogue.
 */
public class OhvpsErrorWriter {

    private final Clock clock;

    public OhvpsErrorWriter(Clock clock) {
        this.clock = clock;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String errorCode, String moreInformation, String moreInformationTr) throws IOException {
        String body = """
                {"path":"%s","timestamp":"%s","httpCode":%d,"httpMessage":"%s",\
                "moreInformation":"%s","moreInformationTr":"%s","errorCode":"%s"}"""
                .formatted(
                        escape(UrlPathHelper.defaultInstance.getPathWithinApplication(request)),
                        Instant.now(clock),
                        status.value(),
                        escape(status.getReasonPhrase()),
                        escape(moreInformation),
                        escape(moreInformationTr),
                        escape(errorCode));

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
