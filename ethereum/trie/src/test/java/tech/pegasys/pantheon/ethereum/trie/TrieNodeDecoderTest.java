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
package tech.pegasys.pantheon.ethereum.trie;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;

public class TrieNodeDecoderTest {

  @Test
  public void decodeNodes() {
    final InMemoryKeyValueStorage storage = new InMemoryKeyValueStorage();

    // Build a small trie
    MerklePatriciaTrie<BytesValue, BytesValue> trie =
        new StoredMerklePatriciaTrie<>(
            new BytesToByteNodeLoader(storage), Function.identity(), Function.identity());
    trie.put(BytesValue.fromHexString("0x100000"), BytesValue.of(1));
    trie.put(BytesValue.fromHexString("0x200000"), BytesValue.of(2));
    trie.put(BytesValue.fromHexString("0x300000"), BytesValue.of(3));

    trie.put(BytesValue.fromHexString("0x110000"), BytesValue.of(10));
    trie.put(BytesValue.fromHexString("0x210000"), BytesValue.of(20));
    // Create large leaf node that will not be inlined
    trie.put(
        BytesValue.fromHexString("0x310000"),
        BytesValue.fromHexString("0x11223344556677889900112233445566778899"));

    // Save nodes to storage
    final KeyValueStorageTransaction tx = storage.startTransaction();
    trie.commit((key, value) -> tx.put(key.getArrayUnsafe(), value.getArrayUnsafe()));
    tx.commit();

    // Get and flatten root node
    final BytesValue rootNodeRlp =
        BytesValue.wrap(storage.get(trie.getRootHash().getArrayUnsafe()).get());
    final List<Node<BytesValue>> nodes = TrieNodeDecoder.decodeNodes(rootNodeRlp);
    // The full trie hold 10 nodes, the branch node starting with 0x3... holding 2 values will be a
    // hash
    // referenced node and so its 2 child nodes will be missing
    assertThat(nodes.size()).isEqualTo(8);

    // Collect and check values
    List<BytesValue> actualValues =
        nodes.stream()
            .filter(n -> !n.isReferencedByHash())
            .map(Node::getValue)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    assertThat(actualValues)
        .containsExactlyInAnyOrder(
            BytesValue.of(1), BytesValue.of(10), BytesValue.of(2), BytesValue.of(20));
  }

  @Test
  public void breadthFirstDecode_smallTrie() {
    final InMemoryKeyValueStorage storage = new InMemoryKeyValueStorage();

    // Build a small trie
    MerklePatriciaTrie<BytesValue, BytesValue> trie =
        new StoredMerklePatriciaTrie<>(
            new BytesToByteNodeLoader(storage), Function.identity(), Function.identity());
    trie.put(BytesValue.fromHexString("0x100000"), BytesValue.of(1));
    trie.put(BytesValue.fromHexString("0x200000"), BytesValue.of(2));
    trie.put(BytesValue.fromHexString("0x300000"), BytesValue.of(3));

    trie.put(BytesValue.fromHexString("0x110000"), BytesValue.of(10));
    trie.put(BytesValue.fromHexString("0x210000"), BytesValue.of(20));
    trie.put(BytesValue.fromHexString("0x310000"), BytesValue.of(30));

    // Save nodes to storage
    final KeyValueStorageTransaction tx = storage.startTransaction();
    trie.commit((key, value) -> tx.put(key.getArrayUnsafe(), value.getArrayUnsafe()));
    tx.commit();

    // First layer should just be the root node
    final List<Node<BytesValue>> depth0Nodes =
        TrieNodeDecoder.breadthFirstDecoder(
                new BytesToByteNodeLoader(storage), trie.getRootHash(), 0)
            .collect(Collectors.toList());

    assertThat(depth0Nodes.size()).isEqualTo(1);
    final Node<BytesValue> rootNode = depth0Nodes.get(0);
    assertThat(rootNode.getHash()).isEqualTo(trie.getRootHash());

    // Decode first 2 levels
    final List<Node<BytesValue>> depth0And1Nodes =
        (TrieNodeDecoder.breadthFirstDecoder(
                new BytesToByteNodeLoader(storage), trie.getRootHash(), 1)
            .collect(Collectors.toList()));
    final int secondLevelNodeCount = 3;
    final int expectedNodeCount = secondLevelNodeCount + 1;
    assertThat(depth0And1Nodes.size()).isEqualTo(expectedNodeCount);
    // First node should be root node
    assertThat(depth0And1Nodes.get(0).getHash()).isEqualTo(rootNode.getHash());
    // Subsequent nodes should be children of root node
    List<Bytes32> expectedNodesHashes =
        rootNode.getChildren().stream()
            .filter(n -> !Objects.equals(n, NullNode.instance()))
            .map(Node::getHash)
            .collect(Collectors.toList());
    List<Bytes32> actualNodeHashes =
        depth0And1Nodes.subList(1, expectedNodeCount).stream()
            .map(Node::getHash)
            .collect(Collectors.toList());
    assertThat(actualNodeHashes).isEqualTo(expectedNodesHashes);

    // Decode full trie
    final List<Node<BytesValue>> allNodes =
        TrieNodeDecoder.breadthFirstDecoder(new BytesToByteNodeLoader(storage), trie.getRootHash())
            .collect(Collectors.toList());
    assertThat(allNodes.size()).isEqualTo(10);
    // Collect and check values
    List<BytesValue> actualValues =
        allNodes.stream()
            .map(Node::getValue)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    assertThat(actualValues)
        .containsExactly(
            BytesValue.of(1),
            BytesValue.of(10),
            BytesValue.of(2),
            BytesValue.of(20),
            BytesValue.of(3),
            BytesValue.of(30));
  }

  @Test
  public void breadthFirstDecode_partialTrie() {
    final InMemoryKeyValueStorage fullStorage = new InMemoryKeyValueStorage();
    final InMemoryKeyValueStorage partialStorage = new InMemoryKeyValueStorage();

    // Build a small trie
    MerklePatriciaTrie<BytesValue, BytesValue> trie =
        new StoredMerklePatriciaTrie<>(
            new BytesToByteNodeLoader(fullStorage), Function.identity(), Function.identity());
    final Random random = new Random(1);
    for (int i = 0; i < 30; i++) {
      byte[] key = new byte[4];
      byte[] val = new byte[4];
      random.nextBytes(key);
      random.nextBytes(val);
      trie.put(BytesValue.wrap(key), BytesValue.wrap(val));
    }
    final KeyValueStorageTransaction tx = fullStorage.startTransaction();
    trie.commit((key, value) -> tx.put(key.getArrayUnsafe(), value.getArrayUnsafe()));
    tx.commit();

    // Get root node
    Node<BytesValue> rootNode =
        TrieNodeDecoder.breadthFirstDecoder(
                new BytesToByteNodeLoader(fullStorage), trie.getRootHash())
            .findFirst()
            .get();

    // Decode partially available trie
    final KeyValueStorageTransaction partialTx = partialStorage.startTransaction();
    partialTx.put(trie.getRootHash().getArrayUnsafe(), rootNode.getRlp().getArrayUnsafe());
    partialTx.commit();
    final List<Node<BytesValue>> allDecodableNodes =
        TrieNodeDecoder.breadthFirstDecoder(
                new BytesToByteNodeLoader(partialStorage), trie.getRootHash())
            .collect(Collectors.toList());
    assertThat(allDecodableNodes.size()).isGreaterThanOrEqualTo(1);
    assertThat(allDecodableNodes.get(0).getHash()).isEqualTo(rootNode.getHash());
  }

  @Test
  public void breadthFirstDecode_emptyTrie() {
    List<Node<BytesValue>> result =
        TrieNodeDecoder.breadthFirstDecoder(
                (h) -> Optional.empty(), MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)
            .collect(Collectors.toList());
    assertThat(result.size()).isEqualTo(0);
  }

  @Test
  public void breadthFirstDecode_singleNodeTrie() {
    final InMemoryKeyValueStorage storage = new InMemoryKeyValueStorage();

    MerklePatriciaTrie<BytesValue, BytesValue> trie =
        new StoredMerklePatriciaTrie<>(
            new BytesToByteNodeLoader(storage), Function.identity(), Function.identity());
    trie.put(BytesValue.fromHexString("0x100000"), BytesValue.of(1));

    // Save nodes to storage
    final KeyValueStorageTransaction tx = storage.startTransaction();
    trie.commit((key, value) -> tx.put(key.getArrayUnsafe(), value.getArrayUnsafe()));
    tx.commit();

    List<Node<BytesValue>> result =
        TrieNodeDecoder.breadthFirstDecoder(new BytesToByteNodeLoader(storage), trie.getRootHash())
            .collect(Collectors.toList());
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getValue()).contains(BytesValue.of(1));
    BytesValue actualPath = CompactEncoding.pathToBytes(result.get(0).getPath());
    assertThat(actualPath).isEqualTo(BytesValue.fromHexString("0x100000"));
  }

  @Test
  public void breadthFirstDecode_unknownTrie() {

    Bytes32 randomRootHash = Bytes32.fromHexStringLenient("0x12");
    List<Node<BytesValue>> result =
        TrieNodeDecoder.breadthFirstDecoder((h) -> Optional.empty(), randomRootHash)
            .collect(Collectors.toList());
    assertThat(result.size()).isEqualTo(0);
  }

  private static class BytesToByteNodeLoader implements NodeLoader {

    private final KeyValueStorage storage;

    private BytesToByteNodeLoader(final KeyValueStorage storage) {
      this.storage = storage;
    }

    @Override
    public Optional<BytesValue> getNode(final Bytes32 hash) {
      final byte[] value = storage.get(hash.getArrayUnsafe()).orElse(null);
      return value == null ? Optional.empty() : Optional.of(BytesValue.wrap(value));
    }
  }
}
