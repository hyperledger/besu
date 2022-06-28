/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloadProcess;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.testutil.TestClock;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
public class SnapWorldDownloadStateTest {

  private static final Bytes ROOT_NODE_DATA = Bytes.of(1, 2, 3, 4);
  private static final Hash ROOT_NODE_HASH = Hash.hash(ROOT_NODE_DATA);
  private static final int MAX_REQUESTS_WITHOUT_PROGRESS = 10;
  private static final long MIN_MILLIS_BEFORE_STALLING = 50_000;

  private WorldStateStorage worldStateStorage;
  private final BlockHeader header =
      new BlockHeaderTestFixture().stateRoot(ROOT_NODE_HASH).buildHeader();
  private final InMemoryTasksPriorityQueues<SnapDataRequest> pendingRequests =
      new InMemoryTasksPriorityQueues<>();
  private final WorldStateDownloadProcess worldStateDownloadProcess =
      mock(WorldStateDownloadProcess.class);
  private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
  private final SnapsyncMetricsManager metricsManager = mock(SnapsyncMetricsManager.class);

  private final TestClock clock = new TestClock();
  private SnapWorldDownloadState downloadState;

  private CompletableFuture<Void> future;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{DataStorageFormat.BONSAI}, {DataStorageFormat.FOREST}});
  }

  private final DataStorageFormat storageFormat;

  public SnapWorldDownloadStateTest(final DataStorageFormat storageFormat) {
    this.storageFormat = storageFormat;
  }

  @Before
  public void setUp() {

    when(metricsManager.getMetricsSystem()).thenReturn(new NoOpMetricsSystem());

    if (storageFormat == DataStorageFormat.BONSAI) {
      worldStateStorage =
          new BonsaiWorldStateKeyValueStorage(new InMemoryKeyValueStorageProvider());
    } else {
      worldStateStorage = new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    }
    downloadState =
        new SnapWorldDownloadState(
            worldStateStorage,
            snapSyncState,
            pendingRequests,
            MAX_REQUESTS_WITHOUT_PROGRESS,
            MIN_MILLIS_BEFORE_STALLING,
            metricsManager,
            clock);
    final DynamicPivotBlockManager dynamicPivotBlockManager = mock(DynamicPivotBlockManager.class);
    doAnswer(
            invocation -> {
              BiConsumer<BlockHeader, Boolean> callback = invocation.getArgument(0);
              callback.accept(header, false);
              return null;
            })
        .when(dynamicPivotBlockManager)
        .switchToNewPivotBlock(any());
    downloadState.setDynamicPivotBlockManager(dynamicPivotBlockManager);
    downloadState.setRootNodeData(ROOT_NODE_DATA);
    future = downloadState.getDownloadFuture();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldCompleteReturnedFutureWhenNoPendingTasksRemain() {
    when(snapSyncState.isHealInProgress()).thenReturn(true);
    downloadState.checkCompletion(header);

    assertThat(future).isCompleted();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @Test
  public void shouldStartHealWhenNoSnapsyncPendingTasksRemain() {
    when(snapSyncState.isHealInProgress()).thenReturn(false);
    when(snapSyncState.getPivotBlockHeader()).thenReturn(Optional.of(mock(BlockHeader.class)));
    assertThat(downloadState.pendingTrieNodeRequests.isEmpty()).isTrue();

    downloadState.checkCompletion(header);
    assertThat(downloadState.isDownloading()).isTrue();
    assertThat(downloadState.pendingTrieNodeRequests.isEmpty()).isFalse();
  }

  @Test
  public void shouldStoreRootNodeBeforeReturnedFutureCompletes() {
    when(snapSyncState.isHealInProgress()).thenReturn(true);
    final CompletableFuture<Void> postFutureChecks =
        future.thenAccept(
            result ->
                assertThat(worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, ROOT_NODE_HASH))
                    .contains(ROOT_NODE_DATA));

    downloadState.checkCompletion(header);

    assertThat(future).isCompleted();
    assertThat(postFutureChecks).isCompleted();
  }

  @Test
  public void shouldNotCompleteWhenThereAreAccountPendingTasks() {
    when(snapSyncState.isHealInProgress()).thenReturn(false);
    downloadState.pendingAccountRequests.add(
        SnapDataRequest.createAccountDataRequest(
            Hash.EMPTY_TRIE_HASH,
            Hash.wrap(Bytes32.random()),
            RangeManager.MIN_RANGE,
            RangeManager.MAX_RANGE));

    downloadState.checkCompletion(header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, ROOT_NODE_HASH)).isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldNotCompleteWhenThereAreStoragePendingTasks() {
    when(snapSyncState.isHealInProgress()).thenReturn(false);
    downloadState.pendingStorageRequests.add(
        SnapDataRequest.createStorageTrieNodeDataRequest(
            Hash.EMPTY_TRIE_HASH, Hash.wrap(Bytes32.random()), Hash.EMPTY_TRIE_HASH, Bytes.EMPTY));

    downloadState.checkCompletion(header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, ROOT_NODE_HASH)).isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();

    downloadState.pendingBigStorageRequests.add(
        SnapDataRequest.createStorageTrieNodeDataRequest(
            Hash.EMPTY_TRIE_HASH, Hash.wrap(Bytes32.random()), Hash.EMPTY_TRIE_HASH, Bytes.EMPTY));

    downloadState.checkCompletion(header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, ROOT_NODE_HASH)).isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldNotCompleteWhenThereAreTriePendingTasks() {
    when(snapSyncState.isHealInProgress()).thenReturn(true);
    downloadState.pendingTrieNodeRequests.add(
        SnapDataRequest.createAccountTrieNodeDataRequest(
            Hash.wrap(Bytes32.random()), Bytes.EMPTY, new HashSet<>()));

    downloadState.checkCompletion(header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, ROOT_NODE_HASH)).isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldCancelOutstandingTasksWhenFutureIsCancelled() {
    final EthTask<?> outstandingTask1 = mock(EthTask.class);
    final EthTask<?> outstandingTask2 = mock(EthTask.class);
    downloadState.addOutstandingTask(outstandingTask1);
    downloadState.addOutstandingTask(outstandingTask2);

    downloadState.pendingAccountRequests.add(
        SnapDataRequest.createAccountDataRequest(
            Hash.EMPTY_TRIE_HASH,
            Hash.wrap(Bytes32.random()),
            RangeManager.MIN_RANGE,
            RangeManager.MAX_RANGE));
    downloadState.pendingStorageRequests.add(
        SnapDataRequest.createStorageTrieNodeDataRequest(
            Hash.EMPTY_TRIE_HASH, Hash.wrap(Bytes32.random()), Hash.EMPTY_TRIE_HASH, Bytes.EMPTY));
    downloadState.setWorldStateDownloadProcess(worldStateDownloadProcess);

    future.cancel(true);

    verify(outstandingTask1).cancel();
    verify(outstandingTask2).cancel();

    assertThat(downloadState.pendingAccountRequests.isEmpty()).isTrue();
    assertThat(downloadState.pendingStorageRequests.isEmpty()).isTrue();
    verify(worldStateDownloadProcess).abort();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @Test
  public void shouldRestartHealWhenNewPivotBlock() {
    when(snapSyncState.getPivotBlockHeader()).thenReturn(Optional.of(mock(BlockHeader.class)));
    when(snapSyncState.isHealInProgress()).thenReturn(false);
    assertThat(downloadState.pendingTrieNodeRequests.isEmpty()).isTrue();
    // start heal
    downloadState.checkCompletion(header);
    verify(snapSyncState).setHealStatus(true);
    assertThat(downloadState.pendingTrieNodeRequests.isEmpty()).isFalse();
    // add useless requests
    downloadState.pendingTrieNodeRequests.add(
        BytecodeRequest.createAccountTrieNodeDataRequest(Hash.EMPTY, Bytes.EMPTY, new HashSet<>()));
    downloadState.pendingCodeRequests.add(
        BytecodeRequest.createBytecodeRequest(Bytes32.ZERO, Hash.EMPTY, Bytes32.ZERO));
    // reload the heal
    downloadState.reloadHeal();
    verify(snapSyncState).setHealStatus(false);
    assertThat(downloadState.pendingTrieNodeRequests.size()).isEqualTo(1);
    assertThat(downloadState.pendingCodeRequests.isEmpty()).isTrue();
  }
}
