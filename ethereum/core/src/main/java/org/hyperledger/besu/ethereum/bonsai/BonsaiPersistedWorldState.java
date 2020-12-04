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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.ethereum.bonsai.BonsaiAccount.fromRLP;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiPersistedWorldState implements MutableWorldState, BonsaiWorldState {

  private static final byte[] WORLD_ROOT_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  private final KeyValueStorage accountStorage;
  private final KeyValueStorage codeStorage;
  private final KeyValueStorage storageStorage;
  private final KeyValueStorage trieBranchStorage;
  private final KeyValueStorage trieLogStorage;

  private Bytes32 worldStateRootHash;

  private final BonsaiWorldStateArchive archive;
  private BonsaiWorldStateUpdater updater;

  public BonsaiPersistedWorldState(
      final BonsaiWorldStateArchive archive,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    this.archive = archive;
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
    worldStateRootHash =
        Bytes32.wrap(
            trieBranchStorage.get(WORLD_ROOT_KEY).map(Bytes::wrap).orElse(Hash.EMPTY_TRIE_HASH));
  }

  public BonsaiWorldStateArchive getArchive() {
    return archive;
  }

  @Override
  public MutableWorldState copy() {
    throw new UnsupportedOperationException(
        "Bonsai Tries does not support direct duplication of the persisted tries.");
  }

  @Override
  public Bytes getCode(@Nonnull final Address address) {
    return codeStorage.get(address.toArrayUnsafe()).map(Bytes::wrap).orElse(Bytes.EMPTY);
  }

  @Override
  public void persist(final Hash blockHash) {
    boolean success = false;
    final KeyValueStorageTransaction accountTx = accountStorage.startTransaction();
    final KeyValueStorageTransaction codeTx = codeStorage.startTransaction();
    final KeyValueStorageTransaction storageTx = storageStorage.startTransaction();
    final KeyValueStorageTransaction trieBranchTx = trieBranchStorage.startTransaction();
    final KeyValueStorageTransaction trieLogTx = trieLogStorage.startTransaction();

    try {
      // first clear storage
      for (final Address address : updater.getStorageToClear()) {
        // because we are clearing persisted values we need the account root as persisted
        final BonsaiAccount oldAccount =
            accountStorage
                .get(address.toArrayUnsafe())
                .map(
                    bytes ->
                        fromRLP(BonsaiPersistedWorldState.this, address, Bytes.wrap(bytes), true))
                .orElse(null);
        if (oldAccount == null) {
          // This is when an account is both created and deleted within the scope of the same
          // block.  A not-uncommon DeFi bot pattern.
          continue;
        }
        final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
            new StoredMerklePatriciaTrie<>(
                (location, key) -> getStorageTrieNode(address, location, key),
                oldAccount.getStorageRoot(),
                Function.identity(),
                Function.identity());
        Map<Bytes32, Bytes> entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
        while (!entriesToDelete.isEmpty()) {
          entriesToDelete
              .keySet()
              .forEach(k -> storageTx.remove(Bytes.concatenate(address, k).toArrayUnsafe()));
          if (entriesToDelete.size() == 256) {
            entriesToDelete.keySet().forEach(storageTrie::remove);
            entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
          } else {
            break;
          }
        }
      }

      // second update account storage state.  This must be done before updating the accounts so
      // that we can get the storage state hash
      for (final Map.Entry<Address, Map<Hash, BonsaiValue<UInt256>>> storageAccountUpdate :
          updater.getStorageToUpdate().entrySet()) {
        final Address updatedAddress = storageAccountUpdate.getKey();
        final BonsaiValue<BonsaiAccount> accountValue =
            updater.getAccountsToUpdate().get(updatedAddress);
        final BonsaiAccount accountOriginal = accountValue.getOriginal();
        final Hash storageRoot =
            (accountOriginal == null) ? Hash.EMPTY_TRIE_HASH : accountOriginal.getStorageRoot();
        final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
            new StoredMerklePatriciaTrie<>(
                (location, key) -> getStorageTrieNode(updatedAddress, location, key),
                storageRoot,
                Function.identity(),
                Function.identity());

        // for manicured tries and composting, collect branches here (not implemented)

        for (final Map.Entry<Hash, BonsaiValue<UInt256>> storageUpdate :
            storageAccountUpdate.getValue().entrySet()) {
          final Hash keyHash = storageUpdate.getKey();
          final byte[] writeAddress = Bytes.concatenate(updatedAddress, keyHash).toArrayUnsafe();
          final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
          if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
            storageTx.remove(writeAddress);
            storageTrie.remove(keyHash);
          } else {
            final Bytes32 updatedStorageBytes = updatedStorage.toBytes();
            storageTx.put(writeAddress, updatedStorageBytes.toArrayUnsafe());
            storageTrie.put(keyHash, rlpEncode(updatedStorageBytes));
          }
        }

        final BonsaiAccount accountUpdated = accountValue.getUpdated();
        if (accountUpdated != null) {
          storageTrie.commit(
              (location, key, value) ->
                  writeStorageTrieNode(trieBranchTx, updatedAddress, location, value));
          final Hash newStorageRoot = Hash.wrap(storageTrie.getRootHash());
          accountUpdated.setStorageRoot(newStorageRoot);
        }
        // for manicured tries and composting, trim and compost here
      }

      // Third update the code.  This has the side effect of ensuring a code hash is calculated.
      for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate :
          updater.getCodeToUpdate().entrySet()) {
        final Bytes updatedCode = codeUpdate.getValue().getUpdated();
        if (updatedCode == null || updatedCode.size() == 0) {
          codeTx.remove(codeUpdate.getKey().toArrayUnsafe());
        } else {
          codeTx.put(codeUpdate.getKey().toArrayUnsafe(), updatedCode.toArrayUnsafe());
        }
      }

      // next collect the branches that will be trimmed
      final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              this::getTrieNode, worldStateRootHash, Function.identity(), Function.identity());

      // for manicured tries and composting, collect branches here (not implemented)

      // now add the accounts
      for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
          updater.getAccountsToUpdate().entrySet()) {
        final Bytes accountKey = accountUpdate.getKey();
        final BonsaiValue<BonsaiAccount> bonsaiValue = accountUpdate.getValue();
        final BonsaiAccount updatedAccount = bonsaiValue.getUpdated();
        if (updatedAccount == null) {
          final Hash addressHash = Hash.hash(accountKey);
          accountTrie.remove(addressHash);
          accountTx.remove(accountKey.toArrayUnsafe());
        } else {
          final Hash addressHash = updatedAccount.getAddressHash();
          final Bytes accountValue = updatedAccount.serializeAccount();
          accountTx.put(accountKey.toArrayUnsafe(), accountValue.toArrayUnsafe());
          accountTrie.put(addressHash, accountValue);
        }
      }

      accountTrie.commit((location, hash, value) -> writeTrieNode(trieBranchTx, location, value));
      worldStateRootHash = accountTrie.getRootHash();
      trieBranchTx.put(WORLD_ROOT_KEY, worldStateRootHash.toArrayUnsafe());

      // for manicured tries and composting, trim and compost branches here

      if (blockHash != null) {
        final TrieLogLayer trieLog = updater.generateTrieLog(blockHash);
        trieLog.freeze();
        // TODO add to archive here, but only once we get persisted follow distance implemented
        // archive.addLayeredWorldState(new BonsaiLayeredWorldState(this, trieLog));

        final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
        trieLog.writeTo(rlpLog);
        trieLogTx.put(blockHash.toArrayUnsafe(), rlpLog.encoded().toArrayUnsafe());
      }

      success = true;
    } finally {
      if (success) {
        accountTx.commit();
        codeTx.commit();
        storageTx.commit();
        trieBranchTx.commit();
        trieLogTx.commit();
        updater.reset();
      } else {
        accountTx.rollback();
        codeTx.rollback();
        storageTx.rollback();
        trieBranchTx.rollback();
        trieLogTx.rollback();
      }
    }
  }

  private static Bytes rlpEncode(final Bytes bytes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytes(bytes.trimLeadingZeros());
    return out.encoded();
  }

  @Override
  public WorldUpdater updater() {
    if (updater == null) {
      updater = new BonsaiWorldStateUpdater(this);
    }
    return updater;
  }

  @Override
  public Hash rootHash() {
    return Hash.wrap(worldStateRootHash);
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("Bonsai Tries do not provide account streaming.");
  }

  @Override
  public Account get(final Address address) {
    return accountStorage
        .get(address.toArrayUnsafe())
        .map(bytes -> fromRLP(updater, address, Bytes.wrap(bytes), true))
        .orElse(null);
  }

  private Optional<Bytes> getTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage.get(location.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  private void writeTrieNode(
      final KeyValueStorageTransaction tx, final Bytes location, final Bytes value) {
    tx.put(location.toArrayUnsafe(), value.toArrayUnsafe());
  }

  private Optional<Bytes> getStorageTrieNode(
      final Address address, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage
          .get(Bytes.concatenate(address, location).toArrayUnsafe())
          .map(Bytes::wrap);
    }
  }

  private void writeStorageTrieNode(
      final KeyValueStorageTransaction tx,
      final Address address,
      final Bytes location,
      final Bytes value) {
    tx.put(Bytes.concatenate(address, location).toArrayUnsafe(), value.toArrayUnsafe());
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValueBySlotHash(address, Hash.hash(storageKey.toBytes())).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueBySlotHash(final Address address, final Hash slotHash) {
    return storageStorage
        .get(Bytes.concatenate(address, slotHash).toArrayUnsafe())
        .map(Bytes::wrap)
        .map(UInt256::fromBytes);
  }

  @Override
  public UInt256 getOriginalStorageValue(final Address address, final UInt256 storageKey) {
    return getStorageValue(address, storageKey);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            (location, key) -> getStorageTrieNode(address, location, key),
            rootHash,
            Function.identity(),
            Function.identity());
    return storageTrie.entriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
  }
}
