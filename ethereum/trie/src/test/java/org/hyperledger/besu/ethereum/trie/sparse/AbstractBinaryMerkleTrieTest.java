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
package org.hyperledger.besu.ethereum.trie.sparse;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.KeyValueMerkleStorage;
import org.hyperledger.besu.ethereum.trie.MerkleStorage;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractBinaryMerkleTrieTest {
  protected MerkleTrie<Bytes, String> trie;

  @Before
  public void setup() {
    trie = createTrie();
  }

  protected abstract MerkleTrie<Bytes, String> createTrie();

  @Test
  public void emptyTreeReturnsEmpty() {
    assertFalse(trie.get(Bytes.EMPTY).isPresent());
  }

  @Test
  public void emptyTreeHasKnownRootHash() {
    assertThat(trie.getRootHash().toString())
        .isEqualTo("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
  }

  @Test
  public void throwsOnUpdateWithNull() {
    assertThatThrownBy(() -> trie.put(Bytes.EMPTY, (String) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void replaceSingleValue() {
    final Bytes key = Bytes.of(1);
    final String value1 = "value1";
    trie.put(key, value1);
    assertThat(trie.get(key)).isEqualTo(Optional.of(value1));

    final String value2 = "value2";
    trie.put(key, value2);
    assertThat(trie.get(key)).isEqualTo(Optional.of(value2));
  }

  @Test
  public void hashChangesWhenSingleValueReplaced() {
    final Bytes key = Bytes.of(1);
    final String value1 = "value1";
    trie.put(key, value1);
    final Bytes32 hash1 = trie.getRootHash();

    final String value2 = "value2";
    trie.put(key, value2);
    final Bytes32 hash2 = trie.getRootHash();

    assertThat(hash1).isNotEqualTo(hash2);

    trie.put(key, value1);
    assertThat(trie.getRootHash()).isEqualTo(hash1);
  }

  @Test
  public void readPastLeaf() {
    final Bytes key1 = Bytes.of(1);
    trie.put(key1, "value");
    final Bytes key2 = Bytes.of(1, 1);
    assertFalse(trie.get(key2).isPresent());
  }

  @Test
  public void branchValue() {
    final Bytes key1 = Bytes.of(1);
    final Bytes key2 = Bytes.of(0);

    final String value1 = "value1";
    trie.put(key1, value1);

    final String value2 = "value2";
    trie.put(key2, value2);

    assertThat(trie.get(key1)).isEqualTo(Optional.of(value1));
    assertThat(trie.get(key2)).isEqualTo(Optional.of(value2));
  }

  @Test
  public void branchWithValue() {
    final Bytes key1 = Bytes.of(1);
    final Bytes key2 = Bytes.EMPTY;

    final String value1 = "value1";
    trie.put(key1, value1);

    final String value2 = "value2";
    trie.put(key2, value2);

    assertThat(trie.get(key1)).isEqualTo(Optional.of(value1));
    assertThat(trie.get(key2)).isEqualTo(Optional.of(value2));
  }

  @Test
  public void extendAndBranch() {
    final Bytes key1 = Bytes.of(1, 1, 0);
    final Bytes key2 = Bytes.of(1, 0, 0);

    final String value1 = "value1";
    trie.put(key1, value1);

    final String value2 = "value2";
    trie.put(key2, value2);

    assertThat(trie.get(key1)).isEqualTo(Optional.of(value1));
    assertThat(trie.get(key2)).isEqualTo(Optional.of(value2));
    assertFalse(trie.get(Bytes.of(1, 4)).isPresent());
  }

  @Test
  public void replaceBranchChild() {
    final Bytes key1 = Bytes.of(0);
    final Bytes key2 = Bytes.of(1);

    final String value1 = "value1";
    trie.put(key1, value1);
    final String value2 = "value2";
    trie.put(key2, value2);

    assertThat(trie.get(key1)).isEqualTo(Optional.of(value1));
    assertThat(trie.get(key2)).isEqualTo(Optional.of(value2));

    final String value3 = "value3";
    trie.put(key1, value3);

    assertThat(trie.get(key1)).isEqualTo(Optional.of(value3));
    assertThat(trie.get(key2)).isEqualTo(Optional.of(value2));
  }

  @Test
  public void inlineBranchInBranch() {
    final Bytes key1 = Bytes.of(1,1,0);
    final Bytes key2 = Bytes.of(1,0,0);
    final Bytes key3 = Bytes.of(1,1,1);
    final Bytes key4 = Bytes.of(0, 0,0);
    final Bytes key5 = Bytes.of(0, 1,0);

    trie.put(key1, "value1");
    trie.put(key2, "value2");
    trie.put(key3, "value3");
    trie.put(key4, "value4");
    trie.put(key5, "value5");

    trie.remove(key2);
    trie.remove(key3);

    assertThat(trie.get(key1)).isEqualTo(Optional.of("value1"));
    assertFalse(trie.get(key2).isPresent());
    assertFalse(trie.get(key3).isPresent());
    assertThat(trie.get(key4)).isEqualTo(Optional.of("value4"));
    assertThat(trie.get(key5)).isEqualTo(Optional.of("value5"));
  }



  @Test
  public void hashChangesWhenValueChanged() {
    final Bytes key1 = Bytes.of(1,1,0);
    final Bytes key2 = Bytes.of(1,0,0);
    final Bytes key3 = Bytes.of(1,1,1);

    final String value1 = "value1";
    trie.put(key1, value1);
    final Bytes32 hash1 = trie.getRootHash();

    final String value2 = "value2";
    trie.put(key2, value2);
    final Bytes32 hash6 = trie.getRootHash();
    final String value3 = "value3";
    trie.put(key3, value3);
    final Bytes32 hash2 = trie.getRootHash();

    assertThat(hash1).isNotEqualTo(hash2);

    final String value4 = "value4";
    trie.put(key1, value4);
    final Bytes32 hash3 = trie.getRootHash();

    assertThat(hash1).isNotEqualTo(hash3);
    assertThat(hash2).isNotEqualTo(hash3);

    trie.put(key1, value1);
    assertThat(trie.getRootHash()).isEqualTo(hash2);

    trie.remove(key3);

    assertThat(trie.getRootHash()).isEqualTo(hash6);

    trie.remove(key2);
    assertThat(trie.getRootHash()).isEqualTo(hash1);
  }

  @Test
  public void shouldInlineNodesInParentAcrossModifications() {
    // Misuse of StorageNode allowed inlineable trie nodes to end
    // up being stored as a hash in its parent, which this would fail for.
    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    final MerkleStorage merkleStorage = new KeyValueMerkleStorage(keyValueStorage);
    final StoredMerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(merkleStorage::get, b -> b, b -> b);

    // Both of these can be inlined in its parent branch.
    trie.put(Bytes.fromHexString("0x0100"), Bytes.of(1));
    trie.put(Bytes.fromHexString("0x0000"), Bytes.of(2));
    trie.commit(merkleStorage::put);

    final Bytes32 rootHash = trie.getRootHash();
    final StoredMerklePatriciaTrie<Bytes, Bytes> newTrie =
        new StoredMerklePatriciaTrie<>(merkleStorage::get, rootHash, b -> b, b -> b);

    newTrie.put(Bytes.fromHexString("0x0000"), Bytes.of(3));
    newTrie.get(Bytes.fromHexString("0x0101"));
    trie.commit(merkleStorage::put);

    newTrie.get(Bytes.fromHexString("0x0101"));
  }

  @Test
  public void getValueWithProof_emptyTrie() {
    final Bytes key1 = Bytes.of(0x1, 0x1);

    Proof<String> valueWithProof = trie.getValueWithProof(key1);
    assertThat(valueWithProof.getValue()).isEmpty();
    assertThat(valueWithProof.getProofRelatedNodes()).hasSize(0);
  }

  @Test
  public void getValueWithProof_forExistingValues() {
    final Bytes key1 = Bytes.of(1,1,0);
    final Bytes key2 = Bytes.of(1,0,0);
    final Bytes key3 = Bytes.of(1,1,1);

    final String value1 = "value1";
    trie.put(key1, value1);

    final String value2 = "value2";
    trie.put(key2, value2);

    final String value3 = "value3";
    trie.put(key3, value3);

    final Proof<String> valueWithProof = trie.getValueWithProof(key1);
    assertThat(valueWithProof.getProofRelatedNodes()).hasSize(2);
    assertThat(valueWithProof.getValue()).contains(value1);

    final List<Node<Bytes>> nodes =
        TrieNodeDecoder.decodeNodes(null, valueWithProof.getProofRelatedNodes().get(1));

    assertThat(new String(nodes.get(1).getValue().get().toArray(), UTF_8)).isEqualTo(value1);
    assertThat(new String(nodes.get(2).getValue().get().toArray(), UTF_8)).isEqualTo(value2);
  }

  @Test
  public void getValueWithProof_forNonExistentValue() {
    final Bytes key1 = Bytes.of(1,1,0);
    final Bytes key2 = Bytes.of(1,0,0);
    final Bytes key3 = Bytes.of(1,1,1);
    final Bytes key4 = Bytes.of(0, 0,0);

    final String value1 = "value1";
    trie.put(key1, value1);

    final String value2 = "value2";
    trie.put(key2, value2);

    final String value3 = "value3";
    trie.put(key3, value3);

    final Proof<String> valueWithProof = trie.getValueWithProof(key4);
    assertThat(valueWithProof.getValue()).isEmpty();
    assertThat(valueWithProof.getProofRelatedNodes()).hasSize(2);
  }

  @Test
  public void getValueWithProof_singleNodeTrie() {
    final Bytes key1 = Bytes.of(0, 1);
    final String value1 = "1";
    trie.put(key1, value1);

    final Proof<String> valueWithProof = trie.getValueWithProof(key1);
    assertThat(valueWithProof.getValue()).contains(value1);
    assertThat(valueWithProof.getProofRelatedNodes()).hasSize(1);

    final List<Node<Bytes>> nodes =
        TrieNodeDecoder.decodeNodes(null, valueWithProof.getProofRelatedNodes().get(0));

    assertThat(nodes.size()).isEqualTo(1);
    final String nodeValue = new String(nodes.get(0).getValue().get().toArray(), UTF_8);
    assertThat(nodeValue).isEqualTo(value1);
  }
}
