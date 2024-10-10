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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable.AddResult.AddOutcome;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.BloomFilter;
import org.apache.tuweni.bytes.Bytes;

/**
 * Implements a Kademlia routing table based on k-buckets with a keccak-256 XOR-based distance
 * metric.
 */
public class PeerTable {
  private static final int N_BUCKETS = 256;
  private static final int DEFAULT_BUCKET_SIZE = 16;
  private static final int BLOOM_FILTER_REGENERATION_THRESHOLD = 50; // evictions

  private final Bucket[] table;
  private final Bytes keccak256;
  private final int maxEntriesCnt;

  private final Map<Bytes, Integer> distanceCache;
  private BloomFilter<Bytes> idBloom;
  private int evictionCnt = 0;
  private final Cache<String, Integer> unresponsiveIPs;

  /**
   * Builds a new peer table, where distance is calculated using the provided nodeId as a baseline.
   *
   * @param nodeId The ID of the node where this peer table is stored.
   */
  public PeerTable(final Bytes nodeId) {
    this.keccak256 = Hash.keccak256(nodeId);
    this.table =
        Stream.generate(() -> new Bucket(DEFAULT_BUCKET_SIZE))
            .limit(N_BUCKETS + 1)
            .toArray(Bucket[]::new);
    this.distanceCache = new ConcurrentHashMap<>();
    this.maxEntriesCnt = N_BUCKETS * DEFAULT_BUCKET_SIZE;
    this.unresponsiveIPs =
        CacheBuilder.newBuilder()
            .maximumSize(maxEntriesCnt)
            .expireAfterWrite(15L, TimeUnit.MINUTES)
            .build();

    // A bloom filter with 4096 expected insertions of 64-byte keys with a 0.1% false positive
    // probability yields a memory footprint of ~7.5kb.
    buildBloomFilter();
  }

  /**
   * Returns the table's representation of a peer, if it exists.
   *
   * @param peer The peer to query.
   * @return The stored representation.
   */
  public Optional<DiscoveryPeer> get(final PeerId peer) {
    final Bytes peerId = peer.getId();
    if (!idBloom.mightContain(peerId)) {
      return Optional.empty();
    }
    final int distance = distanceFrom(peer);
    return table[distance].getAndTouch(peerId);
  }

  /**
   * Attempts to add the provided peer to the peer table, and returns an {@link AddResult}
   * signalling one of three outcomes.
   *
   * <h3>Possible outcomes:</h3>
   *
   * <ul>
   *   <li>the operation succeeded and the peer was added to the corresponding k-bucket.
   *   <li>the operation failed because the k-bucket was full, in which case a candidate is proposed
   *       for eviction.
   *   <li>the operation failed because the peer already existed.
   *   <li>the operation failed because the IP address is invalid.
   * </ul>
   *
   * @param peer The peer to add.
   * @return An object indicating the outcome of the operation.
   * @see AddOutcome
   */
  public AddResult tryAdd(final DiscoveryPeer peer) {
    if (isIpAddressInvalid(peer.getEndpoint())) {
      return AddResult.invalid();
    }
    final Bytes id = peer.getId();
    final int distance = distanceFrom(peer);

    // Safeguard against adding ourselves to the peer table.
    if (distance == 0) {
      return AddResult.self();
    }

    final Bucket bucket = table[distance];
    // We add the peer, and two things can happen: (1) either we get an empty optional (peer was
    // added successfully,
    // or it was already there), or (2) we get a filled optional, in which case the bucket is full
    // and an eviction
    // candidate is proposed. The Bucket#add method will raise an exception if the peer already
    // existed.
    final Optional<DiscoveryPeer> res;
    try {
      res = bucket.add(peer);
    } catch (final IllegalArgumentException ex) {
      return AddResult.existed();
    }

    if (!res.isPresent()) {
      idBloom.put(id);
      distanceCache.put(id, distance);
      return AddResult.added();
    }

    return res.map(AddResult::bucketFull).get();
  }

  /**
   * Evicts a peer from the underlying table.
   *
   * @param peer The peer to evict.
   * @return Whether the peer existed, and hence the eviction took place.
   */
  public EvictResult tryEvict(final PeerId peer) {
    final Bytes id = peer.getId();
    final int distance = distanceFrom(peer);

    if (distance == 0) {
      return EvictResult.self();
    }

    distanceCache.remove(id);

    if (table[distance].getPeers().isEmpty()) {
      return EvictResult.absent();
    }

    final boolean evicted = table[distance].evict(peer);
    if (evicted) {
      evictionCnt++;
    } else {
      return EvictResult.absent();
    }

    // Trigger the bloom filter regeneration if needed.
    if (evictionCnt >= BLOOM_FILTER_REGENERATION_THRESHOLD) {
      ForkJoinPool.commonPool().execute(this::buildBloomFilter);
    }

    return EvictResult.evicted();
  }

  private void buildBloomFilter() {
    final BloomFilter<Bytes> bf =
        BloomFilter.create((id, val) -> val.putBytes(id.toArray()), maxEntriesCnt, 0.001);
    streamAllPeers().map(Peer::getId).forEach(bf::put);
    this.evictionCnt = 0;
    this.idBloom = bf;
  }

  /**
   * Returns the <code>limit</code> peers (at most) bonded closest to the provided target, based on
   * the XOR distance between the keccak-256 hash of the ID and the keccak-256 hash of the target.
   *
   * @param target The target node ID.
   * @param limit The amount of results to return.
   * @return The <code>limit</code> closest peers, at most.
   */
  public List<DiscoveryPeer> nearestBondedPeers(final Bytes target, final int limit) {
    final Bytes keccak256 = Hash.keccak256(target);
    return streamAllPeers()
        .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .sorted(
            comparingInt((peer) -> PeerDistanceCalculator.distance(peer.keccak256(), keccak256)))
        .limit(limit)
        .collect(toList());
  }

  public Stream<DiscoveryPeer> streamAllPeers() {
    return Arrays.stream(table).flatMap(e -> e.getPeers().stream());
  }

  public boolean isIpAddressInvalid(final Endpoint endpoint) {
    final String key = getKey(endpoint);
    return unresponsiveIPs.getIfPresent(key) != null;
  }

  public void invalidateIP(final Endpoint endpoint) {
    final String key = getKey(endpoint);
    unresponsiveIPs.put(key, Integer.MAX_VALUE);
  }

  private static String getKey(final Endpoint endpoint) {
    return endpoint.getHost() + ":" + endpoint.getFunctionalTcpPort();
  }

  /**
   * Calculates the XOR distance between the keccak-256 hashes of our node ID and the provided
   * {@link DiscoveryPeer}.
   *
   * @param peer The target peer.
   * @return The distance.
   */
  private int distanceFrom(final PeerId peer) {
    final Integer distance = distanceCache.get(peer.getId());
    return distance == null
        ? PeerDistanceCalculator.distance(keccak256, peer.keccak256())
        : distance;
  }

  /** A class that encapsulates the result of a peer addition to the table. */
  public static class AddResult {

    /** The outcome of the operation. */
    public enum AddOutcome {

      /** The peer was added successfully to its corresponding k-bucket. */
      ADDED,

      /** The bucket for this peer was full. An eviction candidate must be proposed. */
      BUCKET_FULL,

      /** The peer already existed, hence it was not overwritten. */
      ALREADY_EXISTED,

      /** The caller requested to add ourselves. */
      SELF,

      /** The peer was not added because the IP address is invalid. */
      INVALID
    }

    private final AddOutcome outcome;
    private final Peer evictionCandidate;

    private AddResult(final AddOutcome outcome, final Peer evictionCandidate) {
      this.outcome = outcome;
      this.evictionCandidate = evictionCandidate;
    }

    static AddResult added() {
      return new AddResult(AddOutcome.ADDED, null);
    }

    static AddResult bucketFull(final Peer evictionCandidate) {
      return new AddResult(AddOutcome.BUCKET_FULL, evictionCandidate);
    }

    static AddResult existed() {
      return new AddResult(AddOutcome.ALREADY_EXISTED, null);
    }

    static AddResult self() {
      return new AddResult(AddOutcome.SELF, null);
    }

    public static AddResult invalid() {
      return new AddResult((AddOutcome.INVALID), null);
    }

    public AddOutcome getOutcome() {
      return outcome;
    }

    public Peer getEvictionCandidate() {
      return evictionCandidate;
    }
  }

  static class EvictResult {
    public enum EvictOutcome {
      EVICTED,
      ABSENT,
      SELF
    }

    private final EvictOutcome outcome;

    private EvictResult(final EvictOutcome outcome) {
      this.outcome = outcome;
    }

    static EvictResult evicted() {
      return new EvictResult(EvictOutcome.EVICTED);
    }

    static EvictResult absent() {
      return new EvictResult(EvictOutcome.ABSENT);
    }

    static EvictResult self() {
      return new EvictResult(EvictOutcome.SELF);
    }

    EvictOutcome getOutcome() {
      return outcome;
    }
  }
}
