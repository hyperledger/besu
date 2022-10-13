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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PersistDataStepTest {

  private final WorldStateStorage worldStateStorage =
      new InMemoryKeyValueStorageProvider().createWorldStateStorage(DataStorageFormat.FOREST);
  private final SnapSyncState snapSyncState = mock(SnapSyncState.class);
  private final SnapWorldDownloadState downloadState = mock(SnapWorldDownloadState.class);

  private final PersistDataStep persistDataStep =
      new PersistDataStep(snapSyncState, worldStateStorage, downloadState);

  @Before
  public void setUp() {
    when(downloadState.getMetricsManager()).thenReturn(mock(SnapsyncMetricsManager.class));
  }

  @Test
  public void shouldPersistDataWhenPresent() {
    final List<Task<SnapDataRequest>> tasks = TaskGenerator.createAccountRequest(true);
    final List<Task<SnapDataRequest>> result = persistDataStep.persist(tasks);

    assertThat(result).isSameAs(tasks);

    assertDataPersisted(tasks);
  }

  @Test
  public void shouldSkipPersistDataWhenNoData() {
    final List<Task<SnapDataRequest>> tasks = TaskGenerator.createAccountRequest(false);
    final List<Task<SnapDataRequest>> result = persistDataStep.persist(tasks);

    assertThat(result).isSameAs(tasks);
    assertThat(worldStateStorage.getNodeData(Bytes.EMPTY, tasks.get(0).getData().getRootHash()))
        .isEmpty();
  }

  private void assertDataPersisted(final List<Task<SnapDataRequest>> tasks) {
    tasks.forEach(
        task -> {
          if (task.getData() instanceof AccountRangeDataRequest) {
            final AccountRangeDataRequest data = (AccountRangeDataRequest) task.getData();
            StoredMerklePatriciaTrie<Bytes, Bytes> trie =
                new StoredMerklePatriciaTrie<>(
                    worldStateStorage::getAccountStateTrieNode, data.getRootHash(), b -> b, b -> b);
            data.getAccounts().forEach((key, value) -> assertThat(trie.get(key)).isPresent());
          } else if (task.getData() instanceof StorageRangeDataRequest) {
            final StorageRangeDataRequest data = (StorageRangeDataRequest) task.getData();
            final StoredMerklePatriciaTrie<Bytes, Bytes> trie =
                new StoredMerklePatriciaTrie<>(
                    (location, hash) ->
                        worldStateStorage.getAccountStorageTrieNode(
                            Hash.wrap(data.getAccountHash()), location, hash),
                    data.getStorageRoot(),
                    b -> b,
                    b -> b);
            data.getSlots().forEach((key, value) -> assertThat(trie.get(key)).isPresent());
          } else if (task.getData() instanceof BytecodeRequest) {
            final BytecodeRequest data = (BytecodeRequest) task.getData();
            assertThat(
                    worldStateStorage.getCode(data.getCodeHash(), Hash.wrap(data.getAccountHash())))
                .isPresent();
          } else {
            fail("not expected message");
          }
        });
  }
}
