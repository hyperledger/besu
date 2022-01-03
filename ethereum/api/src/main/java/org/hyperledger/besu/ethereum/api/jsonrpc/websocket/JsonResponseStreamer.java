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

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class JsonResponseStreamer extends OutputStream {

  private static final Logger LOG = LogManager.getLogger();
  private static final Buffer EMPTY_BUFFER = Buffer.buffer();

  private final ServerWebSocket response;
  private final Semaphore paused = new Semaphore(0);
  private final byte[] singleByteBuf = new byte[1];
  private boolean firstFrame = true;
  private Buffer buffer = EMPTY_BUFFER;

  public JsonResponseStreamer(final ServerWebSocket response) {
    this.response = response;
  }

  @Override
  public void write(final int b) throws IOException {
    singleByteBuf[0] = (byte) b;
    write(singleByteBuf, 0, 1);
  }

  @Override
  public void write(final byte[] bbuf, final int off, final int len) throws IOException {
    if (buffer != EMPTY_BUFFER) {
      writeFrame(buffer, false);
    }
    Buffer buf = Buffer.buffer(len);
    buf.appendBytes(bbuf, off, len);
    buffer = buf;
  }

  private void writeFrame(final Buffer buf, final boolean isFinal) throws IOException {
    if (response.writeQueueFull()) {
      LOG.debug("WebSocketResponse write queue is full pausing streaming");
      response.drainHandler(e -> paused.release());
      try {
        paused.acquire();
        LOG.debug("WebSocketResponse write queue is not accepting more data, resuming streaming");
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException(
            "Interrupted while waiting for HttpServerResponse to drain the write queue", ex);
      }
    }
    if (firstFrame) {
      response.writeFrame(WebSocketFrame.textFrame(buf.toString(), isFinal));
      firstFrame = false;
    } else {
      response.writeFrame(WebSocketFrame.continuationFrame(buf, isFinal));
    }
  }

  @Override
  public void close() throws IOException {
    writeFrame(buffer, true);
  }
}
