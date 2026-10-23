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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.eth.EthProtocolVersion;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.MalformedRlpFromPeerException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerSelector;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskRequestSender;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskValidationResponse;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.chain.BodyBackfill.Settings;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class BodyBackfillTest {

  private static final int HEAD = 60;
  private static final int RANGE_BLOCKS = 5;

  private final BlockDataGenerator gen = new BlockDataGenerator(5);
  private final List<Block> chain = new ArrayList<>();
  private final Map<Hash, Block> blocksByHash = new HashMap<>();
  private final Map<Hash, List<TransactionReceipt>> receiptsByHash = new HashMap<>();
  private final FakePeers peers = new FakePeers();
  private MutableBlockchain blockchain;

  @Test
  void storesTheMissingBodiesAndReceiptsHighestFirst() {
    blockchain = chain(51);
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> 1);

    run(backfill);

    assertThat(backfill.done()).isCompleted();
    assertBodiesStored(1, HEAD);
    assertThat(blockchain.getBodiesFrom()).contains(1L);
    assertThat(backfill.lowestStored()).isEqualTo(1);
    assertThat(backfill.blocksRemaining()).isZero();
    assertThat(backfill.blocksStored()).isEqualTo(50);
    // ranges of five blocks, from the highest missing one down
    assertThat(peers.askedRanges).containsExactly(46L, 41L, 36L, 31L, 26L, 21L, 16L, 11L, 6L, 1L);
  }

  @Test
  void leavesAPeerWithARequestInFlightToTheOtherRequests() {
    blockchain = chain(51);
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> 46);
    final AtomicInteger checked = new AtomicInteger();
    peers.duringRequest =
        peer -> {
          // with this request in flight, the filter refuses its peer and takes any other
          assertThat(peers.lastFilter.test(attributes(peer))).isFalse();
          assertThat(peers.all.stream().filter(other -> other != peer))
              .allMatch(other -> peers.lastFilter.test(attributes(other)));
          checked.incrementAndGet();
        };

    run(backfill);

    assertThat(checked).hasPositiveValue();
    assertThat(backfill.done()).isCompleted();
  }

  @Test
  void asksNothingWhileTheNodeIsBehindTheHead() {
    blockchain = chain(51);
    final AtomicInteger checks = new AtomicInteger();
    final List<Integer> requestsWhileBehind = new ArrayList<>();
    final BooleanSupplier inSync =
        () -> {
          final boolean behind = checks.incrementAndGet() <= 3;
          if (behind) {
            requestsWhileBehind.add(peers.requests);
          }
          return !behind;
        };
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, inSync, head -> 1);

    run(backfill);

    assertThat(requestsWhileBehind).containsExactly(0, 0, 0);
    assertThat(backfill.done()).isCompleted();
    assertBodiesStored(1, HEAD);
  }

  @Test
  void resumesFromTheBlockRecordedOnStop() {
    blockchain = chain(51);
    final BodyBackfill stopped = backfill(Long.MAX_VALUE, () -> true, head -> 1);
    // stopped once the bodies of its third range arrived, while their receipts download
    peers.beforeReceipts =
        first -> {
          if (first == 36) {
            stopped.stop();
          }
        };

    run(stopped);

    assertThat(stopped.done()).isCompletedExceptionally();
    assertThat(blockchain.getBodiesFrom()).contains(41L);
    assertNoBodies(36, 40);

    peers.beforeReceipts = first -> {};
    peers.askedRanges.clear();
    final BodyBackfill resumed = backfill(Long.MAX_VALUE, () -> true, head -> 1);
    run(resumed);

    assertThat(resumed.done()).isCompleted();
    assertThat(peers.askedRanges).startsWith(36L);
    assertBodiesStored(1, HEAD);
  }

  @Test
  void stopsAtTheLowestBlockNeeded() {
    blockchain = chain(51);
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> head - 29);

    run(backfill);

    assertThat(backfill.done()).isCompleted();
    assertBodiesStored(31, HEAD);
    assertNoBodies(1, 30);
    assertThat(blockchain.getBodiesFrom()).contains(31L);
  }

  @Test
  void hasNothingToDoWhenEveryNeededBodyIsStored() {
    blockchain = chain(1);
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> 1);

    backfill.start(new QueuedExecutor());

    assertThat(backfill.done()).isCompleted();
    assertThat(peers.requests).isZero();
  }

  @Test
  void asksARangeAgainAfterAFailedRequest() {
    blockchain = chain(51);
    // the first answer holds no body
    peers.failNextBodies = true;
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> 1);

    run(backfill);

    assertThat(peers.askedRanges).startsWith(46L, 46L, 41L);
    assertThat(backfill.done()).isCompleted();
    assertBodiesStored(1, HEAD);
  }

  @Test
  void dropsTheBlocksThatFallBelowTheFloorWhileTheyDownload() {
    blockchain = chain(51);
    final AtomicLong floor = new AtomicLong(1);
    // a pruner raises the floor to 44 while the second range, 41 to 45, downloads
    peers.beforeBodies =
        first -> {
          if (first == 41) {
            floor.set(44);
          }
        };
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> floor.get());

    run(backfill);

    assertThat(backfill.done()).isCompleted();
    assertBodiesStored(44, HEAD);
    assertNoBodies(41, 43);
    assertThat(blockchain.getBodiesFrom()).contains(44L);
  }

  @Test
  void recordsTheLowestBlockAfterEveryRange() {
    blockchain = chain(51);
    final List<Long> recorded = new ArrayList<>();
    peers.beforeBodies = first -> recorded.add(blockchain.getBodiesFrom().orElseThrow());
    final BodyBackfill backfill = backfill(0, () -> true, head -> 1);

    run(backfill);

    // a node that stops at any time downloads one range again at most
    assertThat(recorded).containsExactly(51L, 46L, 41L, 36L, 31L, 26L, 21L, 16L, 11L, 6L);
  }

  @Test
  void dropsARangeAReorgReplacedAndAsksItAgain() {
    blockchain = chain(51);
    final Block fork =
        gen.block(
            new BlockOptions()
                .setBlockNumber(50)
                .setParentHash(chain.get(49).getHash())
                .setDifficulty(Difficulty.ZERO)
                .hasOmmers(false)
                .hasTransactions(false)
                .setReceiptsRoot(Hash.EMPTY_TRIE_HASH));
    blocksByHash.put(fork.getHash(), fork);
    final AtomicBoolean reorged = new AtomicBoolean();
    // block 50 leaves the canonical chain while the range that ends with it downloads
    peers.beforeBodies =
        first -> {
          if (first == 46 && reorged.compareAndSet(false, true)) {
            blockchain.storeBlockHeaders(List.of(fork.getHeader()), Difficulty.ONE);
          }
        };
    final BodyBackfill backfill = backfill(Long.MAX_VALUE, () -> true, head -> 1);

    run(backfill);

    assertThat(peers.askedRanges).startsWith(46L, 46L, 41L);
    assertThat(blockchain.getBlockBody(fork.getHash())).contains(fork.getBody());
    assertThat(blockchain.getBlockBody(chain.get(50).getHash())).isEmpty();
    assertBodiesStored(1, 49);
  }

  // two requests, ranges of five blocks, no wait, and the lowest block recorded every so many bytes
  private BodyBackfill backfill(
      final long recordBytes, final BooleanSupplier inSync, final LongUnaryOperator lowestNeeded) {
    return new BodyBackfill(
        blockchain,
        ProtocolScheduleFixture.MAINNET,
        peers,
        peers,
        inSync,
        blockchain.getBodiesFrom().orElseThrow(),
        lowestNeeded,
        new Settings(
            "test", 2, recordBytes, Duration.ZERO, Duration.ZERO, RANGE_BLOCKS, false, true),
        new NoOpMetricsSystem());
  }

  // runs the back-fill to its end, its requests in turn on the test thread
  private static void run(final BodyBackfill backfill) {
    final QueuedExecutor workers = new QueuedExecutor();
    backfill.start(workers);
    workers.runAll();
  }

  // headers 1 to HEAD stored, the head set, and the bodies and receipts from bodiesFrom only
  private MutableBlockchain chain(final long bodiesFrom) {
    final Block genesis = gen.genesisBlock();
    chain.add(genesis);
    final MutableBlockchain stored =
        InMemoryKeyValueStorageProvider.createInMemoryBlockchain(genesis);
    Block parent = genesis;
    for (long number = 1; number <= HEAD; number++) {
      final List<Transaction> transactions =
          List.of(gen.transaction(), gen.transaction(), gen.transaction());
      final List<TransactionReceipt> receipts =
          transactions.stream().map(transaction -> gen.receipt()).toList();
      parent =
          gen.block(
              new BlockOptions()
                  .setBlockNumber(number)
                  .setParentHash(parent.getHash())
                  .setDifficulty(Difficulty.ZERO)
                  .hasOmmers(false)
                  .addTransaction(transactions)
                  .setReceiptsRoot(BodyValidation.receiptsRoot(receipts)));
      chain.add(parent);
      blocksByHash.put(parent.getHash(), parent);
      receiptsByHash.put(parent.getHash(), receipts);
    }
    stored.storeBlockHeaders(
        chain.subList(1, chain.size()).stream().map(Block::getHeader).toList(), Difficulty.ONE);
    for (long number = bodiesFrom; number <= HEAD; number++) {
      final Block block = chain.get((int) number);
      stored.unsafeImportBlock(block, receiptsByHash.get(block.getHash()), Optional.empty());
    }
    stored.unsafeSetChainHead(chain.get(HEAD).getHeader(), Difficulty.ONE);
    stored.unsafeSetBodiesFrom(bodiesFrom);
    return stored;
  }

  // the answer of a peer that has the whole chain
  private MessageData answer(final MessageData message) {
    return switch (message.getCode()) {
      case EthProtocolMessages.GET_BLOCK_BODIES -> {
        final List<Hash> hashes =
            ImmutableList.copyOf(GetBlockBodiesMessage.readFrom(message).hashes());
        final long first = number(hashes.getFirst());
        peers.askedRanges.add(first);
        peers.beforeBodies.accept(first);
        if (peers.failNextBodies) {
          peers.failNextBodies = false;
          yield BlockBodiesMessage.create(List.of());
        }
        yield BlockBodiesMessage.create(
            hashes.stream().map(hash -> blocksByHash.get(hash).getBody()).toList());
      }
      case EthProtocolMessages.GET_RECEIPTS -> {
        final List<Hash> hashes =
            ImmutableList.copyOf(GetReceiptsMessage.readFrom(message).blockHashes());
        peers.beforeReceipts.accept(number(hashes.getFirst()));
        final BytesValueRLPOutput out = new BytesValueRLPOutput();
        out.startList();
        for (final Hash hash : hashes) {
          out.startList();
          receiptsByHash
              .get(hash)
              .forEach(
                  receipt ->
                      TransactionReceiptEncoder.writeTo(
                          receipt,
                          out,
                          TransactionReceiptEncodingConfiguration.DEFAULT_NETWORK_CONFIGURATION));
          out.endList();
        }
        out.endList();
        yield ReceiptsMessage.createUnsafe(out.encoded());
      }
      default -> throw new IllegalArgumentException("unexpected eth message " + message.getCode());
    };
  }

  private long number(final Hash hash) {
    return blocksByHash.get(hash).getHeader().getNumber();
  }

  private void assertBodiesStored(final long from, final long to) {
    for (long number = from; number <= to; number++) {
      final Block block = chain.get((int) number);
      assertThat(blockchain.getBlockBody(block.getHash())).contains(block.getBody());
      assertThat(blockchain.getTxReceipts(block.getHash()))
          .contains(receiptsByHash.get(block.getHash()));
    }
  }

  private void assertNoBodies(final long from, final long to) {
    for (long number = from; number <= to; number++) {
      assertThat(blockchain.getBlockBody(chain.get((int) number).getHash())).isEmpty();
    }
  }

  // a synced and validated peer, as the tasks require
  private static EthPeerImmutableAttributes attributes(final EthPeer peer) {
    return new EthPeerImmutableAttributes(
        UInt256.ZERO, true, Long.MAX_VALUE / 2, 0, 0, 0, false, true, true, true, false, peer);
  }

  private static EthPeer peer() {
    final EthPeer peer = mock(EthPeer.class);
    when(peer.getAgreedCapabilities()).thenReturn(Set.of(EthProtocolVersion.V68.getCapability()));
    when(peer.chainState()).thenReturn(new ChainState());
    return peer;
  }

  /** Peers that answer the requests of the tasks on the calling thread, from the chain. */
  private final class FakePeers extends PeerTaskExecutor implements PeerSelector {
    private final List<EthPeer> all = IntStream.range(0, 4).mapToObj(i -> peer()).toList();
    // the first block of each range whose bodies were asked, in request order
    private final List<Long> askedRanges = new ArrayList<>();
    private int requests;
    private boolean failNextBodies;
    private Predicate<EthPeerImmutableAttributes> lastFilter;
    // called with the first block of each request, before it is answered
    private Consumer<Long> beforeBodies = first -> {};
    private Consumer<Long> beforeReceipts = first -> {};
    private Consumer<EthPeer> duringRequest = peer -> {};

    private FakePeers() {
      super(mock(PeerSelector.class), new PeerTaskRequestSender(), new NoOpMetricsSystem());
    }

    @Override
    public Optional<EthPeer> getPeer(final Predicate<EthPeerImmutableAttributes> filter) {
      lastFilter = filter;
      return all.stream().filter(peer -> filter.test(attributes(peer))).findFirst();
    }

    @Override
    public CompletableFuture<EthPeer> waitForPeer(
        final Predicate<EthPeerImmutableAttributes> filter) {
      return getPeer(filter)
          .map(CompletableFuture::completedFuture)
          .orElseGet(CompletableFuture::new);
    }

    @Override
    public Optional<EthPeer> getPeerByPeerId(final PeerId peerId) {
      return Optional.empty();
    }

    @Override
    public <T> PeerTaskExecutorResult<T> executeAgainstPeer(
        final PeerTask<T> task, final EthPeer peer) {
      requests++;
      duringRequest.accept(peer);
      final Set<Capability> capabilities = peer.getAgreedCapabilities();
      try {
        final T result =
            task.processResponse(answer(task.getRequestMessage(capabilities)), capabilities);
        final boolean valid =
            task.validateResult(result) == PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD;
        return new PeerTaskExecutorResult<>(
            Optional.of(result),
            valid
                ? PeerTaskExecutorResponseCode.SUCCESS
                : PeerTaskExecutorResponseCode.INVALID_RESPONSE,
            List.of(peer));
      } catch (final InvalidPeerTaskResponseException | MalformedRlpFromPeerException e) {
        return new PeerTaskExecutorResult<>(
            Optional.empty(), PeerTaskExecutorResponseCode.INVALID_RESPONSE, List.of(peer));
      }
    }
  }
}
