/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class PersistDataStepTest {

  private final WorldStateStorage worldStateStorage =
      new InMemoryKeyValueStorageProvider().createWorldStateStorage(DataStorageFormat.BONSAI);
  private final SnapSyncProcessState snapSyncState = mock(SnapSyncProcessState.class);
  private final SnapWorldDownloadState downloadState = mock(SnapWorldDownloadState.class);
  private final SnapSyncConfiguration snapSyncConfiguration = mock(SnapSyncConfiguration.class);

  private final PersistDataStep persistDataStep =
      new PersistDataStep(snapSyncState, worldStateStorage, downloadState, snapSyncConfiguration);

  @BeforeEach
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

  @Test
  public void shouldHealWhenIncompleteStorageDataWithoutProof() {
    // in normal operation this case would likely be triggered by a repivot.
    // for simplicity, we limit the storage slots returned and return the partial
    // range without a proof, implying the slots should be the complete storage range

    final List<Task<SnapDataRequest>> tasks =
        TaskGenerator.createAccountRequest(1, 1, 1, true, downloadState, false);
    final List<Task<SnapDataRequest>> result = persistDataStep.persist(tasks);

    assertThat(result).isSameAs(tasks);
    // assert incomplete storage data without proof causes the account to be added to the heal list
    verify(downloadState, times(1)).addAccountToHealingList(any(Bytes.class));
    verify(downloadState, times(1)).enqueueRequest(any(AccountRangeDataRequest.class));
    assertThat(worldStateStorage.getNodeData(Bytes.EMPTY, tasks.get(0).getData().getRootHash()))
        .isEmpty();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void shouldEnqueueChildRequestWhenIncompleteStorageDataWithProof() {

    final List<Task<SnapDataRequest>> tasks =
        TaskGenerator.createAccountRequest(1, 1, 1, true, downloadState, true);
    final List<Task<SnapDataRequest>> result = persistDataStep.persist(tasks);

    assertThat(result).isSameAs(tasks);
    // assert the current heal behavior of account marked, without new account request
    verify(downloadState, times(1)).addAccountToHealingList(any(Bytes.class));
    verify(downloadState, times(0)).enqueueRequest(any(AccountRangeDataRequest.class));

    // assert that the incomplete storage data with proof enqueues a child storage request.
    // verification is messy due to the stream generic parameter
    ArgumentCaptor<Stream> rawArgumentCaptor = ArgumentCaptor.forClass(Stream.class);
    verify(downloadState, atLeast(1)).enqueueRequests(rawArgumentCaptor.capture());
    var enqueuedChildRequests =
        rawArgumentCaptor.getAllValues().stream()
            .map(stream -> stream.collect(Collectors.toList()))
            .map(List.class::cast)
            .filter(list -> !list.isEmpty())
            .filter(list -> list.get(0) instanceof StorageRangeDataRequest)
            .findFirst();
    assertThat(enqueuedChildRequests).isPresent();
    assertThat(enqueuedChildRequests.get()).isNotEmpty();

    // assert the parent storage request is not persisted, child requests should complete first
    assertThat(worldStateStorage.getNodeData(Bytes.EMPTY, tasks.get(0).getData().getRootHash()))
        .isEmpty();
  }

  @Test
  public void shouldHealWhenProofInvalid() {}

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
