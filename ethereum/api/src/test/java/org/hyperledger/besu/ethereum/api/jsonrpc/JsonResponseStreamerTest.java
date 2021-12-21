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
package org.hyperledger.besu.ethereum.api.jsonrpc;

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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;

public class JsonResponseStreamerTest {

  @Test
  public void writeSingleChar() throws IOException {
    HttpServerResponse httpResponse = mock(HttpServerResponse.class);

    JsonResponseStreamer streamer = new JsonResponseStreamer(httpResponse);
    streamer.write('x');

    verify(httpResponse).write(argThat(bufferContains("x")));
  }

  @Test
  public void writeString() throws IOException {
    HttpServerResponse httpResponse = mock(HttpServerResponse.class);

    JsonResponseStreamer streamer = new JsonResponseStreamer(httpResponse);
    streamer.write("xyz".getBytes(StandardCharsets.UTF_8));

    verify(httpResponse).write(argThat(bufferContains("xyz")));
  }

  @Test
  public void writeSubString() throws IOException {
    HttpServerResponse httpResponse = mock(HttpServerResponse.class);

    JsonResponseStreamer streamer = new JsonResponseStreamer(httpResponse);
    streamer.write("abcxyz".getBytes(StandardCharsets.UTF_8), 1, 3);

    verify(httpResponse).write(argThat(bufferContains("bcx")));
  }

  @Test
  public void writeTwice() throws IOException {
    HttpServerResponse httpResponse = mock(HttpServerResponse.class);

    JsonResponseStreamer streamer = new JsonResponseStreamer(httpResponse);
    streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
    streamer.write('\n');

    verify(httpResponse).write(argThat(bufferContains("xyz")));
    verify(httpResponse).write(argThat(bufferContains("\n")));
  }

  @Test
  public void writeStringAndClose() throws IOException {
    HttpServerResponse httpResponse = mock(HttpServerResponse.class);

    try (JsonResponseStreamer streamer = new JsonResponseStreamer(httpResponse)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
    }

    verify(httpResponse).write(argThat(bufferContains("xyz")));
    verify(httpResponse).end();
  }

  @Test
  public void waitQueueIsDrained() throws IOException {
    HttpServerResponse httpResponse = mock(HttpServerResponse.class);
    when(httpResponse.writeQueueFull()).thenReturn(Boolean.TRUE, Boolean.FALSE);

    when(httpResponse.drainHandler(any())).then(this::emptyQueueAfterAWhile);

    try (JsonResponseStreamer streamer = new JsonResponseStreamer(httpResponse)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
      streamer.write("123".getBytes(StandardCharsets.UTF_8));
    }

    verify(httpResponse).write(argThat(bufferContains("xyz")));
    verify(httpResponse).write(argThat(bufferContains("123")));
    verify(httpResponse).end();
  }

  private HttpServerResponse emptyQueueAfterAWhile(final InvocationOnMock invocation) {
    Handler<Void> handler = invocation.getArgument(0);

    Executors.newSingleThreadScheduledExecutor()
        .schedule(() -> handler.handle(null), 1, TimeUnit.SECONDS);

    return (HttpServerResponse) invocation.getMock();
  }

  private ArgumentMatcher<Buffer> bufferContains(final String text) {
    return buf -> buf.toString().equals(text);
  }
}
