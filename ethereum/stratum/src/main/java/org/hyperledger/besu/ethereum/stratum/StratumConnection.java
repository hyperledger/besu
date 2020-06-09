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
package org.hyperledger.besu.ethereum.stratum;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.function.Consumer;

import com.google.common.base.Splitter;
import io.vertx.core.buffer.Buffer;
import org.apache.logging.log4j.Logger;

/**
 * Persistent TCP connection using a variant of the Stratum protocol, connecting the client to
 * miners.
 */
final class StratumConnection {
  private static final Logger LOG = getLogger();

  private String incompleteMessage = "";

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
    LOG.trace("Buffer received {}", buffer);
    Splitter splitter = Splitter.on('\n');
    boolean firstMessage = false;
    String messagesString;
    try {
      messagesString = buffer.toString(StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      LOG.debug("Invalid message with non UTF-8 characters: " + e.getMessage(), e);
      closeHandle.run();
      return;
    }
    Iterator<String> messages = splitter.split(messagesString).iterator();
    while (messages.hasNext()) {
      String message = messages.next();
      if (!firstMessage) {
        message = incompleteMessage + message;
        firstMessage = true;
      }
      if (!messages.hasNext()) {
        incompleteMessage = message;
      } else {
        LOG.trace("Dispatching message {}", message);
        handleMessage(message);
      }
    }
  }

  void close(final Void aVoid) {
    if (protocol != null) {
      protocol.onClose(this);
    }
  }

  private void handleMessage(final String message) {
    if (protocol == null) {
      for (StratumProtocol protocol : protocols) {
        if (protocol.canHandle(message, this)) {
          this.protocol = protocol;
        }
      }
      if (protocol == null) {
        LOG.debug("Invalid first message: {}", message);
        closeHandle.run();
      }
    } else {
      protocol.handle(this, message);
    }
  }

  public void send(final String message) {
    LOG.debug("Sending message {}", message);
    sender.accept(message);
  }
}
