package org.zalando.putittorest;

import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.zalando.putittorest.InstrumentedHttpAsyncClient.TimedHttpAsyncResponseConsumer.timed;
import static org.zalando.putittorest.InstrumentedHttpAsyncClientTest.response;

public class TimedHttpAsyncResponseConsumerTest {

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Test
    public void shouldDelegate() throws Exception {
        HttpAsyncResponseConsumer<?> delegate = mock(HttpAsyncResponseConsumer.class);
        final InstrumentedHttpAsyncClient.Timing timing = mock(InstrumentedHttpAsyncClient.Timing.class);

        final HttpAsyncResponseConsumer<?> unit = timed(delegate, timing);

        unit.responseReceived(mock(HttpResponse.class, RETURNS_DEEP_STUBS));
        verify(delegate).responseReceived(any(HttpResponse.class));

        unit.consumeContent(mock(ContentDecoder.class), mock(IOControl.class));
        verify(delegate).consumeContent(any(ContentDecoder.class), any(IOControl.class));

        unit.responseCompleted(mock(HttpContext.class));
        verify(delegate).responseCompleted(any(HttpContext.class));

        unit.failed(new IllegalStateException());
        verify(delegate).failed(any(IllegalStateException.class));

        unit.cancel();
        verify(delegate).cancel();

        unit.close();
        verify(delegate).close();

        unit.isDone();
        verify(delegate).isDone();

        unit.getResult();
        verify(delegate).getResult();

        unit.getException();
        verify(delegate).getException();
    }

    @Test
    public void shoudlReportStatusCode() throws Exception {
        HttpAsyncResponseConsumer<?> delegate = mock(HttpAsyncResponseConsumer.class);
        final InstrumentedHttpAsyncClient.Timing timing = mock(InstrumentedHttpAsyncClient.Timing.class);
        final HttpAsyncResponseConsumer<?> unit = timed(delegate, timing);

        unit.responseReceived(response());
        verify(timing).statusReceived(419);
    }

    @Test
    public void shoudlReportCompletion() throws Exception {
        HttpAsyncResponseConsumer<?> delegate = mock(HttpAsyncResponseConsumer.class);
        final InstrumentedHttpAsyncClient.Timing timing = mock(InstrumentedHttpAsyncClient.Timing.class);
        final HttpAsyncResponseConsumer<?> unit = timed(delegate, timing);

        unit.responseCompleted(mock(HttpContext.class));
        verify(timing).stopAndReport();
    }

}
