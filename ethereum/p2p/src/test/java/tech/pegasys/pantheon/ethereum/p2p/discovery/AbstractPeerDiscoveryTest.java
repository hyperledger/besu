/*
 * Copyright 2018 ConsenSys AG.
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

import static io.vertx.core.Vertx.vertx;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PingPacketData;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import junit.framework.AssertionFailedError;
import org.junit.After;

/**
 * A test class you can extend to acquire the ability to easily start discovery agents with a
 * generated Peer and keypair, as well as test sockets to communicate with those discovery agents.
 *
 * <p>Call {@link #startDiscoveryAgent(List)} and variants to start one or more discovery agents, or
 * {@link #startTestSocket()} or variants to start one or more test sockets. The lifecycle of those
 * objects is managed automatically for you via @Before and @After hooks, so you don't need to worry
 * about starting and stopping.
 */
public abstract class AbstractPeerDiscoveryTest {
  private static final String LOOPBACK_IP_ADDR = "127.0.0.1";
  private static final int TEST_SOCKET_START_TIMEOUT_SECS = 5;
  private static final int BROADCAST_TCP_PORT = 12356;
  private final Vertx vertx = vertx();

  List<DiscoveryTestSocket> discoveryTestSockets = new CopyOnWriteArrayList<>();
  List<PeerDiscoveryAgent> agents = new CopyOnWriteArrayList<>();

  @After
  public void stopServices() {
    // Close all sockets, will bubble up exceptions.
    final CompletableFuture<?>[] completions =
        discoveryTestSockets
            .stream()
            .filter(p -> p.getSocket() != null)
            .map(
                p -> {
                  final CompletableFuture<?> completion = new CompletableFuture<>();
                  p.getSocket()
                      .close(
                          ar -> {
                            if (ar.succeeded()) {
                              completion.complete(null);
                            } else {
                              completion.completeExceptionally(ar.cause());
                            }
                          });
                  return completion;
                })
            .toArray(CompletableFuture<?>[]::new);
    try {
      CompletableFuture.allOf(completions).join();
    } finally {
      agents.forEach(PeerDiscoveryAgent::stop);
      vertx.close();
    }
  }

  /**
   * Starts multiple discovery agents with the provided boostrap peers.
   *
   * @param count the number of agents to start
   * @param bootstrapPeers the list of bootstrap peers
   * @return a list of discovery agents.
   */
  protected List<PeerDiscoveryAgent> startDiscoveryAgents(
      final int count, final List<DiscoveryPeer> bootstrapPeers) {
    return Stream.generate(() -> startDiscoveryAgent(bootstrapPeers))
        .limit(count)
        .collect(Collectors.toList());
  }

  /**
   * Start a single discovery agent with the provided bootstrap peers.
   *
   * @param bootstrapPeers the list of bootstrap peers
   * @return a list of discovery agents.
   */
  protected PeerDiscoveryAgent startDiscoveryAgent(final List<DiscoveryPeer> bootstrapPeers) {
    return startDiscoveryAgent(bootstrapPeers, new PeerBlacklist());
  }

  /**
   * Start a single discovery agent with the provided bootstrap peers.
   *
   * @param bootstrapPeers the list of bootstrap peers
   * @param blacklist the peer blacklist
   * @return a list of discovery agents.
   */
  protected PeerDiscoveryAgent startDiscoveryAgent(
      final List<DiscoveryPeer> bootstrapPeers, final PeerBlacklist blacklist) {
    final DiscoveryConfiguration config = new DiscoveryConfiguration();
    config.setBootstrapPeers(bootstrapPeers);
    config.setBindPort(0);

    return startDiscoveryAgent(config, blacklist);
  }

  protected PeerDiscoveryAgent startDiscoveryAgent(
      final DiscoveryConfiguration config, final PeerBlacklist blacklist) {
    final PeerDiscoveryAgent agent =
        new PeerDiscoveryAgent(vertx, SECP256K1.KeyPair.generate(), config, () -> true, blacklist);
    try {
      agent.start(BROADCAST_TCP_PORT).get(5, TimeUnit.SECONDS);
    } catch (final Exception ex) {
      throw new AssertionError("Could not initialize discovery agent", ex);
    }
    agents.add(agent);
    return agent;
  }

  /**
   * Start multiple test sockets.
   *
   * <p>A test socket allows you to send messages to a discovery agent, as well as to react to
   * received messages. A test socket encapsulates: (1) a {@link DiscoveryPeer} and its {@link
   * tech.pegasys.pantheon.crypto.SECP256K1.KeyPair}, (2) an {@link ArrayBlockingQueue} where
   * received messages are placed automatically, and (3) the socket itself.
   *
   * @param count the number of test sockets to start.
   * @return the test sockets.
   */
  protected List<DiscoveryTestSocket> startTestSockets(final int count) {
    return Stream.generate(this::startTestSocket).limit(count).collect(Collectors.toList());
  }

  /**
   * Starts a single test socket.
   *
   * @return the test socket
   */
  protected DiscoveryTestSocket startTestSocket() {
    final ArrayBlockingQueue<Packet> queue = new ArrayBlockingQueue<>(100);

    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final BytesValue peerId = keyPair.getPublicKey().getEncodedBytes();

    final CompletableFuture<DiscoveryTestSocket> result = new CompletableFuture<>();
    // Test packet handler which feeds the received packet into a Future we later consume from.
    vertx
        .createDatagramSocket()
        .listen(
            0,
            LOOPBACK_IP_ADDR,
            ar -> {
              if (!ar.succeeded()) {
                result.completeExceptionally(ar.cause());
                return;
              }

              final DatagramSocket socket = ar.result();
              socket.handler(p -> queue.add(Packet.decode(p.data())));
              final DiscoveryPeer peer =
                  new DiscoveryPeer(
                      peerId,
                      LOOPBACK_IP_ADDR,
                      socket.localAddress().port(),
                      socket.localAddress().port());
              final DiscoveryTestSocket discoveryTestSocket =
                  new DiscoveryTestSocket(peer, keyPair, queue, socket);
              result.complete(discoveryTestSocket);
            });

    final DiscoveryTestSocket discoveryTestSocket;
    try {
      discoveryTestSocket = result.get(TEST_SOCKET_START_TIMEOUT_SECS, TimeUnit.SECONDS);
    } catch (final Exception ex) {
      throw new AssertionError("Could not initialize test peer", ex);
    }
    discoveryTestSockets.add(discoveryTestSocket);
    return discoveryTestSocket;
  }

  protected void bondViaIncomingPing(
      final PeerDiscoveryAgent agent, final DiscoveryTestSocket peerSocket)
      throws InterruptedException {
    final DiscoveryPeer peer = peerSocket.getPeer();

    final PingPacketData ping =
        PingPacketData.create(peer.getEndpoint(), agent.getAdvertisedPeer().getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, ping, peerSocket.getKeyPair());
    peerSocket.sendToAgent(agent, pingPacket);

    // Wait for returned pong packet to finish bonding
    peerSocket.getIncomingPackets().poll(10, TimeUnit.SECONDS);
  }

  /**
   * Encapsulates a test socket representing a Peer, with an associated queue where all incoming
   * packets are placed.
   */
  protected static class DiscoveryTestSocket {
    private final DiscoveryPeer peer;
    private final SECP256K1.KeyPair keyPair;
    private final ArrayBlockingQueue<Packet> queue;
    private final DatagramSocket socket;

    public DiscoveryTestSocket(
        final DiscoveryPeer peer,
        final SECP256K1.KeyPair keyPair,
        final ArrayBlockingQueue<Packet> queue,
        final DatagramSocket socket) {
      this.peer = peer;
      this.keyPair = keyPair;
      this.queue = queue;
      this.socket = socket;
    }

    public DiscoveryPeer getPeer() {
      return peer;
    }

    public ArrayBlockingQueue<Packet> getIncomingPackets() {
      return queue;
    }

    public DatagramSocket getSocket() {
      return socket;
    }

    public SECP256K1.KeyPair getKeyPair() {
      return keyPair;
    }

    /**
     * Sends a message to an agent.
     *
     * @param agent the recipient
     * @param packet the packet to send
     */
    public void sendToAgent(final PeerDiscoveryAgent agent, final Packet packet) {
      final Endpoint endpoint = agent.getAdvertisedPeer().getEndpoint();
      socket.send(packet.encode(), endpoint.getUdpPort(), endpoint.getHost(), ar -> {});
    }

    /**
     * Retrieves the head of the queue, compulsorily. If no message exists, or no message appears in
     * 5 seconds, it throws an assertion error.
     *
     * @return the head of the queue
     */
    public Packet compulsoryPoll() {
      final Packet packet;
      try {
        packet = queue.poll(5, TimeUnit.SECONDS);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }

      if (packet == null) {
        throw new AssertionFailedError(
            "Expected a message in the test peer queue, but found none.");
      }
      return packet;
    }
  }
}
