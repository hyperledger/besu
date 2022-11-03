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

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.bonsai.BonsaiInMemoryWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.KeyValueMerkleStorage;
import org.hyperledger.besu.ethereum.trie.MerkleStorage;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NodeDeletionProcessorTest {

    private WorldStateStorage worldStateStorage ;
    private KeyValueStorage accountStorage;
    private KeyValueStorage storageStorage;
    private KeyValueStorage trieLogStorage;


    @BeforeEach
    void setUp() {
        accountStorage = Mockito.mock(KeyValueStorage.class);
        final KeyValueStorage codeStorage = Mockito.mock(KeyValueStorage.class);
        storageStorage = Mockito.mock(KeyValueStorage.class);
        final KeyValueStorage trieBranchStorage = Mockito.mock(KeyValueStorage.class);
        trieLogStorage = new InMemoryKeyValueStorage();
        prepareData(trieLogStorage, 3);
        worldStateStorage = new BonsaiInMemoryWorldStateKeyValueStorage(accountStorage,codeStorage, storageStorage,trieBranchStorage, trieLogStorage,Optional.empty());
    }

    private void prepareData(final KeyValueStorage trieLogStorage, int accounts) {
        final MerkleStorage merkleStorage = new KeyValueMerkleStorage(trieLogStorage);
        final StoredMerklePatriciaTrie<Bytes, Bytes> trie =
                new StoredMerklePatriciaTrie<>(merkleStorage::get, Function.identity(), Function.identity());
        for (int i = 0; i < accounts; i++) {
            final Bytes accountBytes = encodeAccount(i);
            final Hash accountHash= Hash.hash(accountBytes);
            trie.put(accountHash, accountBytes);
            trie.commit(merkleStorage::put);

            final Bytes32 rootHash = trie.getRootHash();
            final StoredMerklePatriciaTrie<Bytes, Bytes> newTrie =
                    new StoredMerklePatriciaTrie<>(merkleStorage::get, rootHash, b -> b, b -> b);

            return;

        }

    }

    private Bytes encodeAccount(int seed){
        StateTrieAccountValue value = new StateTrieAccountValue(seed, Wei.ZERO,Hash.hash(Bytes.wrap(("storage"+seed).getBytes())),Hash.hash(Bytes.wrap(("code"+seed).getBytes())));
        return RLP.encode(value::writeTo);
    }
    private void insertStorageNode(){
    }

    @Test
    void shouldDoNothingWhenDeletingNodesWithoutPrevious() {
        NodeDeletionProcessor processor = new NodeDeletionProcessor(worldStateStorage, worldStateStorage.updater());

//        final NodeDeletionProcessor.BonsaiStorageInnerNode bonsaiStorageInnerNode = new NodeDeletionProcessor.BonsaiStorageInnerNode(Hash.hash(Bytes.wrap("NonExistingNode".getBytes())),null,null,null);
//        processor.deletePotentialOldChildren(bonsaiStorageInnerNode, bytes -> processor.retrieveStoredInnerStorageNode(bonsaiStorageInnerNode.getAccountHash(), bytes));
    }
}