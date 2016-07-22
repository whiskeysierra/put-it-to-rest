package org.zalando.putittorest;

import org.junit.Test;
import org.zalando.zmon.actuator.metrics.MetricsWrapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.zalando.putittorest.InstrumentedHttpAsyncClient.Timing.createStarted;

public class TimingTest {

    @Test
    public void shouldStartStopwatchOnCreateStarted() throws InterruptedException {
        final MetricsWrapper metricsWrapper = mock(MetricsWrapper.class);

        final InstrumentedHttpAsyncClient.Timing timing = createStarted(metricsWrapper, "GET", "host");
        Thread.sleep(2);
        timing.statusReceived(200);
        timing.stopAndReport();

        verify(metricsWrapper).recordBackendRoundTripMetrics(anyString(), anyString(), anyInt(), longThat(is(greaterThan(0L))));
    }

    @Test
    public void shouldReportMethodAndHost() {
        final MetricsWrapper metricsWrapper = mock(MetricsWrapper.class);

        final InstrumentedHttpAsyncClient.Timing timing = createStarted(metricsWrapper, "OPTIONS", "host");
        timing.statusReceived(200);
        timing.stopAndReport();

        verify(metricsWrapper).recordBackendRoundTripMetrics(eq("OPTIONS"), eq("host"), anyInt(), anyLong());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailWithoutStatus() {
        createStarted(mock(MetricsWrapper.class), "OPTIONS", "host").stopAndReport();
    }
}
