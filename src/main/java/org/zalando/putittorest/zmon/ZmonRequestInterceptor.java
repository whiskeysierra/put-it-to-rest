package org.zalando.putittorest.zmon;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.System.currentTimeMillis;

public class ZmonRequestInterceptor implements HttpRequestInterceptor {

    @Override
    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
        try {
            context.setAttribute(Timing.ATTRIBUTE, assemble(request));
        } catch (final Exception ignored) {
            // ignore
        }
    }

    private Timing assemble(final HttpRequest request) throws URISyntaxException {
        final RequestLine requestLine = request.getRequestLine();
        return new Timing(requestLine.getMethod(), getHost(requestLine), currentTimeMillis());
    }

    private String getHost(final RequestLine requestLine) throws URISyntaxException {
        final URI uri = new URI(requestLine.getUri());
        final int port = uri.getPort();
        return checkNotNull(uri.getHost()) + (port == -1 ? "" : ":" + port);
    }
}
