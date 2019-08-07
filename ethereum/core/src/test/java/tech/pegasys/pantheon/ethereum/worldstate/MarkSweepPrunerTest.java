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
package tech.pegasys.pantheon.ethereum.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class MarkSweepPrunerTest {

  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void shouldMarkAllNodesInCurrentWorldState() {

    // Setup "remote" state
    final InMemoryKeyValueStorage markStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage stateStorage = new InMemoryKeyValueStorage();
    final WorldStateStorage worldStateStorage = new WorldStateKeyValueStorage(stateStorage);
    final WorldStateArchive worldStateArchive =
        new WorldStateArchive(
            worldStateStorage,
            new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
    final MutableWorldState worldState = worldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    gen.createRandomContractAccountsWithNonEmptyStorage(worldState, 20);
    final Hash stateRoot = worldState.rootHash();

    final MarkSweepPruner pruner =
        new MarkSweepPruner(worldStateStorage, markStorage, metricsSystem);
    pruner.mark(stateRoot);
    pruner.flushPendingMarks();

    final Set<BytesValue> keysToKeep = new HashSet<>(stateStorage.keySet());
    assertThat(markStorage.keySet()).containsExactlyInAnyOrderElementsOf(keysToKeep);

    // Generate some more nodes from a world state we didn't mark
    gen.createRandomContractAccountsWithNonEmptyStorage(worldStateArchive.getMutable(), 10);
    assertThat(stateStorage.keySet()).hasSizeGreaterThan(keysToKeep.size());

    // All those new nodes should be removed when we sweep
    pruner.sweep();
    assertThat(stateStorage.keySet()).containsExactlyInAnyOrderElementsOf(keysToKeep);
    assertThat(markStorage.keySet()).isEmpty();
  }
}
