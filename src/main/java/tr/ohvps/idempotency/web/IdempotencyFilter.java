package tr.ohvps.idempotency.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import tr.ohvps.idempotency.checksum.ChecksumGenerator;
import tr.ohvps.idempotency.config.IdempotencyProperties;
import tr.ohvps.idempotency.store.IdempotencyStore;
import tr.ohvps.idempotency.store.Reservation;
import tr.ohvps.idempotency.store.StoredResponse;

/**
 * ÖHVPS idempotency kuralını kapsamdaki POST uç noktalarına uygular:
 * <ol>
 *     <li>{@code x-request-id|request.body} birleşiminden checksum üretilir.</li>
 *     <li>Checksum için saklanan yanıt varsa istek yeniden işlenmeden o yanıt döner.</li>
 *     <li>Yoksa istek işlenir ve YÖS'e dönen yanıt TTL (5 dakika) boyunca saklanır.</li>
 * </ol>
 * Aynı istek henüz işlenirken gelen kopya, ilk işlemin yanıtını bekler; böylece iş
 * mantığı eşzamanlı kopyalar için de bir kez çalışır.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private static final Set<String> NON_REPLAYABLE_HEADERS = Set.of(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase());

    private final IdempotencyProperties properties;
    private final ChecksumGenerator checksumGenerator;
    private final IdempotencyStore store;
    private final OhvpsErrorWriter errorWriter;
    private final List<PathPattern> pathPatterns;

    public IdempotencyFilter(IdempotencyProperties properties, ChecksumGenerator checksumGenerator,
                             IdempotencyStore store, OhvpsErrorWriter errorWriter) {
        this.properties = properties;
        this.checksumGenerator = checksumGenerator;
        this.store = store;
        this.errorWriter = errorWriter;
        this.pathPatterns = properties.paths().stream()
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || !HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        PathContainer path = PathContainer.parsePath(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
        return pathPatterns.stream().noneMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(properties.requestIdHeader());
        if (!StringUtils.hasText(requestId)) {
            errorWriter.write(request, response, HttpStatus.BAD_REQUEST,
                    "TR.OHVPS.Idempotency.MissingRequestId",
                    properties.requestIdHeader() + " header is required",
                    properties.requestIdHeader() + " başlığı zorunludur");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String key = checksumGenerator.generate(requestId, cachedRequest.getBody());

        while (true) {
            Reservation reservation = store.reserve(key);
            if (reservation instanceof Reservation.Acquired) {
                processAndStore(cachedRequest, response, chain, key);
                return;
            }

            try {
                StoredResponse stored = ((Reservation.Existing) reservation).response()
                        .get(properties.inFlightWait().toMillis(), TimeUnit.MILLISECONDS);
                log.debug("Saklanan yanıt dönülüyor, x-request-id={}, checksum={}", requestId, key);
                replay(stored, response);
                return;
            } catch (ExecutionException released) {
                // İlk işlem yanıt saklamadan bitti; isteği bu çağrı işlemeyi dener.
                log.debug("Rezervasyon bırakıldı, yeniden deneniyor, checksum={}", key);
            } catch (TimeoutException e) {
                errorWriter.write(request, response, HttpStatus.CONFLICT,
                        "TR.OHVPS.Idempotency.RequestInProgress",
                        "A request with the same x-request-id and body is still being processed",
                        "Aynı istek numarası ve gövdeye sahip istek hâlâ işleniyor");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServletException("Saklanan yanıt beklenirken kesildi", e);
            }
        }
    }

    private void processAndStore(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                 String key) throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(request, responseWrapper);
            if (isStorable(responseWrapper.getStatus())) {
                store.complete(key, capture(responseWrapper));
                stored = true;
            }
        } finally {
            if (!stored) {
                store.release(key);
            }
        }
        responseWrapper.copyBodyToResponse();
    }

    private boolean isStorable(int status) {
        return properties.storeServerErrors() || !HttpStatus.valueOf(status).is5xxServerError();
    }

    private static StoredResponse capture(ContentCachingResponseWrapper response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            if (!NON_REPLAYABLE_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, new ArrayList<>(response.getHeaders(name)));
            }
        }
        if (response.getContentType() != null) {
            headers.putIfAbsent(HttpHeaders.CONTENT_TYPE, List.of(response.getContentType()));
        }
        return new StoredResponse(response.getStatus(), headers, response.getContentAsByteArray());
    }

    private void replay(StoredResponse stored, HttpServletResponse response) throws IOException {
        byte[] body = stored.body();
        response.setStatus(stored.status());
        stored.headers().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        if (StringUtils.hasText(properties.replayIndicatorHeader())) {
            response.setHeader(properties.replayIndicatorHeader(), "true");
        }
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
