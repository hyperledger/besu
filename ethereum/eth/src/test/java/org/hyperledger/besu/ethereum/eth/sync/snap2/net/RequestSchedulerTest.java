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
package org.hyperledger.besu.ethereum.eth.sync.snap2.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestFailedException.Reason;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Priority;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Requests going through real peers, request managers and response streams. */
@Timeout(30)
class RequestSchedulerTest {

  private static final Duration TIMEOUT = Duration.ofMillis(500);

  private MutableBlockchain blockchain;
  private ProtocolSchedule protocolSchedule;
  private EthProtocolManager ethProtocolManager;
  private EthPeers ethPeers;
  private RespondingEthPeer peer;
  private TestLoop loop;
  private RequestScheduler scheduler;
  // a clock that only moves when a test moves it
  private final AtomicLong clock = new AtomicLong();

  @BeforeEach
  void setUp() {
    final BlockchainSetupUtil setup = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);
    setup.importFirstBlocks(10);
    blockchain = setup.getBlockchain();
    protocolSchedule = setup.getProtocolSchedule();
    ethProtocolManager = EthProtocolManagerTestBuilder.builder().setBlockchain(blockchain).build();
    ethPeers = ethProtocolManager.ethContext().getEthPeers();
    peer = newPeer();
    scheduler = newScheduler(TIMEOUT, 4, System::nanoTime);
  }

  @AfterEach
  void tearDown() {
    scheduler.stop();
  }

  @Test
  void deliversTheResponseOfAPeer() throws Exception {
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> response =
        scheduler.submit(headers(1, 5));

    peer.respondWhileOtherThreadsWork(
        RespondingEthPeer.blockchainResponder(blockchain), () -> !response.isDone());

    assertThat(response.get(5, TimeUnit.SECONDS).value())
        .extracting(BlockHeader::getNumber)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void absorbsAResponseThatArrivesAfterTheTimeout() throws Exception {
    // the request is abandoned one timeout after it timed out: held, it waits for the test
    loop.hold();
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> late =
        scheduler.submit(headers(1, 2));
    await().until(() -> loop.heldCount() == 1);
    loop.release();
    assertFailure(() -> late.get(5, TimeUnit.SECONDS), Reason.TIMEOUT);

    // the late response is absorbed, not counted as the answer to an unknown request
    final int score = peer.getEthPeer().getReputation().getScore();
    peer.respond(RespondingEthPeer.blockchainResponder(blockchain));
    assertThat(peer.getEthPeer().getReputation().getScore()).isEqualTo(score);

    // and the peer keeps serving
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> next =
        scheduler.submit(headers(3, 2));
    peer.respondWhileOtherThreadsWork(
        RespondingEthPeer.blockchainResponder(blockchain), () -> !next.isDone());

    assertThat(next.get(5, TimeUnit.SECONDS).value())
        .extracting(BlockHeader::getNumber)
        .containsExactly(3L, 4L);
    assertThat(peer.getEthPeer().isDisconnected()).isFalse();
  }

  @Test
  void givesBackTheSlotOfARequestNeverAnswered() throws Exception {
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> lost =
        scheduler.submit(headers(1, 2));
    assertFailure(() -> lost.get(5, TimeUnit.SECONDS), Reason.TIMEOUT);
    // the peer drops it without ever answering
    peer.respond((capability, ethPeer, message) -> Optional.empty());

    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> next =
        scheduler.submit(headers(3, 2));
    peer.respondWhileOtherThreadsWork(
        RespondingEthPeer.blockchainResponder(blockchain), () -> !next.isDone());

    assertThat(next.get(5, TimeUnit.SECONDS).value())
        .extracting(BlockHeader::getNumber)
        .containsExactly(3L, 4L);
  }

  @Test
  void sendsTheMostUrgentRequestFirst() {
    // two requests take the two slots the peer starts with
    scheduler.submit(headers(1, 1));
    scheduler.submit(headers(2, 1));
    await().until(() -> peer.streamPendingOutgoingRequests().count() == 2);
    scheduler.submit(headers(3, 1));
    scheduler.submit(headers(4, 1, Priority.PIVOT));

    peer.respond(RespondingEthPeer.blockchainResponder(blockchain));

    await().until(() -> peer.streamPendingOutgoingRequests().count() == 2);
    final GetBlockHeadersMessage next =
        GetBlockHeadersMessage.readFrom(
            peer.peekNextOutgoingRequest().orElseThrow().unwrapMessageData().getValue());
    assertThat(next.blockNumber()).hasValue(4L);
  }

  @Test
  void keepsAskingAPeerThatHadNothingForAnEarlierRequest() throws Exception {
    // the clock stands still: a back-off would hold the peer for ever
    newScheduler(TIMEOUT, 4, clock::get);
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> pruned =
        scheduler.submit(headers(1, 2));
    await().until(peer::hasOutstandingRequests);
    peer.respond(
        (capability, ethPeer, message) -> Optional.of(BlockHeadersMessage.create(List.of())));
    assertFailure(() -> pruned.get(5, TimeUnit.SECONDS), Reason.EMPTY);

    // once the empty answer is recorded, the peer is asked again right away, not after a back-off
    Thread.sleep(200);
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> next =
        scheduler.submit(headers(3, 2));
    await().until(() -> nextRequestedHeader().equals(OptionalLong.of(3)));
    peer.respondWhileOtherThreadsWork(
        RespondingEthPeer.blockchainResponder(blockchain), () -> !next.isDone());
    assertThat(next.get(5, TimeUnit.SECONDS).value())
        .extracting(BlockHeader::getNumber)
        .containsExactly(3L, 4L);
  }

  @Test
  void neverSendsARequestToAnExcludedPeer() {
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> response =
        scheduler.submit(headers(1, 2), Set.of(peer.getEthPeer()));

    assertFailure(() -> response.get(10, TimeUnit.SECONDS), Reason.NO_PEER);
    assertThat(peer.hasOutstandingRequests()).isFalse();
  }

  @Test
  void retriesARequestUntilAPeerAnswersIt() throws Exception {
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> response =
        scheduler.submitWithRetries(headers(1, 2), 5);

    // the first attempt times out, then the peer is tried again once no other peer is left
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> peer.streamPendingOutgoingRequests().count() == 2);
    peer.respondWhileOtherThreadsWork(
        RespondingEthPeer.blockchainResponder(blockchain), () -> !response.isDone());

    assertThat(response.get(5, TimeUnit.SECONDS).value())
        .extracting(BlockHeader::getNumber)
        .containsExactly(1L, 2L);
  }

  @Test
  void failsARequestOnceItsAttemptsRunOut() {
    final CompletableFuture<RequestSender.Response<List<BlockHeader>>> response =
        scheduler.submitWithRetries(headers(1, 2), 2);

    // the peer never answers: a timeout, then no peer left to try
    assertFailure(() -> response.get(10, TimeUnit.SECONDS), Reason.NO_PEER);
    assertThat(peer.streamPendingOutgoingRequests()).hasSize(1);
  }

  private static void assertFailure(final ThrowingCallable call, final Reason reason) {
    assertThatThrownBy(call)
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOfSatisfying(
            RequestFailedException.class,
            failure -> assertThat(failure.reason()).isEqualTo(reason));
  }

  // replaces the scheduler of the test
  private RequestScheduler newScheduler(
      final Duration timeout, final int maxEthRequests, final LongSupplier nanoTime) {
    if (scheduler != null) {
      scheduler.stop();
    }
    loop = new TestLoop();
    scheduler =
        new RequestScheduler(
            ethPeers, new NoOpMetricsSystem(), timeout, 4, maxEthRequests, 2, loop, nanoTime);
    return scheduler;
  }

  private RespondingEthPeer newPeer() {
    return RespondingEthPeer.builder()
        .ethProtocolManager(ethProtocolManager)
        .chainHeadHash(blockchain.getChainHeadHash())
        .totalDifficulty(blockchain.getChainHead().getTotalDifficulty())
        .estimatedHeight(blockchain.getChainHeadBlockNumber())
        .build();
  }

  private OptionalLong nextRequestedHeader() {
    return peer.peekNextOutgoingRequest()
        .map(
            request ->
                GetBlockHeadersMessage.readFrom(request.unwrapMessageData().getValue())
                    .blockNumber())
        .orElse(OptionalLong.empty());
  }

  private PeerTaskRequest<List<BlockHeader>> headers(final long first, final int count) {
    return headers(first, count, Priority.HEADERS);
  }

  private PeerTaskRequest<List<BlockHeader>> headers(
      final long first, final int count, final Priority priority) {
    return new PeerTaskRequest<>(
        () -> new GetHeadersFromPeerTask(first, count, 0, Direction.FORWARD, protocolSchedule),
        priority,
        "headers");
  }
}
