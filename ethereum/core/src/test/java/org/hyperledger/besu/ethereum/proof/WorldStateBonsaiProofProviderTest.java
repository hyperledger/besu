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
package org.hyperledger.besu.ethereum.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WorldStateBonsaiProofProviderTest {

  private static final Address address =
      Address.fromHexString("0x1234567890123456789012345678901234567890");

  private final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage =
      new BonsaiWorldStateKeyValueStorage(
          new InMemoryKeyValueStorageProvider(),
          new NoOpMetricsSystem(),
          DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);

  private WorldStateProofProvider worldStateProofProvider;

  @BeforeEach
  public void setup() {
    worldStateProofProvider =
        new WorldStateProofProvider(new WorldStateStorageCoordinator(worldStateKeyValueStorage));
  }

  @Test
  public void getProofWhenWorldStateNotAvailable() {
    Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(Hash.EMPTY, address, new ArrayList<>());

    assertThat(accountProof).isEmpty();
  }

  @Test
  public void getProofWhenWorldStateAvailable() {
    final Hash addressHash = address.addressHash();
    final MerkleTrie<Bytes32, Bytes> worldStateTrie = emptyWorldStateTrie();
    final MerkleTrie<Bytes32, Bytes> storageTrie = emptyStorageTrie();

    final BonsaiWorldStateKeyValueStorage.Updater updater = worldStateKeyValueStorage.updater();

    // Add some storage values
    writeStorageValue(storageTrie, UInt256.ONE, UInt256.valueOf(2L));
    writeStorageValue(storageTrie, UInt256.valueOf(2L), UInt256.valueOf(4L));
    writeStorageValue(storageTrie, UInt256.valueOf(3L), UInt256.valueOf(6L));
    // Save to Storage
    storageTrie.commit(
        (location, hash, value) ->
            updater.putAccountStorageTrieNode(addressHash, location, hash, value));

    // Define account value
    final Hash codeHash = Hash.hash(Bytes.fromHexString("0x1122"));
    final Optional<PmtStateTrieAccountValue> accountValue =
        Optional.of(
            new PmtStateTrieAccountValue(
                1L, Wei.of(2L), Hash.wrap(storageTrie.getRootHash()), codeHash));
    // Save to storage
    worldStateTrie.put(addressHash, RLP.encode(accountValue.get()::writeTo));
    worldStateTrie.commit(
        (location, hash, value) -> updater.putAccountStateTrieNode(location, hash, value));

    SegmentedKeyValueStorageTransaction tx = updater.getWorldStateTransaction();
    tx.put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, worldStateTrie.getRootHash().toArrayUnsafe());

    // Persist updates
    updater.commit();

    final List<UInt256> storageKeys =
        Arrays.asList(UInt256.ONE, UInt256.valueOf(3L), UInt256.valueOf(6L));
    final Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(
            Hash.wrap(worldStateTrie.getRootHash()), address, storageKeys);

    assertThat(accountProof).isPresent();
    assertThat(accountProof.get().getStateTrieAccountValue().get().getBalance())
        .isEqualTo(accountValue.get().getBalance());
    assertThat(accountProof.get().getStateTrieAccountValue().get().getNonce())
        .isEqualTo(accountValue.get().getNonce());
    assertThat(accountProof.get().getAccountProof().size()).isGreaterThanOrEqualTo(1);
    // Check storage fields
    assertThat(accountProof.get().getStorageKeys()).isEqualTo(storageKeys);
    // Check key 1
    UInt256 storageKey = UInt256.ONE;
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.valueOf(2L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    // Check key 3
    storageKey = UInt256.valueOf(3L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.valueOf(6L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    // Check key 6
    storageKey = UInt256.valueOf(6L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    assertThat(accountProof.get().getStateTrieAccountValue().get().getCodeHash())
        .isEqualTo(accountValue.get().getCodeHash());
    assertThat(accountProof.get().getStateTrieAccountValue().get().getStorageRoot())
        .isEqualTo(accountValue.get().getStorageRoot());

    Assertions.assertThat(accountProof.get().getStateTrieAccountValue().get())
        .usingRecursiveComparison()
        .usingOverriddenEquals()
        .isEqualTo(accountValue.get());
  }

  @Test
  public void getProofWhenStateTrieAccountUnavailable() {
    final MerkleTrie<Bytes32, Bytes> worldStateTrie = emptyWorldStateTrie();

    final Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(
            Hash.wrap(worldStateTrie.getRootHash()), address, new ArrayList<>());

    assertThat(accountProof).isEmpty();
  }

  private void writeStorageValue(
      final MerkleTrie<Bytes32, Bytes> storageTrie, final UInt256 key, final UInt256 value) {
    storageTrie.put(storageKeyHash(key), encodeStorageValue(value));
  }

  private Bytes32 storageKeyHash(final UInt256 storageKey) {
    return Hash.hash(storageKey);
  }

  private Bytes encodeStorageValue(final UInt256 storageValue) {
    return RLP.encode(out -> out.writeBytes(storageValue.toMinimalBytes()));
  }

  private MerkleTrie<Bytes32, Bytes> emptyStorageTrie() {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) -> worldStateKeyValueStorage.getAccountStateTrieNode(location, hash),
        b -> b,
        b -> b);
  }

  private MerkleTrie<Bytes32, Bytes> emptyWorldStateTrie() {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) ->
            worldStateKeyValueStorage.getAccountStorageTrieNode(Hash.EMPTY, location, hash),
        b -> b,
        b -> b);
  }
}
