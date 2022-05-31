/*
 * Copyright Hyperledger Besu contributors
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.impl.future.SucceededFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JsonResponseStreamerTest {

  @Mock private ServerWebSocket response;

  @Mock private ServerWebSocket failedResponse;

  @Before
  public void before() {
    when(response.writeFrame(any(WebSocketFrame.class)))
        .thenReturn(new SucceededFuture<>(null, null));
    when(failedResponse.writeFrame(any(WebSocketFrame.class)))
        .thenReturn(new FailedFuture<Void>(new IOException()));
  }

  @Test
  public void writeSingleChar() throws IOException {
    try (JsonResponseStreamer streamer = new JsonResponseStreamer(response)) {
      streamer.write('x');
    }

    verify(response).writeFrame(argThat(frameContains("x", true)));
  }

  @Test
  public void writeString() throws IOException {
    try (JsonResponseStreamer streamer = new JsonResponseStreamer(response)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8), 0, 3);
    }

    verify(response).writeFrame(argThat(frameContains("xyz", true)));
  }

  @Test
  public void writeSubString() throws IOException {
    try (JsonResponseStreamer streamer = new JsonResponseStreamer(response)) {
      streamer.write("abcxyz".getBytes(StandardCharsets.UTF_8), 1, 3);
    }

    verify(response).writeFrame(argThat(frameContains("bcx", true)));
  }

  @Test
  public void writeTwice() throws IOException {
    try (JsonResponseStreamer streamer = new JsonResponseStreamer(response)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
      streamer.write('\n');
    }

    verify(response).writeFrame(argThat(frameContains("xyz", false)));
    verify(response).writeFrame(argThat(frameContains("\n", true)));
  }

  @Test
  public void stopOnError() throws IOException {
    try (JsonResponseStreamer streamer = new JsonResponseStreamer(failedResponse)) {
      streamer.write("xyz".getBytes(StandardCharsets.UTF_8));
      streamer.write('\n');
      assertThatThrownBy(() -> streamer.write('\n')).isInstanceOf(IOException.class);
    }

    verify(failedResponse).writeFrame(argThat(frameContains("xyz", false)));
    verify(failedResponse, never()).writeFrame(argThat(frameContains("\n", true)));
  }

  private ArgumentMatcher<WebSocketFrame> frameContains(final String text, final boolean isFinal) {
    return frame -> frame.textData().equals(text) && frame.isFinal() == isFinal;
  }
}
