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

import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.PoWObserver;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.Logger;

/**
 * TCP server allowing miners to connect to the client over persistent TCP connections, using the
 * various Stratum protocols.
 */
public class StratumServer implements PoWObserver {

  private static final Logger logger = getLogger();

  private final Vertx vertx;
  private final int port;
  private final String networkInterface;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final StratumProtocol[] protocols;
  private NetServer server;

  public StratumServer(
      final Vertx vertx,
      final MiningCoordinator miningCoordinator,
      final int port,
      final String networkInterface,
      final String extraNonce) {
    this.vertx = vertx;
    this.port = port;
    this.networkInterface = networkInterface;
    protocols =
        new StratumProtocol[] {
          new Stratum1Protocol(extraNonce, miningCoordinator),
          new Stratum1EthProxyProtocol(miningCoordinator)
        };
  }

  public CompletableFuture<?> start() {
    if (started.compareAndSet(false, true)) {
      logger.info("Starting stratum server on {}:{}", networkInterface, port);
      server =
          vertx.createNetServer(
              new NetServerOptions().setPort(port).setHost(networkInterface).setTcpKeepAlive(true));
      CompletableFuture<?> result = new CompletableFuture<>();
      server.connectHandler(this::handle);
      server.listen(
          res -> {
            if (res.failed()) {
              result.completeExceptionally(
                  new StratumServerException(
                      String.format(
                          "Failed to bind Stratum Server listener to %s:%s: %s",
                          networkInterface, port, res.cause().getMessage())));
            } else {
              result.complete(null);
            }
          });
      return result;
    }
    return CompletableFuture.completedFuture(null);
  }

  private void handle(final NetSocket socket) {
    StratumConnection conn =
        new StratumConnection(
            protocols, socket::close, bytes -> socket.write(Buffer.buffer(bytes)));
    socket.handler(conn::handleBuffer);
    socket.closeHandler(conn::close);
  }

  public CompletableFuture<?> stop() {
    if (started.compareAndSet(true, false)) {
      CompletableFuture<?> result = new CompletableFuture<>();
      server.close(
          res -> {
            if (res.failed()) {
              result.completeExceptionally(
                  new StratumServerException(
                      String.format(
                          "Failed to bind Stratum Server listener to %s:%s: %s",
                          networkInterface, port, res.cause().getMessage())));
            } else {
              result.complete(null);
            }
          });
      return result;
    }
    logger.debug("Stopping StratumServer that was not running");
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void newJob(final PoWSolverInputs poWSolverInputs) {
    if (!started.get()) {
      logger.debug("Discarding {} as stratum server is not started", poWSolverInputs);
      return;
    }
    for (StratumProtocol protocol : protocols) {
      protocol.setCurrentWorkTask(poWSolverInputs);
    }
  }

  @Override
  public void setSubmitWorkCallback(final Function<PoWSolution, Boolean> submitSolutionCallback) {
    for (StratumProtocol protocol : protocols) {
      protocol.setSubmitCallback(submitSolutionCallback);
    }
  }
}
