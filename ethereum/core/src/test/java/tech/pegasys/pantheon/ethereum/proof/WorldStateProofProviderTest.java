/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.proof;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.trie.StoredMerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.StateTrieAccountValue;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage.Updater;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WorldStateProofProviderTest {

  private static final Address address =
      Address.fromHexString("0x1234567890123456789012345678901234567890");

  private WorldStateStorage worldStateStorage =
      new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

  private WorldStateProofProvider worldStateProofProvider;

  @Before
  public void setup() {
    worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  @Test
  public void getProofWhenWorldStateNotAvailable() {
    Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(Hash.EMPTY, address, new ArrayList<>());

    assertThat(accountProof).isEmpty();
  }

  @Test
  public void getProofWhenWorldStateAvailable() {
    final MerklePatriciaTrie<Bytes32, BytesValue> worldStateTrie = emptyWorldStateTrie();
    final MerklePatriciaTrie<Bytes32, BytesValue> storageTrie = emptyStorageTrie();

    final Updater updater = worldStateStorage.updater();

    // Add some storage values
    writeStorageValue(storageTrie, UInt256.of(1L), UInt256.of(2L));
    writeStorageValue(storageTrie, UInt256.of(2L), UInt256.of(4L));
    writeStorageValue(storageTrie, UInt256.of(3L), UInt256.of(6L));
    // Save to Storage
    storageTrie.commit(updater::putAccountStorageTrieNode);

    // Define account value
    final Hash addressHash = Hash.hash(address);
    final Hash codeHash = Hash.hash(BytesValue.fromHexString("0x1122"));
    final StateTrieAccountValue accountValue =
        new StateTrieAccountValue(
            1L, Wei.of(2L), Hash.wrap(storageTrie.getRootHash()), codeHash, 0);
    // Save to storage
    worldStateTrie.put(addressHash, RLP.encode(accountValue::writeTo));
    worldStateTrie.commit(updater::putAccountStateTrieNode);

    // Persist updates
    updater.commit();

    final List<UInt256> storageKeys = Arrays.asList(UInt256.of(1L), UInt256.of(3L), UInt256.of(6L));
    final Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(
            Hash.wrap(worldStateTrie.getRootHash()), address, storageKeys);

    assertThat(accountProof).isPresent();
    assertThat(accountProof.get().getStateTrieAccountValue())
        .isEqualToComparingFieldByField(accountValue);
    assertThat(accountProof.get().getAccountProof().size()).isGreaterThanOrEqualTo(1);
    // Check storage fields
    assertThat(accountProof.get().getStorageKeys()).isEqualTo(storageKeys);
    // Check key 1
    UInt256 storageKey = UInt256.of(1L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.of(2L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    // Check key 3
    storageKey = UInt256.of(3L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.of(6L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    // Check key 6
    storageKey = UInt256.of(6L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.of(0L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
  }

  @Test
  public void getProofWhenStateTrieAccountUnavailable() {
    final MerklePatriciaTrie<Bytes32, BytesValue> worldStateTrie = emptyWorldStateTrie();

    final Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(
            Hash.wrap(worldStateTrie.getRootHash()), address, new ArrayList<>());

    assertThat(accountProof).isEmpty();
  }

  private void writeStorageValue(
      final MerklePatriciaTrie<Bytes32, BytesValue> storageTrie,
      final UInt256 key,
      final UInt256 value) {
    storageTrie.put(storageKeyHash(key), encodeStorageValue(value));
  }

  private Bytes32 storageKeyHash(final UInt256 storageKey) {
    return Hash.hash(storageKey.getBytes());
  }

  private BytesValue encodeStorageValue(final UInt256 storageValue) {
    return RLP.encode(out -> out.writeUInt256Scalar(storageValue));
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> emptyStorageTrie() {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, b -> b, b -> b);
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> emptyWorldStateTrie() {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStorageTrieNode, b -> b, b -> b);
  }
}
