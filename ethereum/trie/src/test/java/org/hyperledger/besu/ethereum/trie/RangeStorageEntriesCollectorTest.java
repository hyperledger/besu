/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie;

import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class RangeStorageEntriesCollectorTest {

  @Test
  public void shouldRetrieveAllLeavesInRangeWhenStartFromZero() {
    InMemoryKeyValueStorage worldStateKeyValueStorage = new InMemoryKeyValueStorage();
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                worldStateKeyValueStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap),
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
    InMemoryKeyValueStorage worldStateKeyValueStorage = new InMemoryKeyValueStorage();
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                worldStateKeyValueStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap),
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
    InMemoryKeyValueStorage worldStateKeyValueStorage = new InMemoryKeyValueStorage();
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                worldStateKeyValueStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap),
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
