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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem.NO_OP_LABELLED_TIMER;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContextTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthMessage;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

public class CheckpointHeaderManagerTest {

  private static final BlockHeader GENESIS = block(0);
  private static final int SEGMENT_SIZE = 5;
  private static final int HEADER_REQUEST_SIZE = 3;

  private static final ProtocolSchedule<Void> PROTOCOL_SCHEDULE = MainnetProtocolSchedule.create();

  private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final ProtocolContext<Void> protocolContext =
      new ProtocolContext<>(blockchain, worldStateArchive, null);

  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private final EthContext ethContext = EthContextTestUtil.createTestEthContext(timeout::get);
  private final SyncState syncState = new SyncState(blockchain, ethContext.getEthPeers());
  private final LabelledMetric<OperationTimer> ethTasksTimer = NO_OP_LABELLED_TIMER;
  private final EthPeer syncTargetPeer = mock(EthPeer.class);
  private final RequestManager requestManager = new RequestManager(syncTargetPeer);
  private SyncTarget syncTarget;

  private final CheckpointHeaderManager<Void> checkpointHeaderManager =
      new CheckpointHeaderManager<>(
          SynchronizerConfiguration.builder()
              .downloaderChainSegmentSize(SEGMENT_SIZE)
              .downloaderHeadersRequestSize(HEADER_REQUEST_SIZE)
              .downloaderCheckpointTimeoutsPermitted(2)
              .build(),
          protocolContext,
          ethContext,
          syncState,
          PROTOCOL_SCHEDULE,
          ethTasksTimer);

  @Before
  public void setUp() {
    when(syncTargetPeer.chainState()).thenReturn(new ChainState());
    syncTarget = syncState.setSyncTarget(syncTargetPeer, GENESIS);
  }

  @Test
  public void shouldHandleErrorsWhenRequestingHeaders() throws Exception {
    when(anyHeadersRequested()).thenThrow(new PeerNotConnected("Nope"));

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(emptyList());
  }

  @Test
  public void shouldHandleTimeouts() throws Exception {
    timeout.set(true);
    when(anyHeadersRequested()).thenReturn(createResponseStream(), createResponseStream());

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(emptyList());
    assertThat(checkpointHeaderManager.checkpointsHaveTimedOut()).isFalse();

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(emptyList());
    assertThat(checkpointHeaderManager.checkpointsHaveTimedOut()).isTrue();
  }

  @Test
  public void shouldResetTimeoutWhenHeadersReceived() throws Exception {
    // Timeout
    timeout.set(true);
    when(anyHeadersRequested()).thenReturn(createResponseStream());

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(emptyList());
    assertThat(checkpointHeaderManager.checkpointsHaveTimedOut()).isFalse();

    // Receive response
    reset(syncTargetPeer);
    respondToHeaderRequests(GENESIS, block(5));
    timeout.set(false);
    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(GENESIS, block(5)));
    assertThat(checkpointHeaderManager.checkpointsHaveTimedOut()).isFalse();

    // Timeout again but shouldn't have reached threshold
    reset(syncTargetPeer);
    timeout.set(true);
    when(anyHeadersRequested()).thenReturn(createResponseStream());
    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(GENESIS, block(5)));
    assertThat(checkpointHeaderManager.checkpointsHaveTimedOut()).isFalse();
  }

  @Test
  public void shouldUseReturnedHeadersAsCheckpointHeaders() throws Exception {
    respondToHeaderRequests(GENESIS, block(5), block(10));

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(GENESIS, block(5), block(10)));
  }

  @Test
  public void shouldPullAdditionalCheckpointsWhenRequired() throws Exception {
    respondToHeaderRequests(GENESIS, block(5));
    respondToHeaderRequests(block(5), block(10), block(15), block(20));

    // Pull initial headers
    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(GENESIS, block(5)));

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(GENESIS, block(5), block(10), block(15), block(20)));
  }

  @Test
  public void shouldRemoveImportedCheckpointHeaders() throws Exception {
    respondToHeaderRequests(GENESIS, block(5), block(10));
    respondToHeaderRequests(block(10));

    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(GENESIS, block(5), block(10)));

    when(blockchain.contains(GENESIS.getHash())).thenReturn(true);
    when(blockchain.contains(block(5).getHash())).thenReturn(true);
    when(blockchain.contains(block(10).getHash())).thenReturn(false);
    checkpointHeaderManager.clearImportedCheckpointHeaders();

    // The first checkpoint header should always be in the blockchain (just as geneis was present)
    assertThat(checkpointHeaderManager.pullCheckpointHeaders(syncTarget))
        .isCompletedWithValue(asList(block(5), block(10)));
  }

  private void respondToHeaderRequests(final BlockHeader... headers) throws Exception {
    final ResponseStream responseStream = createResponseStream();
    when(syncTargetPeer.getHeadersByHash(
            headers[0].getHash(), HEADER_REQUEST_SIZE + 1, SEGMENT_SIZE - 1, false))
        .thenReturn(responseStream);
    requestManager.dispatchResponse(
        new EthMessage(syncTargetPeer, BlockHeadersMessage.create(asList(headers))));
  }

  private static BlockHeader block(final int blockNumber) {
    return new BlockHeaderTestFixture().number(blockNumber).buildHeader();
  }

  private ResponseStream createResponseStream() throws PeerNotConnected {
    return requestManager.dispatchRequest(() -> {});
  }

  private ResponseStream anyHeadersRequested() throws PeerNotConnected {
    return syncTargetPeer.getHeadersByHash(any(Hash.class), anyInt(), anyInt(), anyBoolean());
  }
}
