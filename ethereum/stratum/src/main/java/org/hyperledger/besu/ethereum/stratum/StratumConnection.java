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

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent TCP connection using a variant of the Stratum protocol, connecting the client to
 * miners.
 */
final class StratumConnection {
  private static final Logger LOG = LoggerFactory.getLogger(StratumConnection.class);

  private String incompleteMessage = "";
  private boolean httpDetected = false;

  private final StratumProtocol[] protocols;
  private final Runnable closeHandle;
  private final Consumer<String> sender;

  private StratumProtocol protocol;

  private static final Pattern contentLengthPattern =
      Pattern.compile("\r\nContent-Length: (\\d+)\r\n");

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
    String messagesString;
    try {
      messagesString = buffer.toString(StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      LOG.debug("Invalid message with non UTF-8 characters: " + e.getMessage(), e);
      closeHandle.run();
      return;
    }

    if (httpDetected) {
      httpDetected = false;
      messagesString = incompleteMessage + messagesString;
    }
    Matcher match = contentLengthPattern.matcher(messagesString);
    if (match.find()) {
      try {
        int contentLength = Integer.parseInt(match.group(1));
        String body = messagesString.substring(messagesString.indexOf("\r\n\r\n") + 4);
        if (body.length() < contentLength) {
          incompleteMessage = messagesString;
          httpDetected = true;
          return;
        }
      } catch (NumberFormatException e) {
        close();
        return;
      }

      LOG.trace("Dispatching HTTP message {}", messagesString);
      handleMessage(messagesString);
    } else {
      boolean firstMessage = false;
      Splitter splitter = Splitter.on('\n');
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
  }

  void close() {
    if (protocol != null) {
      protocol.onClose(this);
    }
  }

  private void handleMessage(final String message) {
    if (protocol == null) {
      for (StratumProtocol protocol : protocols) {
        if (protocol.maybeHandle(message, this)) {
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
