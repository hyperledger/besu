/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;

public class JsonResponseStreamerTest {

  @Test
  public void writeSingleChar() throws IOException {
    final ServerWebSocket response = mock(ServerWebSocket.class);

    try (WebSocketRequestHandler.JsonResponseStreamer streamer =
        new WebSocketRequestHandler.JsonResponseStreamer(response)) {
      streamer.write('x');
    }

    verify(response).writeFrame(argThat(frameContains("x", true)));
  }

  @Test
  public void writeString() throws IOException {
    final ServerWebSocket response = mock(ServerWebSocket.class);

    try (WebSocketRequestHandler.JsonResponseStreamer streamer =
        new WebSocketRequestHandler.JsonResponseStreamer(response)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8), 0, 3);
    }

    verify(response).writeFrame(argThat(frameContains("xyz", true)));
  }

  @Test
  public void writeSubString() throws IOException {
    final ServerWebSocket response = mock(ServerWebSocket.class);

    try (WebSocketRequestHandler.JsonResponseStreamer streamer =
        new WebSocketRequestHandler.JsonResponseStreamer(response)) {
      streamer.write("abcxyz".getBytes(StandardCharsets.UTF_8), 1, 3);
    }

    verify(response).writeFrame(argThat(frameContains("bcx", true)));
  }

  @Test
  public void writeTwice() throws IOException {
    final ServerWebSocket response = mock(ServerWebSocket.class);

    try (WebSocketRequestHandler.JsonResponseStreamer streamer =
        new WebSocketRequestHandler.JsonResponseStreamer(response)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
      streamer.write('\n');
    }

    verify(response).writeFrame(argThat(frameContains("xyz", false)));
    verify(response).writeFrame(argThat(frameContains("\n", true)));
  }

  @Test
  public void waitQueueIsDrained() throws IOException {
    final ServerWebSocket response = mock(ServerWebSocket.class);

    when(response.writeQueueFull()).thenReturn(Boolean.TRUE, Boolean.FALSE);

    when(response.drainHandler(any())).then(this::emptyQueueAfterAWhile);

    try (WebSocketRequestHandler.JsonResponseStreamer streamer =
        new WebSocketRequestHandler.JsonResponseStreamer(response)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
      streamer.write("123".getBytes(StandardCharsets.UTF_8));
    }

    verify(response).writeFrame(argThat(frameContains("xyz", false)));
    verify(response).writeFrame(argThat(frameContains("123", true)));
  }

  private ServerWebSocket emptyQueueAfterAWhile(final InvocationOnMock invocation) {
    Handler<Void> handler = invocation.getArgument(0);

    Executors.newSingleThreadScheduledExecutor()
        .schedule(() -> handler.handle(null), 1, TimeUnit.SECONDS);

    return (ServerWebSocket) invocation.getMock();
  }

  private ArgumentMatcher<WebSocketFrame> frameContains(final String text, final boolean isFinal) {
    return frame -> frame.textData().equals(text) && frame.isFinal() == isFinal;
  }
}
