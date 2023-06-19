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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.tasks.Task;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class CompleteTaskStepTest {

  private static final Hash HASH = Hash.hash(Bytes.of(1, 2, 3));

  private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
  private final SnapWorldDownloadState downloadState = mock(SnapWorldDownloadState.class);

  private final BlockHeader blockHeader =
      new BlockHeaderTestFixture().stateRoot(HASH).buildHeader();

  private final CompleteTaskStep completeTaskStep =
      new CompleteTaskStep(snapSyncState, new NoOpMetricsSystem());

  @Before
  public void setup() {
    when(snapSyncState.getPivotBlockHeader()).thenReturn(Optional.of(blockHeader));
  }

  @Test
  public void shouldMarkAccountTrieNodeTaskAsFailedIfItDoesNotHaveData() {
    final StubTask task =
        new StubTask(
            SnapDataRequest.createAccountTrieNodeDataRequest(HASH, Bytes.EMPTY, new HashSet<>()));

    completeTaskStep.markAsCompleteOrFailed(downloadState, task);

    assertThat(task.isCompleted()).isFalse();
    assertThat(task.isFailed()).isTrue();
    verify(downloadState).notifyTaskAvailable();
    verify(downloadState, never()).checkCompletion(blockHeader);
  }

  @Test
  public void shouldMarkAccountTrieNodeTaskCompleteIfItDoesNotHaveDataAndExpired() {
    final StubTask task =
        new StubTask(
            SnapDataRequest.createAccountTrieNodeDataRequest(HASH, Bytes.EMPTY, new HashSet<>()));

    when(snapSyncState.isExpired(any())).thenReturn(true);

    completeTaskStep.markAsCompleteOrFailed(downloadState, task);

    assertThat(task.isCompleted()).isTrue();
    verify(downloadState).notifyTaskAvailable();
    verify(downloadState).checkCompletion(blockHeader);
  }

  @Test
  public void shouldMarkStorageTrieNodeTaskAsFailedIfItDoesNotHaveData() {
    final StubTask task =
        new StubTask(
            SnapDataRequest.createStorageTrieNodeDataRequest(HASH, HASH, HASH, Bytes.EMPTY));

    completeTaskStep.markAsCompleteOrFailed(downloadState, task);

    assertThat(task.isCompleted()).isFalse();
    assertThat(task.isFailed()).isTrue();

    verify(downloadState, never()).checkCompletion(blockHeader);
  }

  @Test
  public void shouldMarkStorageTrieNodeTaskCompleteIfItDoesNotHaveDataAndExpired() {
    final StubTask task =
        new StubTask(
            SnapDataRequest.createStorageTrieNodeDataRequest(HASH, HASH, HASH, Bytes.EMPTY));

    when(snapSyncState.isExpired(any())).thenReturn(true);

    completeTaskStep.markAsCompleteOrFailed(downloadState, task);

    assertThat(task.isCompleted()).isTrue();
    assertThat(task.isFailed()).isFalse();

    verify(downloadState).checkCompletion(blockHeader);
  }

  @Test
  public void shouldMarkSnapsyncTaskCompleteWhenData() {
    final List<Task<SnapDataRequest>> requests = TaskGenerator.createAccountRequest(true);
    requests.stream()
        .map(StubTask.class::cast)
        .forEach(
            snapDataRequestTask -> {
              completeTaskStep.markAsCompleteOrFailed(downloadState, snapDataRequestTask);

              assertThat(snapDataRequestTask.isCompleted()).isTrue();
              assertThat(snapDataRequestTask.isFailed()).isFalse();
            });
    verify(downloadState, times(3)).checkCompletion(blockHeader);
    verify(downloadState, times(3)).notifyTaskAvailable();
  }

  @Test
  public void shouldMarkSnapsyncTaskAsFailedWhenNoData() {
    final List<Task<SnapDataRequest>> requests = TaskGenerator.createAccountRequest(false);
    requests.stream()
        .map(StubTask.class::cast)
        .forEach(
            snapDataRequestTask -> {
              completeTaskStep.markAsCompleteOrFailed(downloadState, snapDataRequestTask);

              assertThat(snapDataRequestTask.isCompleted()).isFalse();
              assertThat(snapDataRequestTask.isFailed()).isTrue();
            });

    verify(downloadState, times(3)).notifyTaskAvailable();
    verify(downloadState, never()).checkCompletion(blockHeader);
  }
}
