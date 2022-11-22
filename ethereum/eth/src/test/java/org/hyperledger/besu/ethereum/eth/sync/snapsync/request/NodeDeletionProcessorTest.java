/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.util.RangeManager;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class NodeDeletionProcessorTest {

  private static final InMemoryKeyValueStorageProvider inMemory =
      new InMemoryKeyValueStorageProvider();
  private static final BonsaiWorldStateKeyValueStorage worldStateStorage =
      new BonsaiWorldStateKeyValueStorage(inMemory);

  final List<Hash> ACCOUNTS =
      List.of(
          Hash.fromHexString("0xb5c55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6"),
          Hash.fromHexString("0xd6c55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6"),
          Hash.fromHexString("0xc5c55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6"));

  final Map<Bytes32, Node<Bytes>> leafs = new HashMap<>();

  MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie;
  KeyValueStorage trieBranchStorage, accountInfoState, storageState;

  @Before
  public void setup() {

    // generate a trie
    accountStateTrie = TrieGenerator.generateTrie(worldStateStorage, ACCOUNTS);

    // get the leaf of the accounts
    accountStateTrie.visitLeafs(
        (keyHash, node) -> {
          leafs.put(keyHash, node);
          return TrieIterator.State.CONTINUE;
        });

    trieBranchStorage =
        inMemory.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    accountInfoState =
        inMemory.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    storageState =
        inMemory.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
  }

  @Test
  public void shouldCleanDatabaseWhenLeafNodeIsReached() {

    // put deprecated data in the db
    final Bytes deprecatedTrieNode = Bytes.fromHexString("0b0507");
    final Bytes deprecatedAccount =
        Hash.fromHexString("0xb5c65812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6");
    final KeyValueStorageTransaction trieBranchStorageTransaction =
        trieBranchStorage.startTransaction();
    trieBranchStorageTransaction.put(
        deprecatedTrieNode.toArrayUnsafe(), Bytes32.random().toArrayUnsafe());
    trieBranchStorageTransaction.commit();
    final KeyValueStorageTransaction accountTransaction = accountInfoState.startTransaction();
    accountTransaction.put(deprecatedAccount.toArrayUnsafe(), Bytes32.random().toArrayUnsafe());
    accountTransaction.commit();

    final Node<Bytes> firstAccountNode = leafs.get(ACCOUNTS.get(0));

    final Bytes prefix = Bytes.fromHexString("0b");
    final Pair<Bytes, Bytes> accountRange =
        RangeManager.generateRangeFromLocation(Bytes.EMPTY, prefix);

    // check if we have the deprecated data before the cleaning in the database
    Assertions.assertThat(trieBranchStorage.getByPrefix(prefix))
        .containsExactlyInAnyOrder(
            firstAccountNode.getLocation().orElseThrow(), deprecatedTrieNode);
    Assertions.assertThat(
            accountInfoState.getInRange(accountRange.getFirst(), accountRange.getSecond()).keySet())
        .containsExactlyInAnyOrder(ACCOUNTS.get(0), deprecatedAccount);

    // trigger worldstate pruning
    final AccountTrieNodeDataRequest accountTrieNodeDataRequest =
        new AccountTrieNodeDataRequest(
            Hash.wrap(firstAccountNode.getHash()),
            Hash.wrap(accountStateTrie.getRootHash()),
            firstAccountNode.getLocation().orElseThrow(),
            new HashSet<>());
    accountTrieNodeDataRequest.setData(firstAccountNode.getRlp());
    NodeDeletionProcessor.deletePotentialOldAccountEntries(
        worldStateStorage, accountTrieNodeDataRequest);

    // update account as snapsync
    accountTrieNodeDataRequest.getRequestsFromTrieNodeValue(
        worldStateStorage,
        firstAccountNode.getLocation().orElseThrow(),
        firstAccountNode.getPath(),
        firstAccountNode.getValue().orElseThrow());
    BonsaiWorldStateKeyValueStorage.BonsaiUpdater updater = worldStateStorage.updater();
    accountTrieNodeDataRequest.doPersist(
        worldStateStorage,
        updater,
        Mockito.mock(SnapWorldDownloadState.class),
        Mockito.mock(SnapSyncState.class));
    updater.commit();
    // check if deprecated node are removed
    Assertions.assertThat(trieBranchStorage.getByPrefix(prefix))
        .containsExactlyInAnyOrder(firstAccountNode.getLocation().orElseThrow());
    Assertions.assertThat(
            accountInfoState.getInRange(accountRange.getFirst(), accountRange.getSecond()).keySet())
        .containsExactlyInAnyOrder(ACCOUNTS.get(0));
    Assertions.assertThat(
            storageState.getInRange(accountRange.getFirst(), accountRange.getSecond()).keySet())
        .containsExactlyInAnyOrder(ACCOUNTS.get(0));

    Assertions.assertThat(accountStateTrie.get(ACCOUNTS.get(0))).isPresent();
  }

  @Test
  public void shouldCleanDatabaseWhenExtensionNodeIsReached() {

    // put deprecated data in the db
    final Bytes deprecatedTrieNode = Bytes.fromHexString("0b0507");
    final Bytes deprecatedAccount =
        Hash.fromHexString("0xb5c65812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6");
    final KeyValueStorageTransaction trieBranchStorageTransaction =
        trieBranchStorage.startTransaction();
    trieBranchStorageTransaction.put(
        deprecatedTrieNode.toArrayUnsafe(), Bytes32.random().toArrayUnsafe());
    trieBranchStorageTransaction.commit();
    final KeyValueStorageTransaction accountTransaction = accountInfoState.startTransaction();
    accountTransaction.put(deprecatedAccount.toArrayUnsafe(), Bytes32.random().toArrayUnsafe());
    accountTransaction.commit();

    final Node<Bytes> firstAccountNode = leafs.get(ACCOUNTS.get(0));

    final Bytes prefix = Bytes.fromHexString("0b");
    final Pair<Bytes, Bytes> accountRange =
        RangeManager.generateRangeFromLocation(Bytes.EMPTY, prefix);

    // check if we have the deprecated data before the cleaning in the database
    Assertions.assertThat(trieBranchStorage.getByPrefix(prefix))
        .containsExactlyInAnyOrder(
            firstAccountNode.getLocation().orElseThrow(), deprecatedTrieNode);
    Assertions.assertThat(
            accountInfoState.getInRange(accountRange.getFirst(), accountRange.getSecond()).keySet())
        .containsExactlyInAnyOrder(ACCOUNTS.get(0), deprecatedAccount);

    // trigger worldstate pruning
    final AccountTrieNodeDataRequest accountTrieNodeDataRequest =
        new AccountTrieNodeDataRequest(
            Hash.wrap(firstAccountNode.getHash()),
            Hash.wrap(accountStateTrie.getRootHash()),
            firstAccountNode.getLocation().orElseThrow(),
            new HashSet<>());
    accountTrieNodeDataRequest.setData(firstAccountNode.getRlp());
    NodeDeletionProcessor.deletePotentialOldAccountEntries(
        worldStateStorage, accountTrieNodeDataRequest);

    // update account as snapsync
    accountTrieNodeDataRequest.getRequestsFromTrieNodeValue(
        worldStateStorage,
        firstAccountNode.getLocation().orElseThrow(),
        firstAccountNode.getPath(),
        firstAccountNode.getValue().orElseThrow());
    BonsaiWorldStateKeyValueStorage.BonsaiUpdater updater = worldStateStorage.updater();
    accountTrieNodeDataRequest.doPersist(
        worldStateStorage,
        updater,
        Mockito.mock(SnapWorldDownloadState.class),
        Mockito.mock(SnapSyncState.class));
    updater.commit();
    // check if deprecated node are removed
    Assertions.assertThat(trieBranchStorage.getByPrefix(prefix))
        .containsExactlyInAnyOrder(firstAccountNode.getLocation().orElseThrow());
    Assertions.assertThat(
            accountInfoState.getInRange(accountRange.getFirst(), accountRange.getSecond()).keySet())
        .containsExactlyInAnyOrder(ACCOUNTS.get(0));
    Assertions.assertThat(
            storageState.getInRange(accountRange.getFirst(), accountRange.getSecond()).keySet())
        .containsExactlyInAnyOrder(ACCOUNTS.get(0));

    Assertions.assertThat(accountStateTrie.get(ACCOUNTS.get(0))).isPresent();
  }
}
