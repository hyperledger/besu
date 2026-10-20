/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snap2.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.BlockAccessListsMessageData;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockAccessListsMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV2;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestFailedException;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestSender;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SnapMessages;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads block access lists, over snap/2 or eth/71 depending on the peer, and checks each one
 * against the block access list hash of its header. Blocks a peer could not serve are asked to
 * other peers until every list is received or the attempts run out.
 */
public final class BalFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(BalFetcher.class);

  private final RequestSender sender;
  private final int batchSize;
  private final long responseBytes;
  private final int maxRounds;
  private final Function<Duration, Executor> pauses;

  public BalFetcher(
      final RequestSender sender,
      final int batchSize,
      final long responseBytes,
      final int maxRounds) {
    this(
        sender,
        batchSize,
        responseBytes,
        maxRounds,
        pause -> CompletableFuture.delayedExecutor(pause.toMillis(), TimeUnit.MILLISECONDS));
  }

  /** Creates a fetcher that waits between rounds on the executors {@code pauses} returns. */
  @VisibleForTesting
  BalFetcher(
      final RequestSender sender,
      final int batchSize,
      final long responseBytes,
      final int maxRounds,
      final Function<Duration, Executor> pauses) {
    this.sender = sender;
    this.batchSize = batchSize;
    this.responseBytes = responseBytes;
    this.maxRounds = maxRounds;
    this.pauses = pauses;
  }

  /**
   * Fetches the block access lists of the given headers.
   *
   * @return the raw lists, in the order of the headers
   */
  public CompletableFuture<List<Bytes>> fetch(final List<BlockHeader> headers) {
    for (final BlockHeader header : headers) {
      if (header.getBalHash().isEmpty()) {
        return CompletableFuture.failedFuture(
            new IllegalStateException(
                "block "
                    + header.getNumber()
                    + " has no block access list, snap/2 cannot sync it"));
      }
    }
    final Fetch fetch = new Fetch(headers, maxRounds);
    fetch.round(0);
    return fetch.result;
  }

  /** The lists of some headers, asked in rounds, each round to peers that did not lack them. */
  private final class Fetch {
    private final List<BlockHeader> headers;
    private final int rounds;
    private final CompletableFuture<List<Bytes>> result = new CompletableFuture<>();
    // guarded by this
    private final Map<Hash, Bytes> received = new HashMap<>();
    private final Map<Hash, Set<EthPeer>> lacking = new HashMap<>();

    private Fetch(final List<BlockHeader> headers, final int rounds) {
      this.headers = headers;
      this.rounds = rounds;
    }

    private void round(final int round) {
      final List<BlockHeader> missing = missing();
      if (missing.isEmpty()) {
        result.complete(lists());
        return;
      }
      if (round >= rounds) {
        result.completeExceptionally(
            new IllegalStateException(
                "no peer served the block access list of block " + missing.getFirst().getNumber()));
        return;
      }
      final List<CompletableFuture<Void>> requests = new ArrayList<>();
      for (int i = 0; i < missing.size(); i += batchSize) {
        requests.add(request(missing.subList(i, Math.min(missing.size(), i + batchSize))));
      }
      CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
          .whenComplete((unused, error) -> next(round, missing));
    }

    private CompletableFuture<Void> request(final List<BlockHeader> batch) {
      return sender
          .submit(new BalRequest(batch, responseBytes), excluded(batch))
          .handle(
              (response, error) -> {
                if (error == null) {
                  record(batch, response.value(), response.peer());
                } else {
                  RequestFailedException.from(error)
                      .flatMap(RequestFailedException::peer)
                      .ifPresent(peer -> record(batch, Map.of(), peer));
                }
                return null;
              });
    }

    // a round that received nothing waits, peers that lacked the lists may catch up meanwhile
    private void next(final int round, final List<BlockHeader> missing) {
      if (receivedAny(missing)) {
        round(round + 1);
        return;
      }
      LOG.debug("No block access list received in round {}, retrying", round);
      if (round % 5 == 4) {
        forgetLacking();
      }
      // once the sync stopped, the next round fails at once
      pauses.apply(Duration.ofSeconds(Math.min(10, 1 + round))).execute(() -> round(round + 1));
    }

    // keeps the lists the peer sent, and notes it lacked the others
    private synchronized void record(
        final List<BlockHeader> batch, final Map<Hash, Bytes> lists, final EthPeer peer) {
      received.putAll(lists);
      for (final BlockHeader header : batch) {
        if (!lists.containsKey(header.getHash())) {
          lacking.computeIfAbsent(header.getHash(), hash -> new HashSet<>()).add(peer);
        }
      }
    }

    private synchronized Set<EthPeer> excluded(final List<BlockHeader> batch) {
      final Set<EthPeer> excluded = new HashSet<>();
      batch.forEach(header -> excluded.addAll(lacking.getOrDefault(header.getHash(), Set.of())));
      return excluded;
    }

    private synchronized List<BlockHeader> missing() {
      return headers.stream().filter(header -> !received.containsKey(header.getHash())).toList();
    }

    private synchronized boolean receivedAny(final List<BlockHeader> missing) {
      return missing.stream().anyMatch(header -> received.containsKey(header.getHash()));
    }

    private synchronized List<Bytes> lists() {
      return headers.stream().map(header -> received.get(header.getHash())).toList();
    }

    private synchronized void forgetLacking() {
      lacking.clear();
    }
  }

  /** Requests block access lists by block hash; each returned list is checked. */
  static final class BalRequest implements SyncRequest<Map<Hash, Bytes>> {
    private final List<BlockHeader> headers;
    private final long bytes;

    BalRequest(final List<BlockHeader> headers, final long bytes) {
      this.headers = headers;
      this.bytes = bytes;
    }

    @Override
    public Protocol protocol() {
      return Protocol.SNAP2;
    }

    // snap/2 when the peer has it, otherwise eth/71
    @Override
    public Protocol protocolFor(final EthPeer peer) {
      return peer.getAgreedCapabilities().contains(SnapProtocol.SNAP2)
          ? Protocol.SNAP2
          : Protocol.ETH;
    }

    @Override
    public long minimumPeerHeight() {
      return headers.getLast().getNumber();
    }

    @Override
    public boolean canServe(final EthPeer peer) {
      return peer.getAgreedCapabilities().contains(SnapProtocol.SNAP2)
          || peer.getAgreedCapabilities().stream().anyMatch(EthProtocol::isEth71Compatible);
    }

    @Override
    public Priority priority() {
      return Priority.PIVOT;
    }

    @Override
    public String name() {
      return "block_access_lists";
    }

    @Override
    public MessageData message(final EthPeer peer) {
      final List<Hash> hashes = headers.stream().map(BlockHeader::getHash).toList();
      return protocolFor(peer) == Protocol.SNAP2
          ? SnapMessages.getBlockAccessLists(hashes, bytes)
          : GetBlockAccessListsMessage.create(hashes);
    }

    @Override
    public Map<Hash, Bytes> decode(final MessageData response, final EthPeer peer) {
      final List<Bytes> lists = blockAccessLists(response, protocolFor(peer));
      if (lists.size() > headers.size()) {
        throw new InvalidResponseException("more block access lists than requested", true);
      }
      final Map<Hash, Bytes> out = new HashMap<>();
      for (int i = 0; i < lists.size(); i++) {
        final Bytes list = lists.get(i);
        if (list.equals(RLP.NULL)) {
          continue;
        }
        final BlockHeader header = headers.get(i);
        if (!BodyValidation.balHash(list).equals(header.getBalHash().orElseThrow())) {
          throw new InvalidResponseException(
              "block access list does not match the header of block " + header.getNumber(), true);
        }
        // keep a compact copy, the list slices the whole message
        out.put(header.getHash(), list.copy());
      }
      if (out.isEmpty()) {
        throw new EmptyResponseException("no block access list");
      }
      return out;
    }

    // the raw lists, unavailable ones as RLP.NULL; the eth request manager strips the request id
    private static List<Bytes> blockAccessLists(
        final MessageData message, final Protocol protocol) {
      final boolean snap = protocol == Protocol.SNAP2;
      final int expected =
          snap ? SnapV2.BLOCK_ACCESS_LISTS : EthProtocolMessages.BLOCK_ACCESS_LISTS;
      if (message.getCode() != expected) {
        throw new InvalidResponseException("unexpected message code " + message.getCode(), true);
      }
      final List<Bytes> out = new ArrayList<>();
      (snap
              ? BlockAccessListsMessageData.decodeSnapResponseRaw(message.getData(), true)
              : BlockAccessListsMessageData.decodeEthResponseRaw(message.getData()))
          .forEach(out::add);
      return out;
    }
  }
}
