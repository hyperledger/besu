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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static java.util.Collections.unmodifiableList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerDistanceCalculator.distance;

import tech.pegasys.pantheon.crypto.Hash;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerId;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

import com.google.common.hash.BloomFilter;

/**
 * Implements a Kademlia routing table based on k-buckets with a keccak-256 XOR-based distance
 * metric.
 */
public class PeerTable {
  private static final int N_BUCKETS = 256;
  private static final int DEFAULT_BUCKET_SIZE = 16;
  private static final int BLOOM_FILTER_REGENERATION_THRESHOLD = 50; // evictions

  private final Bucket[] table;
  private final BytesValue keccak256;
  private final int maxEntriesCnt;

  private final Map<BytesValue, Integer> distanceCache;
  private BloomFilter<BytesValue> idBloom;
  private int evictionCnt = 0;

  /**
   * Builds a new peer table, where distance is calculated using the provided nodeId as a baseline.
   *
   * @param nodeId The ID of the node where this peer table is stored.
   * @param bucketSize The maximum length of each k-bucket.
   */
  public PeerTable(final BytesValue nodeId, final int bucketSize) {
    this.keccak256 = Hash.keccak256(nodeId);
    this.table =
        Stream.generate(() -> new Bucket(DEFAULT_BUCKET_SIZE))
            .limit(N_BUCKETS + 1)
            .toArray(Bucket[]::new);
    this.distanceCache = new ConcurrentHashMap<>();
    this.maxEntriesCnt = N_BUCKETS * bucketSize;

    // A bloom filter with 4096 expected insertions of 64-byte keys with a 0.1% false positive
    // probability yields a memory footprint of ~7.5kb.
    buildBloomFilter();
  }

  public PeerTable(final BytesValue nodeId) {
    this(nodeId, DEFAULT_BUCKET_SIZE);
  }

  /**
   * Returns the table's representation of a peer, if it exists.
   *
   * @param peer The peer to query.
   * @return The stored representation.
   */
  public Optional<DiscoveryPeer> get(final PeerId peer) {
    if (!idBloom.mightContain(peer.getId())) {
      return Optional.empty();
    }
    final int distance = distanceFrom(peer);
    return table[distance].getAndTouch(peer.getId());
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
   * </ul>
   *
   * @see AddResult.Outcome
   * @param peer The peer to add.
   * @return An object indicating the outcome of the operation.
   */
  public AddResult tryAdd(final DiscoveryPeer peer) {
    final BytesValue id = peer.getId();
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
  public boolean evict(final PeerId peer) {
    final BytesValue id = peer.getId();
    final int distance = distanceFrom(peer);
    distanceCache.remove(id);

    final boolean evicted = table[distance].evict(peer);
    evictionCnt += evicted ? 1 : 0;

    // Trigger the bloom filter regeneration if needed.
    if (evictionCnt >= BLOOM_FILTER_REGENERATION_THRESHOLD) {
      ForkJoinPool.commonPool().execute(this::buildBloomFilter);
    }

    return evicted;
  }

  private void buildBloomFilter() {
    final BloomFilter<BytesValue> bf =
        BloomFilter.create((id, val) -> val.putBytes(id.extractArray()), maxEntriesCnt, 0.001);
    getAllPeers().stream().map(Peer::getId).forEach(bf::put);
    this.evictionCnt = 0;
    this.idBloom = bf;
  }

  /**
   * Returns the <code>limit</code> peers (at most) closest to the provided target, based on the XOR
   * distance between the keccak-256 hash of the ID and the keccak-256 hash of the target.
   *
   * @param target The target node ID.
   * @param limit The amount of results to return.
   * @return The <code>limit</code> closest peers, at most.
   */
  public List<DiscoveryPeer> nearestPeers(final BytesValue target, final int limit) {
    final BytesValue keccak256 = Hash.keccak256(target);
    return getAllPeers().stream()
        .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .sorted(comparingInt((peer) -> distance(peer.keccak256(), keccak256)))
        .limit(limit)
        .collect(toList());
  }

  public Collection<DiscoveryPeer> getAllPeers() {
    return unmodifiableList(
        Arrays.stream(table).flatMap(e -> e.peers().stream()).collect(toList()));
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
    return distance == null ? distance(keccak256, peer.keccak256()) : distance;
  }

  /** A class that encapsulates the result of a peer addition to the table. */
  public static class AddResult {
    /** The outcome of the operation. */
    public enum Outcome {

      /** The peer was added successfully to its corresponding k-bucket. */
      ADDED,

      /** The bucket for this peer was full. An eviction candidate must be proposed. */
      BUCKET_FULL,

      /** The peer already existed, hence it was not overwritten. */
      ALREADY_EXISTED,

      /** The caller requested to add ourselves. */
      SELF
    }

    private final Outcome outcome;
    private final Peer evictionCandidate;

    private AddResult(final Outcome outcome, final Peer evictionCandidate) {
      this.outcome = outcome;
      this.evictionCandidate = evictionCandidate;
    }

    static AddResult added() {
      return new AddResult(Outcome.ADDED, null);
    }

    static AddResult bucketFull(final Peer evictionCandidate) {
      return new AddResult(Outcome.BUCKET_FULL, evictionCandidate);
    }

    static AddResult existed() {
      return new AddResult(Outcome.ALREADY_EXISTED, null);
    }

    static AddResult self() {
      return new AddResult(Outcome.SELF, null);
    }

    public Outcome getOutcome() {
      return outcome;
    }

    public Peer getEvictionCandidate() {
      return evictionCandidate;
    }
  }
}
