package org.hyperledger.besu.ethereum.trie;

import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class RangeStorageEntriesCollectorTest {

  @Test
  public void shouldRetrieveAllLeavesInRangeWhenStartFromZero() {
    InMemoryKeyValueStorage worldStateStorage = new InMemoryKeyValueStorage();
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) -> worldStateStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap),
            b -> b,
            b -> b);
    final List<Bytes32> lists =
        List.of(
            Bytes32.rightPad(Bytes.of(1, 1, 3, 0)),
            Bytes32.rightPad(Bytes.of(1, 1, 3, 1)),
            Bytes32.rightPad(Bytes.of(1, 2, 0, 0)));
    lists.forEach(bytes -> accountStateTrie.put(bytes, Bytes.of(1, 2, 3)));
    Assertions.assertThat(
            accountStateTrie.entriesFrom(Bytes32.rightPad(Bytes.of(0, 0, 0, 0)), 3).keySet())
        .containsAll(lists);
  }

  @Test
  public void shouldRetrieveAllLeavesInRangeWhenStartFromSpecificRange() {
    InMemoryKeyValueStorage worldStateStorage = new InMemoryKeyValueStorage();
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) -> worldStateStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap),
            b -> b,
            b -> b);
    final List<Bytes32> lists =
        List.of(
            Bytes32.rightPad(Bytes.of(1, 1, 3, 0)),
            Bytes32.rightPad(Bytes.of(1, 1, 3, 1)),
            Bytes32.rightPad(Bytes.of(1, 2, 0, 0)));
    lists.forEach(bytes -> accountStateTrie.put(bytes, Bytes.of(1, 2, 3)));
    Assertions.assertThat(
            accountStateTrie.entriesFrom(Bytes32.rightPad(Bytes.of(1, 1, 2, 1)), 3).keySet())
        .containsAll(lists);
  }

  @Test
  public void shouldExcludeLeavesNotInRange() {
    InMemoryKeyValueStorage worldStateStorage = new InMemoryKeyValueStorage();
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) -> worldStateStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap),
            b -> b,
            b -> b);
    final List<Bytes32> lists =
        List.of(
            Bytes32.rightPad(Bytes.of(1, 1, 3, 0)),
            Bytes32.rightPad(Bytes.of(1, 1, 3, 1)),
            Bytes32.rightPad(Bytes.of(1, 2, 0, 0)));
    lists.forEach(bytes -> accountStateTrie.put(bytes, Bytes.of(1, 2, 3)));
    Assertions.assertThat(
            accountStateTrie.entriesFrom(Bytes32.rightPad(Bytes.of(1, 1, 9, 9)), 1).keySet())
        .contains(Bytes32.rightPad(Bytes.of(1, 2, 0, 0)));
  }
}
