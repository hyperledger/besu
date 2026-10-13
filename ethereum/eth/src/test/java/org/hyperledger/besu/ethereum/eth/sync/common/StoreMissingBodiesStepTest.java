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
package org.hyperledger.besu.ethereum.eth.sync.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.MissingBlockBodies;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

public class StoreMissingBodiesStepTest {

  private static final int CHAIN_HEAD = 12;
  private static final MissingBlockBodies MISSING = new MissingBlockBodies(1, 9);
  private static final Difficulty TOTAL_DIFFICULTY = Difficulty.of(42);

  // the index of a block is its number
  private final List<Block> chain = new BlockDataGenerator(1).blockSequence(CHAIN_HEAD + 1);
  private final MutableBlockchain blockchain = createInMemoryBlockchain(chain.getFirst());
  private final MutableClock clock = new MutableClock();
  private StoreMissingBodiesStep storeStep;

  @BeforeEach
  public void setUp() {
    // the headers are there, and the blocks above the missing ones have their bodies
    blockchain.storeBlockHeaders(
        chain.subList(1, CHAIN_HEAD + 1).stream().map(Block::getHeader).toList());
    blockchain.unsafeStoreSyncBodiesAndReceipts(batch(10, CHAIN_HEAD), false);
    blockchain.unsafeSetMissingBlockBodies(Optional.of(MISSING));
    storeStep = new StoreMissingBodiesStep(blockchain, MISSING, TOTAL_DIFFICULTY, false, clock);
  }

  @Test
  public void shouldStoreBodiesAndTotalDifficultyOfABatch() {
    storeStep.accept(batch(1, 3));

    for (int number = 1; number <= 3; number++) {
      assertThat(blockchain.getBlockBody(chain.get(number).getHash())).isPresent();
      assertThat(blockchain.getTxReceipts(chain.get(number).getHash())).isPresent();
      assertThat(blockchain.getTotalDifficultyByHash(chain.get(number).getHash()))
          .contains(TOTAL_DIFFICULTY);
    }
    assertThat(blockchain.getBlockBody(chain.get(4).getHash())).isEmpty();
    assertThat(storeStep.lastBlockStoredInOrder()).isEqualTo(3);
  }

  @Test
  public void shouldNotTouchTheChainHead() {
    final long chainHead = blockchain.getChainHeadBlockNumber();

    storeStep.accept(batch(1, 9));

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(chainHead);
  }

  @Test
  public void shouldOnlyCountBlocksThatHaveEveryBlockBeforeThemStored() {
    storeStep.accept(batch(4, 6));

    // stored, but not counted while the batch before it is missing
    assertThat(blockchain.getBlockBody(chain.get(5).getHash())).isPresent();
    assertThat(storeStep.lastBlockStoredInOrder()).isZero();

    storeStep.accept(batch(1, 3));

    assertThat(storeStep.lastBlockStoredInOrder()).isEqualTo(6);
  }

  @Test
  public void shouldRecordWhatIsStillMissingAfterAWhile() {
    storeStep.accept(batch(1, 3));
    // not recorded with every batch
    assertThat(blockchain.getMissingBlockBodies()).contains(MISSING);

    clock.advance(StoreMissingBodiesStep.RECORD_INTERVAL_MILLIS);
    storeStep.accept(batch(4, 6));

    assertThat(blockchain.getMissingBlockBodies()).contains(new MissingBlockBodies(7, 9));
    assertThat(blockchain.getEarliestBlockNumber()).contains(10L);
  }

  @Test
  public void shouldNotRecordABatchStoredAheadAsDone() {
    clock.advance(StoreMissingBodiesStep.RECORD_INTERVAL_MILLIS);
    storeStep.accept(batch(7, 9));

    assertThat(blockchain.getMissingBlockBodies()).contains(MISSING);
  }

  @Test
  public void shouldRecordThatNothingIsMissingWhenTheLastBatchIsStored() {
    storeStep.accept(batch(7, 9));
    storeStep.accept(batch(1, 3));
    storeStep.accept(batch(4, 6));

    assertThat(blockchain.getMissingBlockBodies()).isEmpty();
    // every block of the chain is there now
    assertThat(blockchain.getEarliestBlockNumber()).contains(0L);
  }

  @Test
  public void shouldCopeWithABatchThatWasStoredBefore() {
    storeStep.accept(batch(1, 3));
    storeStep.accept(batch(1, 3));
    storeStep.accept(batch(4, 9));

    assertThat(blockchain.getMissingBlockBodies()).isEmpty();
  }

  @Test
  public void shouldPersistWhatIsStoredBeforeRecordingIt() {
    final MutableBlockchain recordingBlockchain = spy(blockchain);
    final StoreMissingBodiesStep step = stepOn(recordingBlockchain, millis -> {});

    step.accept(batch(1, 3));
    // nothing recorded yet, so nothing needs to be on disk
    verify(recordingBlockchain, never()).unsafePersistSyncBodiesAndReceipts();

    clock.advance(StoreMissingBodiesStep.RECORD_INTERVAL_MILLIS);
    step.accept(batch(4, 6));
    step.accept(batch(7, 9));

    final InOrder inOrder = inOrder(recordingBlockchain);
    inOrder.verify(recordingBlockchain).unsafePersistSyncBodiesAndReceipts();
    inOrder
        .verify(recordingBlockchain)
        .unsafeSetMissingBlockBodies(Optional.of(new MissingBlockBodies(7, 9)));
    inOrder.verify(recordingBlockchain).unsafePersistSyncBodiesAndReceipts();
    inOrder.verify(recordingBlockchain).unsafeSetMissingBlockBodies(Optional.empty());
  }

  @Test
  public void shouldStoreABatchAtOnceWhenTheStorageHasNothingToFlush() {
    final List<Long> pauses = new ArrayList<>();
    final StoreMissingBodiesStep step = stepOn(blockchain, pauses::add);

    step.accept(batch(1, 3));

    assertThat(pauses).isEmpty();
    assertThat(blockchain.getBlockBody(chain.get(1).getHash())).isPresent();
  }

  @Test
  public void shouldWaitForTheStorageAndThenAsLongAgain() {
    final MutableBlockchain busyBlockchain = spy(blockchain);
    when(busyBlockchain.isStorageWaitingForFlush()).thenReturn(true, true, true, false);
    final List<Long> pauses = new ArrayList<>();
    final StoreMissingBodiesStep step =
        stepOn(
            busyBlockchain,
            millis -> {
              // nothing is stored while the storage is waited for
              assertThat(blockchain.getBlockBody(chain.get(1).getHash())).isEmpty();
              pauses.add(millis);
            });

    step.accept(batch(1, 3));

    final long poll = StoreMissingBodiesStep.FLUSH_POLL_MILLIS;
    assertThat(pauses).containsExactly(poll, poll, poll, 3 * poll);
    assertThat(blockchain.getBlockBody(chain.get(1).getHash())).isPresent();
  }

  @Test
  public void shouldStoreABatchAfterTheLongestWaitWhateverTheStorageSays() {
    final MutableBlockchain stuckBlockchain = spy(blockchain);
    when(stuckBlockchain.isStorageWaitingForFlush()).thenReturn(true);
    final List<Long> pauses = new ArrayList<>();
    final StoreMissingBodiesStep step = stepOn(stuckBlockchain, pauses::add);

    step.accept(batch(1, 3));

    final long polls =
        StoreMissingBodiesStep.MAX_FLUSH_WAIT_MILLIS / StoreMissingBodiesStep.FLUSH_POLL_MILLIS;
    assertThat(pauses).hasSize((int) polls + 1);
    assertThat(pauses.getLast()).isEqualTo(StoreMissingBodiesStep.MAX_FLUSH_WAIT_MILLIS);
    assertThat(blockchain.getBlockBody(chain.get(1).getHash())).isPresent();
  }

  @Test
  public void shouldStoreTheBatchWhenTheWaitIsInterrupted() {
    final MutableBlockchain busyBlockchain = spy(blockchain);
    when(busyBlockchain.isStorageWaitingForFlush()).thenReturn(true);
    final StoreMissingBodiesStep step =
        stepOn(
            busyBlockchain,
            millis -> {
              throw new InterruptedException();
            });

    step.accept(batch(1, 3));

    // the thread stays interrupted for the pipeline that runs the step
    assertThat(Thread.interrupted()).isTrue();
    assertThat(blockchain.getBlockBody(chain.get(1).getHash())).isPresent();
  }

  @Test
  public void shouldKeepToTheRateLimit() {
    final List<Long> pauses = new ArrayList<>();
    final StoreMissingBodiesStep step =
        new StoreMissingBodiesStep(
            blockchain,
            MISSING,
            TOTAL_DIFFICULTY,
            false,
            1,
            clock,
            millis -> {
              pauses.add(millis);
              clock.advance(millis);
            });
    final List<SyncBlockWithReceipts> first = withLargeReceipts(batch(1, 3));
    final long firstBytes = bytes(first);
    assertThat(firstBytes).isGreaterThan(600_000);

    // the first batch goes at once, the next waits until the first is paid for at 1 MB/s
    step.accept(first);
    assertThat(pauses).isEmpty();
    step.accept(withLargeReceipts(batch(4, 6)));
    assertThat(pauses).containsExactly(firstBytes * 1000 / 1_000_000);
    assertThat(blockchain.getBlockBody(chain.get(6).getHash())).isPresent();
  }

  @Test
  public void shouldNotWaitForTheRateLimitWhenTheTimeIsPaidForAlready() {
    final List<Long> pauses = new ArrayList<>();
    final StoreMissingBodiesStep step =
        new StoreMissingBodiesStep(
            blockchain, MISSING, TOTAL_DIFFICULTY, false, 1, clock, pauses::add);

    step.accept(withLargeReceipts(batch(1, 3)));
    clock.advance(60_000);
    step.accept(withLargeReceipts(batch(4, 6)));

    assertThat(pauses).isEmpty();
  }

  /** The blocks, each with a receipt of 200 kB, so that storing them takes a while at 1 MB/s. */
  private static List<SyncBlockWithReceipts> withLargeReceipts(
      final List<SyncBlockWithReceipts> batch) {
    return batch.stream()
        .map(
            block ->
                new SyncBlockWithReceipts(
                    block.getBlock(),
                    List.of(
                        new SyncTransactionReceipt(RLP.encodeOne(Bytes.wrap(new byte[200_000]))))))
        .toList();
  }

  private static long bytes(final List<SyncBlockWithReceipts> batch) {
    return batch.stream()
        .mapToLong(
            block ->
                block.getBlock().getBody().getRlp().size()
                    + block.getReceipts().stream().mapToLong(r -> r.getRlpBytes().size()).sum())
        .sum();
  }

  private StoreMissingBodiesStep stepOn(
      final MutableBlockchain onBlockchain, final StoreMissingBodiesStep.Pause pause) {
    return new StoreMissingBodiesStep(
        onBlockchain, MISSING, TOTAL_DIFFICULTY, false, 0, clock, pause);
  }

  /** The blocks with the given numbers, as a batch of the download. */
  private List<SyncBlockWithReceipts> batch(final int firstBlock, final int lastBlock) {
    return chain.subList(firstBlock, lastBlock + 1).stream()
        .map(
            block ->
                new SyncBlockWithReceipts(
                    new SyncBlock(
                        block.getHeader(),
                        SyncBlockBody.emptyWithNullWithdrawals(
                            ProtocolScheduleFixture.TESTING_NETWORK)),
                    Collections.emptyList()))
        .toList();
  }

  private static class MutableClock extends Clock {
    private long millis = 1_000_000;

    void advance(final long byMillis) {
      millis += byMillis;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis);
    }

    @Override
    public long millis() {
      return millis;
    }
  }
}
