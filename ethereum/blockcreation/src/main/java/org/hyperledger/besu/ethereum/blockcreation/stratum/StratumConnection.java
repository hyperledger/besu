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
package org.hyperledger.besu.ethereum.blockcreation.stratum;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.Logger;

final class StratumConnection {
  private static final Logger logger = getLogger();

  private final ByteBuffer messageBuffer = ByteBuffer.allocate(4096);

  private final StratumProtocol[] protocols;
  private final Runnable closeHandle;
  private final Consumer<String> sender;

  private StratumProtocol protocol;

  StratumConnection(
      final StratumProtocol[] protocols,
      final Runnable closeHandle,
      final Consumer<String> sender) {
    this.protocols = protocols;
    this.closeHandle = closeHandle;
    this.sender = sender;
  }

  void handleBuffer(final Buffer buffer) {
    if (messageBuffer.remaining() < buffer.length()) {
      logger.debug("Message overflow");
      closeHandle.run();
      return;
    }
    int lastMessage = 0;
    for (int i = 0; i < buffer.length(); i++) {
      if (buffer.getByte(i) == '\n') {
        lastMessage = i + 1;
        messageBuffer.put(buffer.getBytes(0, i), messageBuffer.position(), i);
        byte[] message = new byte[messageBuffer.position()];
        messageBuffer.rewind();
        messageBuffer.get(message, 0, message.length);
        handleMessage(message);
        messageBuffer.clear();
      }
    }

    if (lastMessage != buffer.length()) {
      messageBuffer.put(buffer.getBytes(lastMessage, buffer.length()));
    }
  }

  void close(final Void aVoid) {
    if (protocol != null) {
      protocol.onClose(this);
    }
  }

  private void handleMessage(final byte[] message) {
    if (protocol == null) {
      for (StratumProtocol protocol : protocols) {
        if (protocol.canHandle(message, this)) {
          this.protocol = protocol;
        }
      }
      if (protocol == null) {
        logger.debug("Invalid first message: {}", new String(message, StandardCharsets.UTF_8));
        closeHandle.run();
      }
    } else {
      protocol.handle(this, message);
    }
  }

  public void send(final String message) {
    logger.debug("Sending message {}", message);
    sender.accept(message);
  }
}
