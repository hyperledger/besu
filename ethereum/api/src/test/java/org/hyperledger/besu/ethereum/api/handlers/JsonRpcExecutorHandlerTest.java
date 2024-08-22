package org.hyperledger.besu.ethereum.api.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

class JsonRpcExecutorHandlerTest {

    private JsonRpcExecutor mockExecutor;
    private Tracer mockTracer;
    private JsonRpcConfiguration mockConfig;
    private RoutingContext mockContext;
    private Vertx mockVertx;
    private HttpServerResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockExecutor = mock(JsonRpcExecutor.class);
        mockTracer = mock(Tracer.class);
        mockConfig = mock(JsonRpcConfiguration.class);
        mockContext = mock(RoutingContext.class);
        mockVertx = mock(Vertx.class);
        mockResponse = mock(HttpServerResponse.class);

        when(mockContext.vertx()).thenReturn(mockVertx);
        when(mockContext.response()).thenReturn(mockResponse);
        when(mockResponse.ended()).thenReturn(false);
        when(mockResponse.setStatusCode(anyInt())).thenReturn(mockResponse);
    }

    @Test
    void testTimeoutHandling() {
        // Arrange
        Handler<RoutingContext> handler = JsonRpcExecutorHandler.handler(mockExecutor, mockTracer, mockConfig);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Handler<Long>> timerHandlerCaptor = ArgumentCaptor.forClass(Handler.class);

        when(mockContext.get(eq(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name()))).thenReturn("{}");
        when(mockVertx.setTimer(delayCaptor.capture(), timerHandlerCaptor.capture())).thenReturn(1L);
        when(mockContext.get("timerId")).thenReturn(1L);

        // Act
        handler.handle(mockContext);

        // Assert
        verify(mockVertx).setTimer(eq(30000L), any());

        // Simulate timeout
        timerHandlerCaptor.getValue().handle(1L);

        // Verify timeout handling
        verify(mockResponse, times(1)).setStatusCode(eq(HttpResponseStatus.REQUEST_TIMEOUT.code())); // Expect 408 Request Timeout
        verify(mockResponse, times(1)).end(contains("Timeout expired"));
        verify(mockVertx, times(1)).cancelTimer(1L);
    }

    @Test
    void testCancelTimerOnSuccessfulExecution() throws IOException {
        // Arrange
        Handler<RoutingContext> handler = JsonRpcExecutorHandler.handler(mockExecutor, mockTracer, mockConfig);
        when(mockContext.get(eq(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name()))).thenReturn("{}");
        when(mockVertx.setTimer(anyLong(), any())).thenReturn(1L);
        when(mockContext.get("timerId")).thenReturn(1L);

        // Act
        handler.handle(mockContext);

        // Assert
        verify(mockVertx).setTimer(anyLong(), any());
        verify(mockVertx).cancelTimer(1L);
    }
}