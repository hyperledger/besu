/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.handlers;

import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaxConnectionsHandler {
  private static final Logger LOG = LoggerFactory.getLogger(MaxConnectionsHandler.class);

  private MaxConnectionsHandler() {}

  public static Handler<HttpConnection> handler(
      final AtomicInteger activeConnectionsCount, final int maxActiveConnections) {
    return connection -> {
      if (activeConnectionsCount.get() >= maxActiveConnections) {
        // disallow new connections to prevent DoS
        LOG.warn(
            "Rejecting new connection from {} to {}. Max {} active connections limit reached.",
            connection.remoteAddress(),
            connection.localAddress(),
            activeConnectionsCount.getAndIncrement());
        connection.close();
      } else {
        LOG.debug(
            "Opened connection from {} to {}. Total of active connections: {}/{}",
            connection.remoteAddress(),
            connection.localAddress(),
            activeConnectionsCount.incrementAndGet(),
            maxActiveConnections);
      }
      connection.closeHandler(
          c ->
              LOG.debug(
                  "Connection closed from {} to {}. Total of active connections: {}/{}",
                  connection.remoteAddress(),
                  connection.localAddress(),
                  activeConnectionsCount.decrementAndGet(),
                  maxActiveConnections));
    };
  }
}
