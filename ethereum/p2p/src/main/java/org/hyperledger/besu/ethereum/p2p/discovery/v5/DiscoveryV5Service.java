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
package org.hyperledger.besu.ethereum.p2p.discovery.v5;

import org.hyperledger.besu.crypto.KeyPair;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.MutableDiscoverySystem;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thin wrapper around DiscV5 for Besu using MutableDiscoverySystem (no Teku deps). */
public final class DiscoveryV5Service implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(DiscoveryV5Service.class);
  private final MutableDiscoverySystem discovery;
  private final AtomicBoolean started = new AtomicBoolean(false);

  // Fallback poller (used only if one or both callback methods are missing)
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            final Thread t = new Thread(r, "besu-discv5-poller");
            t.setDaemon(true);
            return t;
          });
  private volatile ScheduledFuture<?> pollTask;
  private volatile Consumer<NodeRecord> addedHandler; // set if we must poll for additions
  private volatile Consumer<NodeRecord> removedHandler; // set if we must poll for removals
  private final Map<String, NodeRecord> lastSnapshot = new ConcurrentHashMap<>();

  public DiscoveryV5Service(final DiscoveryV5Config cfg) {
    final InetSocketAddress bind = cfg.udpBindAddress();
    final DiscoverySystemBuilder builder =
        new DiscoverySystemBuilder()
            .listen(bind.getAddress().getHostAddress(), bind.getPort())
            .addressAccessPolicy(AddressAccessPolicy.ALLOW_ALL);

    // Besu KeyPair -> Tuweni SecretKey
    final Bytes32 skBytes32 = cfg.nodeKey().getPrivateKey().getEncodedBytes();
    final SECP256K1.SecretKey tuweniSk = SECP256K1.SecretKey.fromBytes(skBytes32);
    builder.secretKey(tuweniSk);

    // Local ENR
    final NodeRecord local = buildLocalNodeRecord(cfg, tuweniSk);
    builder.localNodeRecord(local);

    // Bootnodes (accept ENR; enode:// conversion TODO)
    final List<NodeRecord> boots = new ArrayList<>();
    for (String s : cfg.bootEnrs()) {
      final NodeRecord enr = parseBootnode(s);
      if (enr != null) boots.add(enr);
    }
    builder.bootnodes(boots);

    // Build mutable system (callbacks + async lifecycle)
    this.discovery = builder.buildMutable();
  }

  /** Start the DiscV5 system (idempotent). */
  public synchronized void start() {
    if (!started.compareAndSet(false, true)) return;
    discovery.start().join(); // future-based in Mutable API

    // If either handler needs polling, start the poller.
    if ((addedHandler != null || removedHandler != null) && pollTask == null) {
      seedSnapshot();
      pollTask = scheduler.scheduleAtFixedRate(this::pollOnce, 0, 2, TimeUnit.SECONDS);
    }
  }

  /** Stop asynchronously. */
  public CompletableFuture<Void> stopAsync() {
    if (pollTask != null) {
      pollTask.cancel(true);
    }
    scheduler.shutdownNow();
    started.set(false);
    discovery.stop();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void close() {
    stopAsync().join();
  }

  // ---- Event hooks ----

  public void onNodeDiscovered(final Consumer<NodeRecord> handler) {
    if (tryRegister(discovery, "setNewPeerHandler", handler)) {
      return;
    }
    if (tryRegister(discovery, "setNewPeerListener", handler)) { // some versions use *Listener
      return;
    }
    // fallback to polling for additions
    this.addedHandler = handler;
    if (started.get() && pollTask == null) {
      seedSnapshot();
      pollTask = scheduler.scheduleAtFixedRate(this::pollOnce, 0, 2, TimeUnit.SECONDS);
    }
  }

  public void onNodeRemoved(final Consumer<NodeRecord> handler) {
    if (tryRegister(discovery, "setPeerRemovedHandler", handler)) {
      return;
    }
    if (tryRegister(discovery, "setPeerRemovedListener", handler)) { // alt name on some builds
      return;
    }
    // fallback to polling for removals
    this.removedHandler = handler;
    if (started.get() && pollTask == null) {
      seedSnapshot();
      pollTask = scheduler.scheduleAtFixedRate(this::pollOnce, 0, 2, TimeUnit.SECONDS);
    }
  }

  // ---- Basic API ----

  public CompletableFuture<Void> ping(final NodeRecord node) {
    return discovery.ping(node);
  }

  public CompletableFuture<Collection<NodeRecord>> findNodes(
      final NodeRecord node, final List<Integer> distances) {
    return discovery.findNodes(node, distances);
  }

  public CompletableFuture<Bytes> talk(
      final NodeRecord node, final Bytes protocol, final Bytes request) {
    return discovery.talk(node, protocol, request);
  }

  public List<List<NodeRecord>> getRoutingTableBuckets() {
    return discovery.getNodeRecordBuckets();
  }

  public Optional<String> localEnr() {
    return Optional.of(discovery.getLocalNodeRecord().asEnr());
  }

  public Optional<Bytes> localNodeId() {
    return Optional.of(discovery.getLocalNodeRecord().getNodeId());
  }

  public boolean addEnr(final String enr) {
    try {
      final NodeRecord n = NodeRecordFactory.DEFAULT.fromEnr(enr);
      discovery.addNodeRecord(n);
      return discovery.lookupNode(n.getNodeId()).isPresent();
    } catch (Exception e) {
      return false;
    }
  }

  public boolean deleteEnr(final String nodeIdHex) {
    try {
      final var id = Bytes.fromHexString(nodeIdHex);
      discovery.deleteNodeRecord(id);
      return discovery.lookupNode(id).isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  // ---- Helpers ----

  private static NodeRecord parseBootnode(final String s) {
    try {
      if (s == null) return null;
      if (s.startsWith("enr:")) {
        return NodeRecordFactory.DEFAULT.fromEnr(s);
      }
      if (s.startsWith("enode://")) {
        // TODO: implement enode:// -> ENR conversion if required
        return null;
      }
    } catch (Exception e) {
      LOG.debug("Failed to parse bootnode [{}]", s, e);
    }
    return null;
  }

  private static NodeRecord buildLocalNodeRecord(
      final DiscoveryV5Config cfg, final SECP256K1.SecretKey sk) {
    final NodeRecordBuilder b = new NodeRecordBuilder().secretKey(sk);

    final List<String> advertised = cfg.resolvedAdvertisedIpsOrDefault();
    final InetSocketAddress bind = cfg.udpBindAddress();
    final int udp = bind.getPort();
    final int tcp = cfg.tcpAdvertised().map(InetSocketAddress::getPort).orElse(udp);

    if (!advertised.isEmpty()) {
      for (String ip : advertised) {
        b.address(ip, udp, tcp);
      }
    } else {
      final String ip = bind.getAddress().getHostAddress();
      b.address(ip, udp, tcp);
    }
    return b.build();
  }

  private static boolean tryRegister(
      final Object target, final String methodName, final Consumer<NodeRecord> handler) {
    try {
      final Method m = target.getClass().getMethod(methodName, Consumer.class);
      m.invoke(target, (Consumer<NodeRecord>) handler::accept);
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void seedSnapshot() {
    lastSnapshot.clear();
    for (List<NodeRecord> bucket : discovery.getNodeRecordBuckets()) {
      for (NodeRecord nr : bucket) {
        lastSnapshot.put(nr.getNodeId().toHexString(), nr);
      }
    }
  }

  private void pollOnce() {
    try {
      final Map<String, NodeRecord> current = new HashMap<>();
      for (List<NodeRecord> bucket : discovery.getNodeRecordBuckets()) {
        for (NodeRecord nr : bucket) {
          current.put(nr.getNodeId().toHexString(), nr);
        }
      }
      // additions
      if (addedHandler != null) {
        for (Map.Entry<String, NodeRecord> e : current.entrySet()) {
          if (!lastSnapshot.containsKey(e.getKey())) {
            addedHandler.accept(e.getValue());
          }
        }
      }
      // removals
      if (removedHandler != null) {
        for (String id : new ArrayList<>(lastSnapshot.keySet())) {
          if (!current.containsKey(id)) {
            removedHandler.accept(lastSnapshot.get(id));
          }
        }
      }
      lastSnapshot.clear();
      lastSnapshot.putAll(current);
    } catch (Throwable ignored) {
      // keep poller alive on any error
    }
  }

  // Convenience factory
  public static DiscoveryV5Service from(
      final KeyPair nodeKey, final InetSocketAddress udpBind, final List<String> bootEnrs) {
    final DiscoveryV5Config cfg =
        DiscoveryV5Config.builder()
            .nodeKey(nodeKey)
            .udpBindAddress(udpBind)
            .bootEnrs(bootEnrs)
            .build();
    return new DiscoveryV5Service(cfg);
  }
}
