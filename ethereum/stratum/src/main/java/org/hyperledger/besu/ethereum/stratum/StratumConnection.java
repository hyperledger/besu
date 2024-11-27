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

import java.util.function.Consumer;

import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent TCP connection using a variant of the Stratum protocol, connecting the client to
 * miners.
 */
@Deprecated(since = "24.12.0")
final class StratumConnection {
  private static final Logger LOG = LoggerFactory.getLogger(StratumConnection.class);

  private final StratumProtocol[] protocols;
  private final Consumer<String> notificationSender;
  private StratumProtocol protocol;

  StratumConnection(final StratumProtocol[] protocols, final Consumer<String> notificationSender) {
    this.protocols = protocols;
    this.notificationSender = notificationSender;
  }

  void handleBuffer(final Buffer message, final Consumer<String> sender) {
    LOG.trace(">> {}", message);
    if (protocol == null) {
      for (StratumProtocol protocol : protocols) {
        if (protocol.maybeHandle(message, this, sender)) {
          LOG.trace("Using protocol: {}", protocol.getClass().getSimpleName());
          this.protocol = protocol;
        }
      }
      if (protocol == null) {
        throw new IllegalArgumentException("Invalid first message");
      }
    } else {
      protocol.handle(this, message, sender);
    }
  }

  void close() {
    if (protocol != null) {
      protocol.onClose(this);
    }
  }

  public Consumer<String> notificationSender() {
    return notificationSender;
  }
}
