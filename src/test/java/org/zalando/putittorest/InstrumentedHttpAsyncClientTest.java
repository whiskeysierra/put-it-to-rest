package org.zalando.putittorest;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;
import org.zalando.zmon.actuator.metrics.MetricsWrapper;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class InstrumentedHttpAsyncClientTest {

    final MetricsWrapper metricsWrapper = mock(MetricsWrapper.class);
    final CloseableHttpAsyncClient delegate = mock(CloseableHttpAsyncClient.class);

    final HttpAsyncRequestProducer producer = mock(HttpAsyncRequestProducer.class);
    final HttpAsyncResponseConsumer<?> consumer = mock(HttpAsyncResponseConsumer.class);
    final HttpContext context = mock(HttpContext.class);
    final FutureCallback callback = mock(FutureCallback.class);

    final InstrumentedHttpAsyncClient unit = new InstrumentedHttpAsyncClient(metricsWrapper, delegate);

    @Captor
    ArgumentCaptor<HttpAsyncResponseConsumer> captor;

    @Before
    public void setUp() {
        when(producer.getTarget()).thenReturn(new HttpHost("example.com", 8080));
    }

    @Test
    public void shouldDelegate() throws IOException {
        unit.isRunning();
        verify(delegate).isRunning();

        unit.start();
        verify(delegate).start();

        unit.close();
        verify(delegate).close();

        unit.execute(producer, consumer, context, callback);
        verify(delegate).execute(argThat(is(producer)), argThat(isA(HttpAsyncResponseConsumer.class)), argThat(is(context)), argThat(is(callback)));
    }

    @Test
    public void shouldWrapTimedResponseConsumer() throws IOException, HttpException {
        unit.execute(producer, consumer, mock(HttpContext.class), callback);

        verify(delegate).execute(any(HttpAsyncRequestProducer.class), captor.capture(), any(HttpContext.class), any(FutureCallback.class));

        assertThat(captor.getValue(), is(instanceOf(InstrumentedHttpAsyncClient.TimedHttpAsyncResponseConsumer.class)));
    }

    @Test
    public void shouldRecordHost() throws IOException, HttpException {
        unit.execute(producer, consumer, mock(HttpContext.class), callback);
        completeRequest();

        verify(metricsWrapper).recordBackendRoundTripMetrics(anyString(), eq("example.com:8080"), anyInt(), anyLong());
    }

    @Test
    public void shouldRecordMethod() throws IOException, HttpException {
        final HttpUriRequest request = mock(HttpUriRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(producer.generateRequest()).thenReturn(request);

        unit.execute(producer, consumer, mock(HttpContext.class), callback);
        completeRequest();

        verify(metricsWrapper).recordBackendRoundTripMetrics(eq("POST"), anyString(), anyInt(), anyLong());
    }

    @Test
    public void shouldFallbackToGetIfUnknown() throws IOException, HttpException {
        unit.execute(producer, consumer, mock(HttpContext.class), callback);

        completeRequest();

        verify(metricsWrapper).recordBackendRoundTripMetrics(eq("GET"), anyString(), anyInt(), anyLong());
    }

    @Test
    public void shouldFallbackToGetOnError() throws IOException, HttpException {
        doThrow(new RuntimeException("expected")).when(producer).generateRequest();

        unit.execute(producer, consumer, mock(HttpContext.class), callback);

        completeRequest();

        verify(metricsWrapper).recordBackendRoundTripMetrics(eq("GET"), anyString(), anyInt(), anyLong());
    }

    @Test
    public void shouldRecordStatus() throws IOException, HttpException {
        unit.execute(producer, consumer, mock(HttpContext.class), callback);
        completeRequest();

        final int expectedStatus = response().getStatusLine().getStatusCode();
        verify(metricsWrapper).recordBackendRoundTripMetrics(anyString(), anyString(), eq(expectedStatus), anyLong());
    }

    private void completeRequest() throws IOException, HttpException {
        verify(delegate).execute(any(HttpAsyncRequestProducer.class), captor.capture(), any(HttpContext.class), any(FutureCallback.class));
        final HttpAsyncResponseConsumer value = captor.getValue();
        final HttpResponse response = response();
        value.responseReceived(response);
        value.responseCompleted(context);
    }

    public static HttpResponse response() {
        final StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(419);
        final HttpResponse response = mock(HttpResponse.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

}
