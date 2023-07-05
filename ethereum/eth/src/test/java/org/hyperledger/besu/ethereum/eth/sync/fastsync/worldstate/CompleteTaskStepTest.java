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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class CompleteTaskStepTest {

  private static final Hash ROOT_HASH = Hash.hash(Bytes.of(1, 2, 3));
  private final FastWorldDownloadState downloadState = mock(FastWorldDownloadState.class);
  private final BlockHeader blockHeader =
      new BlockHeaderTestFixture().stateRoot(ROOT_HASH).buildHeader();

  private final CompleteTaskStep completeTaskStep =
      new CompleteTaskStep(new NoOpMetricsSystem(), () -> 3);

  @Test
  public void shouldMarkTaskAsFailedIfItDoesNotHaveData() {
    final StubTask task =
        new StubTask(NodeDataRequest.createAccountDataRequest(ROOT_HASH, Optional.empty()));

    completeTaskStep.markAsCompleteOrFailed(blockHeader, downloadState, task);

    assertThat(task.isCompleted()).isFalse();
    assertThat(task.isFailed()).isTrue();
    verify(downloadState).notifyTaskAvailable();
    verify(downloadState, never()).checkCompletion(blockHeader);
  }

  @Test
  public void shouldMarkCompleteWhenTaskHasData() {
    // Use an arbitrary but actually valid trie node to get children from.
    final StubTask task = validTask();
    completeTaskStep.markAsCompleteOrFailed(blockHeader, downloadState, task);

    assertThat(task.isCompleted()).isTrue();
    assertThat(task.isFailed()).isFalse();

    verify(downloadState).checkCompletion(blockHeader);
  }

  private StubTask validTask() {
    final Hash hash =
        Hash.fromHexString("0x601a7b0d0267209790cf4c4d9e0cab11b26c537e2ade006412f48b070010e847");
    final Bytes data =
        Bytes.fromHexString(
            "0xf85180808080a05ac6993e3fbca0bfbd30173396dd5c2412657fae0bad92e401d17b2aa9a3698f80808080a012f96a0812be538c302416dc6e8df19ce18f1cc7b06a3c7a16831d766c87a9b580808080808080");
    final StubTask task =
        new StubTask(NodeDataRequest.createAccountDataRequest(hash, Optional.empty()));
    task.getData().setData(data);
    return task;
  }
}
