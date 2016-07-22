package org.zalando.putittorest;

import com.google.common.base.Stopwatch;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.springframework.http.HttpMethod;
import org.zalando.zmon.actuator.metrics.MetricsWrapper;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static org.zalando.putittorest.InstrumentedHttpAsyncClient.TimedHttpAsyncResponseConsumer.timed;
import static org.zalando.putittorest.InstrumentedHttpAsyncClient.Timing.createStarted;

class InstrumentedHttpAsyncClient extends CloseableHttpAsyncClient {

    private final MetricsWrapper metricsWrapper;

    private final CloseableHttpAsyncClient delegate;

    public InstrumentedHttpAsyncClient(final MetricsWrapper metricsWrapper, final CloseableHttpAsyncClient delegate) {
        this.metricsWrapper = metricsWrapper;
        this.delegate = delegate;
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public <T> Future<T> execute(final HttpAsyncRequestProducer requestProducer, final HttpAsyncResponseConsumer<T> responseConsumer,
            final HttpContext context, final FutureCallback<T> callback) {

        final String method = getMethod(requestProducer);
        final String host = getHost(requestProducer);

        return delegate.execute(requestProducer, timed(responseConsumer, createStarted(metricsWrapper, method, host)), context, callback);
    }

    private String getHost(final HttpAsyncRequestProducer requestProducer) {
        return requestProducer.getTarget().toHostString();
    }

    private String getMethod(final HttpAsyncRequestProducer requestProducer) {
        final HttpRequest httpRequest;
        try {
            httpRequest = requestProducer.generateRequest();
            if (httpRequest instanceof HttpUriRequest) {
                return ((HttpUriRequest) httpRequest).getMethod();
            }
        } catch (final Exception e) {
            // ignore
        }
        return HttpMethod.GET.name();
    }

    protected static class Timing {

        static Timing createStarted(final MetricsWrapper metricsWrapper, final String method, final String host) {
            return new Timing(metricsWrapper, Stopwatch.createStarted(), method, host);
        }

        private final MetricsWrapper metricsWrapper;
        private final Stopwatch stopwatch;
        private final String method;
        private final String host;

        private Integer statusCode;

        Timing(final MetricsWrapper metricsWrapper, final Stopwatch stopwatch, final String method, final String host) {
            this.metricsWrapper = metricsWrapper;
            this.stopwatch = stopwatch;
            this.method = method;
            this.host = host;
        }

        public void statusReceived(final int statusCode) {
            this.statusCode = statusCode;
        }

        public void stopAndReport() {
            checkState(statusCode != null, "No status code received yet.");
            metricsWrapper.recordBackendRoundTripMetrics(method, host, statusCode, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    protected static class TimedHttpAsyncResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {

        static <S> HttpAsyncResponseConsumer<S> timed(final HttpAsyncResponseConsumer<S> delegate, final Timing timing) {
            return new TimedHttpAsyncResponseConsumer<>(delegate, timing);
        }

        private final HttpAsyncResponseConsumer<T> delegate;
        private final Timing timing;

        TimedHttpAsyncResponseConsumer(final HttpAsyncResponseConsumer<T> delegate, final Timing timing) {
            this.delegate = delegate;
            this.timing = timing;
        }

        @Override
        public void responseReceived(final HttpResponse response) throws IOException, HttpException {
            timing.statusReceived(response.getStatusLine().getStatusCode());
            delegate.responseReceived(response);
        }

        @Override
        public void consumeContent(final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
            delegate.consumeContent(decoder, ioctrl);
        }

        @Override
        public void responseCompleted(final HttpContext context) {
            timing.stopAndReport();
            delegate.responseCompleted(context);
        }

        @Override
        public void failed(final Exception ex) {
            delegate.failed(ex);
        }

        @Override
        public Exception getException() {
            return delegate.getException();
        }

        @Override
        public T getResult() {
            return delegate.getResult();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public boolean cancel() {
            return delegate.cancel();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
