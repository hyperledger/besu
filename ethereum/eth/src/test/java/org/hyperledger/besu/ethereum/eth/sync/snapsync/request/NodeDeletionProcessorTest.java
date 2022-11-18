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
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class NodeDeletionProcessorTest {

  private static final InMemoryKeyValueStorageProvider inMemory =
      new InMemoryKeyValueStorageProvider();
  private static final BonsaiWorldStateKeyValueStorage worldStateStorage =
      new BonsaiWorldStateKeyValueStorage(inMemory);

  @Test
  public void shouldCleanDatabaseWhenLeafNodeIsReached() {

    // generate a trie
    final List<Hash> accounts =
        List.of(
            Hash.fromHexString(
                "0xb5c55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6"),
            Hash.fromHexString(
                "0xb6c55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6"),
            Hash.fromHexString(
                "0xc5c55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6"));

    final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorage, accounts);

    // get the leafnode of the accounts
    final Map<Bytes32, Node<Bytes>> nodes = new HashMap<>();
    accountStateTrie.visitLeafs(
        (keyHash, node) -> {
          nodes.put(keyHash, node);
          return TrieIterator.State.CONTINUE;
        });

    final KeyValueStorage trieBranchStorage =
        inMemory.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    final KeyValueStorage accountInfoState =
        inMemory.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);

    // put deprecated data in the db
    final Bytes deprecatedTrieNode = Bytes.fromHexString("0b0507");
    final Bytes deprecatedAccount =
        Hash.fromHexString("0xb5d55812f1e0df8418927ceafec7e0b47443347d04753135c35ee3707d6b97d6");
    final KeyValueStorageTransaction trieBranchStorageTransaction =
        trieBranchStorage.startTransaction();
    trieBranchStorageTransaction.put(
        deprecatedTrieNode.toArrayUnsafe(), Bytes32.random().toArrayUnsafe());
    trieBranchStorageTransaction.commit();
    final KeyValueStorageTransaction accountTransaction = accountInfoState.startTransaction();
    accountTransaction.put(deprecatedAccount.toArrayUnsafe(), Bytes32.random().toArrayUnsafe());
    accountTransaction.commit();

    final Node<Bytes> firstAccountNode = nodes.get(accounts.get(0));

    // check if we have the deprecated data before the cleaning
    List<Bytes> byPrefixBeforeCleaning = trieBranchStorage.getByPrefix(Bytes.fromHexString("0b05"));
    Assertions.assertThat(byPrefixBeforeCleaning)
        .containsExactlyInAnyOrder(
            firstAccountNode.getLocation().orElseThrow(), deprecatedTrieNode);
    byPrefixBeforeCleaning = accountInfoState.getByPrefix(Bytes.fromHexString("0b05"));
    Assertions.assertThat(byPrefixBeforeCleaning)
        .containsExactlyInAnyOrder(accounts.get(0), deprecatedAccount);

    final AccountTrieNodeDataRequest accountTrieNodeDataRequest =
        new AccountTrieNodeDataRequest(
            Hash.wrap(firstAccountNode.getHash()),
            Hash.wrap(accountStateTrie.getRootHash()),
            firstAccountNode.getLocation().orElseThrow(),
            new HashSet<>());
    accountTrieNodeDataRequest.setData(firstAccountNode.getRlp());
    NodeDeletionProcessor.deletePotentialOldAccountEntries(
        worldStateStorage, accountTrieNodeDataRequest);

    List<Bytes> byPrefixAfterCleaning = trieBranchStorage.getByPrefix(Bytes.fromHexString("0b05"));
    Assertions.assertThat(byPrefixAfterCleaning)
        .containsExactly(firstAccountNode.getLocation().orElseThrow());
    byPrefixAfterCleaning = accountInfoState.getByPrefix(Bytes.fromHexString("0b05"));
    Assertions.assertThat(byPrefixAfterCleaning).containsExactly(accounts.get(0));
  }
}
