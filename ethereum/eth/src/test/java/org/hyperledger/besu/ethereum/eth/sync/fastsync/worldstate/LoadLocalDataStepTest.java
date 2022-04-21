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
import static org.hyperledger.besu.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipe;
import org.hyperledger.besu.services.tasks.Task;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.mockito.Mockito;

public class LoadLocalDataStepTest {

  private static final Bytes DATA = Bytes.of(1, 2, 3);
  private static final Hash HASH = Hash.hash(DATA);
  private final WorldStateStorage worldStateStorage = mock(WorldStateStorage.class);
  private final WorldStateStorage.Updater updater = mock(WorldStateStorage.Updater.class);

  private final CodeNodeDataRequest request =
      NodeDataRequest.createCodeRequest(HASH, Optional.empty());
  private final Task<NodeDataRequest> task = new StubTask(request);

  private final Pipe<Task<NodeDataRequest>> completedTasks =
      new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER);
  private final LoadLocalDataStep loadLocalDataStep =
      new LoadLocalDataStep(worldStateStorage, new NoOpMetricsSystem());

  @Test
  public void shouldReturnStreamWithUnchangedTaskWhenDataNotPresent() {
    final Stream<Task<NodeDataRequest>> output =
        loadLocalDataStep.loadLocalData(task, completedTasks);

    assertThat(completedTasks.poll()).isNull();
    assertThat(output).containsExactly(task);
  }

  @Test
  public void shouldReturnEmptyStreamAndSendTaskToCompletedPipeWhenDataIsPresent() {
    when(worldStateStorage.getCode(HASH, Hash.EMPTY)).thenReturn(Optional.of(DATA));
    when(worldStateStorage.updater()).thenReturn(updater);

    final Stream<Task<NodeDataRequest>> output =
        loadLocalDataStep.loadLocalData(task, completedTasks);

    assertThat(completedTasks.poll()).isSameAs(task);
    assertThat(request.getData()).isEqualTo(DATA);
    assertThat(output).isEmpty();

    verify(updater).commit();
    Mockito.reset(updater);

    // Should not require persisting.
    request.persist(updater);
    verifyNoInteractions(updater);
  }
}
