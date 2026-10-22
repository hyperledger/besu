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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.sync.common.WrongChainException;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestSender;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Protocol;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class HeaderBackfillTest {

  private static final int HEADERS_PER_REQUEST = 10;
  // more than the segments, so the pipeline buffers hold them all and its stages run in turn
  private static final int PARALLELISM = 16;

  private final List<BlockHeader> chain = chain(101);
  private final FakeSender sender = new FakeSender();
  private final QueuedExecutor executor = new QueuedExecutor();
  private final List<BlockHeader> stored = new ArrayList<>();

  @Test
  void storesTheHeadersDownToTheFloor() {
    sender.serve(chain);
    final HeaderBackfill backfill = backfill(chain.getFirst(), chain.get(100));

    final CompletableFuture<Void> done = backfill.run(executor);
    executor.runAll();

    assertThat(done).isCompleted();
    assertThat(backfill.lowestStored()).isEqualTo(1);
    // top down, each segment highest first
    assertThat(stored).containsExactlyElementsOf(chain.subList(1, 100).reversed());
  }

  @Test
  void followsTheTrustedHeaderWhenPeersServeAnotherChain() {
    // the peers serve by number a fork from block 31, the top stays on the trusted chain
    sender.serve(fork(chain.subList(0, 31), 60));
    sender.know(chain);
    final HeaderBackfill backfill = backfill(chain.getFirst(), chain.get(60));

    final CompletableFuture<Void> done = backfill.run(executor);
    executor.runAll();

    assertThat(done).isCompleted();
    assertThat(stored).containsExactlyElementsOf(chain.subList(1, 60).reversed());
  }

  @Test
  void failsWhenTheHeadersDoNotLeadToTheFloor() {
    sender.serve(chain);
    final BlockHeader otherFloor = new BlockHeaderTestFixture().number(5).buildHeader();
    final HeaderBackfill backfill = backfill(otherFloor, chain.get(20));

    final CompletableFuture<Void> done = backfill.run(executor);
    executor.runAll();

    assertThatThrownBy(done::join)
        .hasCauseInstanceOf(WrongChainException.class)
        .hasMessageContaining("does not lead to block 5");
  }

  @Test
  void storesNothingOnceStopped() {
    sender.serve(chain);
    final HeaderBackfill backfill = backfill(chain.getFirst(), chain.get(100));
    backfill.stop();

    final CompletableFuture<Void> done = backfill.run(executor);
    executor.runAll();

    assertThat(done).isCompletedExceptionally();
    assertThat(stored).isEmpty();
  }

  private HeaderBackfill backfill(final BlockHeader floor, final BlockHeader top) {
    return new HeaderBackfill(
        sender,
        ProtocolScheduleFixture.MAINNET,
        stored::addAll,
        new HeaderFetcher(
            sender, ProtocolScheduleFixture.MAINNET, Optional::empty, HEADERS_PER_REQUEST),
        floor,
        top,
        HEADERS_PER_REQUEST,
        PARALLELISM,
        new NoOpMetricsSystem());
  }

  // headers 0 to count - 1, each the parent of the next
  private static List<BlockHeader> chain(final int count) {
    final List<BlockHeader> headers = new ArrayList<>();
    headers.add(child(null, 0, 0));
    while (headers.size() < count) {
      headers.add(child(headers.getLast(), headers.size(), 0));
    }
    return headers;
  }

  // the base, then a chain of its own up to the highest block, its headers stamped apart
  private static List<BlockHeader> fork(final List<BlockHeader> base, final long highest) {
    final List<BlockHeader> headers = new ArrayList<>(base);
    while (headers.getLast().getNumber() < highest) {
      headers.add(child(headers.getLast(), headers.getLast().getNumber() + 1, 1));
    }
    return headers;
  }

  private static BlockHeader child(final BlockHeader parent, final long number, final int fork) {
    final BlockHeaderTestFixture fixture =
        new BlockHeaderTestFixture().number(number).timestamp(1_000L * fork + number);
    if (parent != null) {
      fixture.parentHash(parent.getHash());
    }
    return fixture.buildHeader();
  }

  /**
   * Answers header requests on the calling thread: by number from the chain it serves, by hash from
   * every header it knows.
   */
  private static final class FakeSender implements RequestSender {
    private final EthPeer peer = mock(EthPeer.class);
    private final Map<Long, BlockHeader> served = new HashMap<>();
    private final Map<Hash, BlockHeader> known = new HashMap<>();

    private FakeSender() {
      when(peer.getAgreedCapabilities()).thenReturn(Set.of(EthProtocol.LATEST));
      // header tasks report the height they see to the peer
      when(peer.chainState()).thenReturn(new ChainState());
    }

    // served by number, and known by hash
    private void serve(final List<BlockHeader> headers) {
      headers.forEach(header -> served.put(header.getNumber(), header));
      know(headers);
    }

    // known by hash only
    private void know(final List<BlockHeader> headers) {
      headers.forEach(header -> known.put(header.getHash(), header));
    }

    @Override
    public <T> CompletableFuture<Response<T>> submit(
        final SyncRequest<T> request, final Set<EthPeer> excluded) {
      final GetBlockHeadersMessage message = GetBlockHeadersMessage.readFrom(request.message(peer));
      // a reverse request, from the header asked for down its own chain
      BlockHeader header =
          message
              .hash()
              .map(known::get)
              .orElseGet(() -> served.get(message.blockNumber().getAsLong()));
      final List<BlockHeader> headers = new ArrayList<>();
      while (header != null && headers.size() < message.maxHeaders()) {
        headers.add(header);
        header = known.get(header.getParentHash());
      }
      return CompletableFuture.completedFuture(
          new Response<>(request.decode(BlockHeadersMessage.create(headers), peer), peer));
    }

    @Override
    public <T> CompletableFuture<Response<T>> submitWithRetries(
        final SyncRequest<T> request, final int attempts) {
      return submit(request);
    }

    @Override
    public int peerCount(final Protocol protocol) {
      return 1;
    }

    @Override
    public int capacity(final Protocol protocol) {
      return 1;
    }

    @Override
    public Executor workers() {
      return Runnable::run;
    }
  }

  /** Keeps what the pipeline submits, to run it on the test thread when the test says. */
  private static final class QueuedExecutor extends AbstractExecutorService {
    private final List<Runnable> tasks = new ArrayList<>();

    private void runAll() {
      for (final Runnable task : tasks) {
        task.run();
        // a stage interrupts its thread, this one here, as operations complete or on an abort
        Thread.interrupted();
      }
    }

    @Override
    public void execute(final Runnable command) {
      tasks.add(command);
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return true;
    }
  }
}
