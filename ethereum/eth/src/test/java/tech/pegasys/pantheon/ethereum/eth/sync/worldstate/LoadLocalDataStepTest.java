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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.pipeline.Pipe;
import tech.pegasys.pantheon.services.tasks.Task;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.Test;

public class LoadLocalDataStepTest {

  private static final BytesValue DATA = BytesValue.of(1, 2, 3);
  private static final Hash HASH = Hash.hash(DATA);
  private final WorldStateStorage worldStateStorage = mock(WorldStateStorage.class);
  private final WorldStateStorage.Updater updater = mock(WorldStateStorage.Updater.class);

  private final CodeNodeDataRequest request = NodeDataRequest.createCodeRequest(HASH);
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
    when(worldStateStorage.getCode(HASH)).thenReturn(Optional.of(DATA));

    final Stream<Task<NodeDataRequest>> output =
        loadLocalDataStep.loadLocalData(task, completedTasks);

    assertThat(completedTasks.poll()).isSameAs(task);
    assertThat(request.getData()).isEqualTo(DATA);
    assertThat(output).isEmpty();

    // Should not require persisting.
    request.persist(updater);
    verifyZeroInteractions(updater);
  }
}
