/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.discovery;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerDiscoveryController;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerRequirement;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.TimerUtil;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.VertxTimerUtil;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.util.NetworkUtility;
import tech.pegasys.pantheon.util.Preconditions;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VertxPeerDiscoveryAgent extends PeerDiscoveryAgent {
  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx;
  /* The vert.x UDP socket. */
  private DatagramSocket socket;

  public VertxPeerDiscoveryAgent(
      final Vertx vertx,
      final KeyPair keyPair,
      final DiscoveryConfiguration config,
      final PeerRequirement peerRequirement,
      final PeerBlacklist peerBlacklist,
      final Optional<NodeWhitelistController> nodeWhitelistController) {
    super(keyPair, config, peerRequirement, peerBlacklist, nodeWhitelistController);
    checkArgument(vertx != null, "vertx instance cannot be null");
    this.vertx = vertx;
  }

  @Override
  protected TimerUtil createTimer() {
    return new VertxTimerUtil(vertx);
  }

  @Override
  protected CompletableFuture<InetSocketAddress> listenForConnections() {
    CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();
    vertx
        .createDatagramSocket(new DatagramSocketOptions().setIpV6(NetworkUtility.isIPv6Available()))
        .listen(
            config.getBindPort(), config.getBindHost(), res -> handleListenerSetup(res, future));
    return future;
  }

  protected void handleListenerSetup(
      final AsyncResult<DatagramSocket> listenResult,
      final CompletableFuture<InetSocketAddress> addressFuture) {
    if (listenResult.failed()) {
      Throwable cause = listenResult.cause();
      LOG.error("An exception occurred when starting the peer discovery agent", cause);

      if (cause instanceof BindException || cause instanceof SocketException) {
        cause =
            new PeerDiscoveryServiceException(
                String.format(
                    "Failed to bind Ethereum UDP discovery listener to %s:%d: %s",
                    config.getBindHost(), config.getBindPort(), cause.getMessage()));
      }
      addressFuture.completeExceptionally(cause);
      return;
    }

    this.socket = listenResult.result();

    // TODO: when using wildcard hosts (0.0.0.0), we need to handle multiple addresses by
    // selecting
    // the correct 'announce' address.
    final String effectiveHost = socket.localAddress().host();
    final int effectivePort = socket.localAddress().port();

    LOG.info(
        "Started peer discovery agent successfully, on effective host={} and port={}",
        effectiveHost,
        effectivePort);

    socket.exceptionHandler(this::handleException);
    socket.handler(this::handlePacket);

    InetSocketAddress address =
        new InetSocketAddress(socket.localAddress().host(), socket.localAddress().port());
    addressFuture.complete(address);
  }

  @Override
  protected CompletableFuture<Void> sendOutgoingPacket(
      final DiscoveryPeer peer, final Packet packet) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    socket.send(
        packet.encode(),
        peer.getEndpoint().getUdpPort(),
        peer.getEndpoint().getHost(),
        ar -> {
          if (ar.failed()) {
            result.completeExceptionally(ar.cause());
          } else {
            result.complete(null);
          }
        });
    return result;
  }

  @Override
  public CompletableFuture<?> stop() {
    if (socket == null) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<?> completion = new CompletableFuture<>();
    socket.close(
        ar -> {
          if (ar.succeeded()) {
            controller.ifPresent(PeerDiscoveryController::stop);
            socket = null;
            completion.complete(null);
          } else {
            completion.completeExceptionally(ar.cause());
          }
        });
    return completion;
  }

  /**
   * For uncontrolled exceptions occurring in the packet handlers.
   *
   * @param exception the exception that was raised
   */
  private void handleException(final Throwable exception) {
    if (exception instanceof IOException) {
      LOG.debug("Packet handler exception", exception);
    } else {
      LOG.error("Packet handler exception", exception);
    }
  }

  /**
   * The UDP packet handler. This is the entrypoint for all received datagrams.
   *
   * @param datagram the received datagram.
   */
  private void handlePacket(final DatagramPacket datagram) {
    try {
      final int length = datagram.data().length();
      Preconditions.checkGuard(
          validatePacketSize(length),
          PeerDiscoveryPacketDecodingException::new,
          "Packet too large. Actual size (bytes): %s",
          length);

      // We allow exceptions to bubble up, as they'll be picked up by the exception handler.
      final Packet packet = Packet.decode(datagram.data());
      // Acquire the senders coordinates to build a Peer representation from them.
      final String host = datagram.sender().host();
      final int port = datagram.sender().port();
      final Endpoint endpoint = new Endpoint(host, port, OptionalInt.empty());
      handleIncomingPacket(endpoint, packet);
    } catch (final PeerDiscoveryPacketDecodingException e) {
      LOG.debug("Discarding invalid peer discovery packet", e);
    } catch (final Throwable t) {
      LOG.error("Encountered error while handling packet", t);
    }
  }
}
