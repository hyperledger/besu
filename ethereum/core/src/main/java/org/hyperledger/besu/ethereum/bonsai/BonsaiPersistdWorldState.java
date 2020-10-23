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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.bonsai.BonsaiAccount.fromRLP;
import static org.hyperledger.besu.ethereum.trie.CompactEncoding.bytesToPath;

import org.hyperledger.besu.ethereum.core.AbstractWorldUpdater;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.UpdateTrackingAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.WrappedEvmAccount;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CollectBranchesVisitor;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.io.RollingFileWriter;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

// FIXME speling
public class BonsaiPersistdWorldState implements MutableWorldState {

  //  private static final Logger LOG = LogManager.getLogger();

  private static final byte[] WORLD_ROOT_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  private final KeyValueStorage accountStorage;
  private final KeyValueStorage codeStorage;
  private final KeyValueStorage storageStorage;
  private final KeyValueStorage trieBranchStorage;
  private final KeyValueStorage trieLogStorage;

  private final Map<Address, BonsaiValue<BonsaiAccount>> accountsToUpdate = new HashMap<>();
  private final Map<Address, BonsaiValue<Bytes>> codeToUpdate = new HashMap<>();
  private final Set<Address> storageToClear = new HashSet<>();
  // storage sub mapped by _hashed_ key.  This is because in self_destruct calls we need to
  // enumerate the old storage and delete it.  Those are trie stored by hashed key by spec and the
  // alternative was to keep a giant pre-image cache of the entire trie.
  private final Map<Address, Map<Hash, BonsaiValue<UInt256>>> storageToUpdate = new HashMap<>();
  private Bytes32 worldStateRootHash;

  // FIXME
  private final RollingFileWriter layerWriter;
  private final Path dataPath;

  public BonsaiPersistdWorldState(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage,
      final Path dataPath) {
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
    this.dataPath = dataPath;
    worldStateRootHash =
        Bytes32.wrap(
            trieBranchStorage.get(WORLD_ROOT_KEY).map(Bytes::wrap).orElse(Hash.EMPTY_TRIE_HASH));
    try {
      layerWriter = (dataPath != null) ? null : new RollingFileWriter(this::bodyFileName, false);
    } catch (final FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public Path bodyFileName(final int fileNumber, final boolean compressed) {
    return dataPath
        .resolve("layer")
        .resolve(String.format("besu-layer-%04d.%sdat", fileNumber, compressed ? "c" : "r"));
  }

  @Override
  public MutableWorldState copy() {
    // return null;
    throw new RuntimeException("LOL no");
  }

  public Bytes getCode(final Address address, @SuppressWarnings("unused") final Hash codeHash) {
    final BonsaiValue<Bytes> localCode = codeToUpdate.get(address);
    if (localCode == null) {
      return codeStorage.get(address.toArrayUnsafe()).map(Bytes::wrap).orElse(Bytes.EMPTY);
    } else {
      return localCode.getUpdated();
    }
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
      for (final Address address : storageToClear) {
        // because we are clearing persisted values we need the account root as persisted
        final BonsaiAccount oldAccount =
            accountStorage
                .get(address.toArrayUnsafe())
                .map(
                    bytes ->
                        fromRLP(BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes), true))
                .orElse(null);
        if (oldAccount == null) {
          // This is when an account is both created and deleted within the scope of the same
          // block.  A not-uncommon DeFi bot pattern.
          continue;
        }
        final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
            new StoredMerklePatriciaTrie<>(
                key -> getStorageTrieNode(address, key),
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
          storageToUpdate.entrySet()) {
        final Address updatedAddress = storageAccountUpdate.getKey();
        final BonsaiValue<BonsaiAccount> accountValue = accountsToUpdate.get(updatedAddress);
        final BonsaiAccount accountOriginal = accountValue.getOriginal();
        final BonsaiAccount accountUpdated = accountValue.getUpdated();
        if (accountUpdated != null) {
          final Hash storageRoot =
              (accountOriginal == null) ? Hash.EMPTY_TRIE_HASH : accountOriginal.getStorageRoot();
          final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
              new StoredMerklePatriciaTrie<>(
                  key -> getStorageTrieNode(updatedAddress, key),
                  storageRoot,
                  Function.identity(),
                  Function.identity());
          final byte[] writeAddressArray = new byte[Address.SIZE + Hash.SIZE];
          final MutableBytes writeAddressBytes = MutableBytes.wrap(writeAddressArray);
          updatedAddress.copyTo(writeAddressBytes, 0);

          // collect account branches
          final CollectBranchesVisitor<Bytes> branchCollector = new CollectBranchesVisitor<>();
          for (final Bytes32 storageKey : storageAccountUpdate.getValue().keySet()) {
            checkNotNull(storageKey);
            storageTrie.acceptAtRoot(branchCollector, bytesToPath(storageKey));
          }

          for (final Map.Entry<Hash, BonsaiValue<UInt256>> storageUpdate :
              storageAccountUpdate.getValue().entrySet()) {
            final Hash keyHash = storageUpdate.getKey();
            keyHash.copyTo(writeAddressBytes, Address.SIZE);
            final UInt256 updatedStorage = storageUpdate.getValue().getUpdated();
            if (updatedStorage == null || updatedStorage.equals(UInt256.ZERO)) {
              storageTx.remove(writeAddressArray);
              storageTrie.remove(keyHash);
            } else {
              final Bytes32 updatedStorageBytes = updatedStorage.toBytes();
              storageTx.put(writeAddressArray, updatedStorageBytes.toArrayUnsafe());
              storageTrie.put(keyHash, rlpEncode(updatedStorageBytes));
            }
          }
          storageTrie.commit(
              (key, value) -> writeStorageTrieNode(trieBranchTx, updatedAddress, key, value));
          final Hash newStorageRoot = Hash.wrap(storageTrie.getRootHash());
          accountValue.getUpdated().setStorageRoot(newStorageRoot);
          if (accountOriginal != null && !accountOriginal.getStorageRoot().equals(newStorageRoot)) {
            // trim old branches
            for (final Bytes32 trieHash : branchCollector.getCollectedBranches()) {
              trieBranchTx.remove(Bytes.concatenate(updatedAddress, trieHash).toArrayUnsafe());
            }
          }
          // } else {
          // TODO delete account storage in else block
        }
      }

      // Third update the code.  This has the side effect of ensuring a code hash is calculated.
      for (final Map.Entry<Address, BonsaiValue<Bytes>> codeUpdate : codeToUpdate.entrySet()) {
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

      final CollectBranchesVisitor<Bytes> branchCollector = new CollectBranchesVisitor<>();
      for (final Address updatedAccountAddress : accountsToUpdate.keySet()) {
        checkNotNull(updatedAccountAddress);
        accountTrie.acceptAtRoot(branchCollector, bytesToPath(updatedAccountAddress));
      }

      // now add the accounts
      for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> accountUpdate :
          accountsToUpdate.entrySet()) {
        final Bytes accountKey = accountUpdate.getKey();
        final BonsaiValue<BonsaiAccount> bonsaiValue = accountUpdate.getValue();
        final BonsaiAccount updatedAccount = bonsaiValue.getUpdated();
        if (updatedAccount == null) {
          final Hash addressHash = Hash.hash(accountKey);
          accountTx.remove(accountKey.toArrayUnsafe());
          accountTrie.remove(addressHash);
        } else {
          final Hash addressHash = updatedAccount.getAddressHash();
          final Bytes accountValue = updatedAccount.serializeAccount();
          accountTx.put(accountKey.toArrayUnsafe(), accountValue.toArrayUnsafe());
          accountTrie.put(addressHash, accountValue);
        }
        //        throw new RuntimeException("NOPE");
      }

      accountTrie.commit((key, value) -> writeTrieNode(trieBranchTx, key, value));
      final Bytes32 oldWorldStateRootHash = worldStateRootHash;
      worldStateRootHash = accountTrie.getRootHash();
      //      LOG.debug("New account Root {}", worldStateRootHash);
      trieBranchTx.put(WORLD_ROOT_KEY, worldStateRootHash.toArrayUnsafe());

      // trim old branches
      if (!oldWorldStateRootHash.equals(worldStateRootHash)) {
        for (final Bytes32 trieHash : branchCollector.getCollectedBranches()) {
          //          if (!Hash.EMPTY_TRIE_HASH.equals(trieHash)) {
          //            LOG.debug("Deleting {} {}%n", "account", trieHash);
          //          }
          trieBranchTx.remove(trieHash.toArrayUnsafe());
        }
      }

      // FIXME get BlockHash
      if (blockHash != null) {
        final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
        generateTrieLog().writeTo(rlpLog);
        // FIXME just round trip checking
        try {
          if (layerWriter != null) {
            layerWriter.writeBytes(rlpLog.encoded().toArrayUnsafe());
          }
          TrieLogLayer.readFrom(new BytesValueRLPInput(rlpLog.encoded(), false, true));
        } catch (final Exception e) {
          System.out.println(rlpLog.encoded());
          throw new RuntimeException(e);
        }
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
        storageToClear.clear();
        storageToUpdate.clear();
        codeToUpdate.clear();
        accountsToUpdate.clear();
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
    return new BonsaiUpdater(this);
  }

  @Override
  public Hash rootHash() {
    return Hash.wrap(worldStateRootHash);
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("NIY");
  }

  @Override
  public Account get(final Address address) {
    final BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);
    if (bonsaiValue == null) {
      return accountStorage
          .get(address.toArrayUnsafe())
          .map(bytes -> fromRLP(BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes), true))
          .orElse(null);
    } else {
      return bonsaiValue.getUpdated();
    }
  }

  private Optional<Bytes> getTrieNode(final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage.get(nodeHash.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  private void writeTrieNode(
      final KeyValueStorageTransaction tx, final Bytes32 key, final Bytes value) {
    //    LOG.debug("Writing node {} {}", "account", key.toHexString());
    tx.put(key.toArrayUnsafe(), value.toArrayUnsafe());
  }

  private Optional<Bytes> getStorageTrieNode(final Address address, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage
          .get(Bytes.concatenate(address, nodeHash).toArrayUnsafe())
          .map(Bytes::wrap);
    }
  }

  private void writeStorageTrieNode(
      final KeyValueStorageTransaction tx,
      final Address address,
      final Bytes32 key,
      final Bytes value) {
    tx.put(Bytes.concatenate(address, key).toArrayUnsafe(), value.toArrayUnsafe());
  }

  public UInt256 getStorageValue(final Address address, final UInt256 storageKey) {
    // TODO log read
    final Map<Hash, BonsaiValue<UInt256>> localAccountStorage =
        storageToUpdate.computeIfAbsent(address, key -> new HashMap<>());
    final Hash slotHashBytes = Hash.hash(storageKey.toBytes());
    final BonsaiValue<UInt256> value = localAccountStorage.get(slotHashBytes);
    if (value != null) {
      return value.getUpdated();
    }
    final Bytes compositeKey = Bytes.concatenate(address, slotHashBytes);
    final Optional<byte[]> valueBits = storageStorage.get(compositeKey.toArrayUnsafe());
    if (valueBits.isPresent()) {
      final UInt256 valueUInt = UInt256.fromBytes(Bytes.wrap(valueBits.get()));
      localAccountStorage.put(slotHashBytes, new BonsaiValue<>(valueUInt, valueUInt));
      return valueUInt;
    } else {
      return UInt256.ZERO;
    }
  }

  public UInt256 getOriginalStorageValue(final Address address, final UInt256 storageKey) {
    // TODO log read?
    final Map<Hash, BonsaiValue<UInt256>> localAccountStorage =
        storageToUpdate.computeIfAbsent(address, key -> new HashMap<>());
    final Hash slotHashBytes = Hash.hash(storageKey.toBytes());
    final BonsaiValue<UInt256> value = localAccountStorage.get(slotHashBytes);
    if (value != null) {
      final UInt256 updated = value.getUpdated();
      if (updated != null) {
        return updated;
      }
      final UInt256 original = value.getOriginal();
      if (original != null) {
        return original;
      }
    }
    final Bytes compositeKey = Bytes.concatenate(address, slotHashBytes);
    final Optional<byte[]> valueBits = storageStorage.get(compositeKey.toArrayUnsafe());
    if (valueBits.isPresent()) {
      final UInt256 valueUInt = UInt256.fromBytes(Bytes.wrap(valueBits.get()));
      localAccountStorage.put(slotHashBytes, new BonsaiValue<>(valueUInt, valueUInt));
      return valueUInt;
    } else {
      return UInt256.ZERO;
    }
  }

  public void setStorageValue(final Address address, final UInt256 key, final UInt256 value) {
    // TODO log write
    final Hash keyHashBytes = Hash.hash(key.toBytes());
    final Map<Hash, BonsaiValue<UInt256>> localAccountStorage =
        storageToUpdate.computeIfAbsent(address, __ -> new HashMap<>());
    final BonsaiValue<UInt256> localValue = localAccountStorage.get(keyHashBytes);
    if (localValue == null) {
      final byte[] keyBits = Bytes.concatenate(address, keyHashBytes).toArrayUnsafe();
      final Optional<byte[]> valueBits = accountStorage.get(keyBits);
      localAccountStorage.put(
          keyHashBytes,
          new BonsaiValue<>(
              valueBits.map(Bytes32::wrap).map(UInt256::fromBytes).orElse(value), value));
    } else {
      localValue.setUpdated(value);
    }
  }

  public void setCode(final Address address, final Bytes code) {
    if (codeToUpdate.containsKey(address)) {
      codeToUpdate.get(address).setUpdated(code);
    } else {
      final byte[] addressBits = address.toArrayUnsafe();
      final Optional<byte[]> codeBits = codeStorage.get(addressBits);
      codeToUpdate.put(address, new BonsaiValue<>(codeBits.map(Bytes::wrap).orElse(null), code));
    }
  }

  public TrieLogLayer generateTrieLog() {
    final TrieLogLayer layer = new TrieLogLayer();
    // FIXME
    layer.setBlockHash(worldStateRootHash);
    for (final Map.Entry<Address, BonsaiValue<BonsaiAccount>> updatedAccount :
        accountsToUpdate.entrySet()) {
      final BonsaiValue<BonsaiAccount> bonsaiValue = updatedAccount.getValue();
      final BonsaiAccount oldValue = bonsaiValue.getOriginal();
      final StateTrieAccountValue oldAccount =
          oldValue == null
              ? null
              : new StateTrieAccountValue(
                  oldValue.getNonce(),
                  oldValue.getBalance(),
                  oldValue.getStorageRoot(),
                  oldValue.getCodeHash(),
                  oldValue.getVersion());
      final BonsaiAccount newValue = bonsaiValue.getUpdated();
      final StateTrieAccountValue newAccount =
          newValue == null
              ? null
              : new StateTrieAccountValue(
                  newValue.getNonce(),
                  newValue.getBalance(),
                  newValue.getStorageRoot(),
                  newValue.getCodeHash(),
                  newValue.getVersion());
      layer.addAccountChange(updatedAccount.getKey(), oldAccount, newAccount);
    }

    for (final Map.Entry<Address, BonsaiValue<Bytes>> updatedCode : codeToUpdate.entrySet()) {
      layer.addCodeChange(
          updatedCode.getKey(),
          updatedCode.getValue().getOriginal(),
          updatedCode.getValue().getUpdated());
    }

    for (final Map.Entry<Address, Map<Hash, BonsaiValue<UInt256>>> updatesStorage :
        storageToUpdate.entrySet()) {
      final Address address = updatesStorage.getKey();
      for (final Map.Entry<Hash, BonsaiValue<UInt256>> slotUpdate :
          updatesStorage.getValue().entrySet()) {
        layer.addStorageChange(
            address,
            slotUpdate.getKey(),
            slotUpdate.getValue().getOriginal(),
            slotUpdate.getValue().getUpdated());
      }
    }

    return layer;
  }

  public void rollForward(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(), entry.getValue().getOriginal(), entry.getValue().getUpdated()));
    layer
        .streamCodeChanges()
        .forEach(
            entry ->
                rollCodeChange(
                    entry.getKey(), entry.getValue().getOriginal(), entry.getValue().getUpdated()));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (key, value) ->
                            rollStorageChange(
                                entry.getKey(), key, value.getOriginal(), value.getUpdated())));
  }

  public void rollBack(final TrieLogLayer layer) {
    layer
        .streamAccountChanges()
        .forEach(
            entry ->
                rollAccountChange(
                    entry.getKey(), entry.getValue().getUpdated(), entry.getValue().getOriginal()));
    layer
        .streamCodeChanges()
        .forEach(
            entry ->
                rollCodeChange(
                    entry.getKey(), entry.getValue().getUpdated(), entry.getValue().getOriginal()));
    layer
        .streamStorageChanges()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(
                        (slotHash, value) ->
                            rollStorageChange(
                                entry.getKey(),
                                slotHash,
                                value.getUpdated(),
                                value.getOriginal())));
  }

  private void rollAccountChange(
      final Address address,
      final StateTrieAccountValue expectedValue,
      final StateTrieAccountValue replacementValue) {
    if (Objects.equals(expectedValue, replacementValue)) {
      // non-change, a cached read.
      return;
    }
    BonsaiValue<BonsaiAccount> accountValue = accountsToUpdate.get(address);
    if (accountValue == null) {
      accountValue = loadAccountFromStorage(address, accountValue);
    }
    if (accountValue == null) {
      if (expectedValue == null && replacementValue != null) {
        accountsToUpdate.put(
            address,
            new BonsaiValue<>(
                null,
                new BonsaiAccount(
                    this,
                    address,
                    Hash.hash(address),
                    replacementValue.getNonce(),
                    replacementValue.getBalance(),
                    replacementValue.getStorageRoot(),
                    replacementValue.getCodeHash(),
                    replacementValue.getVersion(),
                    true)));
      } else {
        throw new IllegalStateException(
            "Expected to update account, but the account does not exist");
      }
    } else {
      if (expectedValue == null) {
        throw new IllegalStateException("Expected to create account, but the account exists");
      }
      BonsaiAccount.assertCloseEnoughForDiffing(
          accountValue.getUpdated(), expectedValue, "Prior Value in Rolling Change");
      if (replacementValue == null) {
        if (accountValue.getOriginal() == null) {
          accountsToUpdate.remove(address);
        } else {
          accountValue.setUpdated(null);
        }
      } else {
        final BonsaiAccount existingAccount = accountValue.getUpdated();
        existingAccount.setNonce(replacementValue.getNonce());
        existingAccount.setBalance(replacementValue.getBalance());
        existingAccount.setStorageRoot(replacementValue.getStorageRoot());
        // depend on correctly structured layers to set code hash
        // existingAccount.setCodeHash(oldValue.getNonce());
        existingAccount.setVersion(replacementValue.getVersion());
      }
    }
  }

  private BonsaiValue<BonsaiAccount> loadAccountFromStorage(
      final Address address, final BonsaiValue<BonsaiAccount> accountValue) {
    final Optional<byte[]> bytes = accountStorage.get(address.toArrayUnsafe());
    if (bytes.isPresent()) {
      final BonsaiAccount account =
          BonsaiAccount.fromRLP(
              BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes.get()), true);
      final BonsaiValue<BonsaiAccount> loadedAccountValue =
          new BonsaiValue<>(new BonsaiAccount(account), account);
      accountsToUpdate.put(address, loadedAccountValue);
      return loadedAccountValue;
    } else {
      return accountValue;
    }
  }

  private void rollCodeChange(
      final Address address, final Bytes expectedCode, final Bytes replacementCode) {
    if (Objects.equals(expectedCode, replacementCode)) {
      // non-change, a cached read.
      return;
    }
    BonsaiValue<Bytes> codeValue = codeToUpdate.get(address);
    if (codeValue == null) {
      final Optional<byte[]> bytes = codeStorage.get(address.toArrayUnsafe());
      if (bytes.isPresent()) {
        final Bytes codeBytes = Bytes.wrap(bytes.get());
        codeValue = new BonsaiValue<>(codeBytes, codeBytes);
        codeToUpdate.put(address, codeValue);
      }
    }

    if (codeValue == null) {
      if (expectedCode == null && replacementCode != null) {
        codeToUpdate.put(address, new BonsaiValue<>(null, replacementCode));
      } else {
        throw new IllegalStateException("Expected to update code, but the code does not exist");
      }
    } else {
      if (expectedCode == null) {
        throw new IllegalStateException("Expected to create code, but the code exists");
      }
      if (!codeValue.getUpdated().equals(expectedCode)) {
        throw new IllegalStateException("Old value of code does not match expected value");
      }
      if (replacementCode == null) {
        if (codeValue.getOriginal() == null) {
          codeToUpdate.remove(address);
        } else {
          codeValue.setUpdated(null);
        }
      } else {
        codeValue.setUpdated(replacementCode);
      }
    }
  }

  private Map<Hash, BonsaiValue<UInt256>> maybeCreateStorageMap(
      final Map<Hash, BonsaiValue<UInt256>> storageMap, final Address address) {
    if (storageMap == null) {
      final Map<Hash, BonsaiValue<UInt256>> newMap = new HashMap<>();
      storageToUpdate.put(address, newMap);
      return newMap;
    } else {
      return storageMap;
    }
  }

  private void rollStorageChange(
      final Address address,
      final Hash slotHash,
      final UInt256 expectedValue,
      final UInt256 replacementValue) {
    if (Objects.equals(expectedValue, replacementValue)) {
      // non-change, a cached read.
      return;
    }
    final Map<Hash, BonsaiValue<UInt256>> storageMap = storageToUpdate.get(address);
    BonsaiValue<UInt256> slotValue = storageMap == null ? null : storageMap.get(slotHash);
    if (slotValue == null) {
      final Bytes compositeKey = Bytes.concatenate(address, slotHash);
      final Optional<byte[]> valueBits = storageStorage.get(compositeKey.toArrayUnsafe());
      if (valueBits.isPresent()) {
        final UInt256 storageValue = UInt256.fromBytes(Bytes.wrap(valueBits.get()));
        slotValue = new BonsaiValue<>(storageValue, storageValue);
        storageToUpdate.computeIfAbsent(address, k -> new HashMap<>()).put(slotHash, slotValue);
      }
    }
    if (slotValue == null) {
      if (expectedValue == null && replacementValue != null) {
        maybeCreateStorageMap(storageMap, address)
            .put(slotHash, new BonsaiValue<>(null, replacementValue));
      } else {
        throw new IllegalStateException(
            "Expected to update storage value, but the slot does not exist");
      }
    } else {
      if (expectedValue == null) {
        throw new IllegalStateException("Expected to create slot, but the slot exists");
      }
      final UInt256 existingSlotValue = slotValue.getUpdated();
      if (!existingSlotValue.equals(expectedValue)) {
        throw new IllegalStateException("Old value of slot does not match expected value");
      }
      if (replacementValue == null) {
        if (slotValue.getOriginal() == null) {
          final Map<Hash, BonsaiValue<UInt256>> thisStorageUpdate =
              maybeCreateStorageMap(storageMap, address);
          thisStorageUpdate.remove(slotHash);
          if (thisStorageUpdate.isEmpty()) {
            storageToUpdate.remove(address);
          }
        } else {
          slotValue.setUpdated(null);
        }
      } else {
        slotValue.setUpdated(replacementValue);
      }
    }
  }

  public class BonsaiUpdater extends AbstractWorldUpdater<BonsaiPersistdWorldState, BonsaiAccount> {

    protected BonsaiUpdater(final BonsaiPersistdWorldState world) {
      super(world);
    }

    @Override
    public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
      BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);
      if (bonsaiValue == null) {
        bonsaiValue = new BonsaiValue<>(null, null);
        accountsToUpdate.put(address, bonsaiValue);
      } else if (bonsaiValue.getUpdated() != null) {
        throw new IllegalStateException("Cannot create an account when one already exists");
      }
      final BonsaiAccount newAccount =
          new BonsaiAccount(
              BonsaiPersistdWorldState.this,
              address,
              Hash.hash(address),
              nonce,
              balance,
              Hash.EMPTY_TRIE_HASH,
              Hash.EMPTY,
              Account.DEFAULT_VERSION,
              true);
      bonsaiValue.setUpdated(newAccount);
      return new WrappedEvmAccount(track(new UpdateTrackingAccount<>(newAccount)));
    }

    @Override
    protected BonsaiAccount getForMutation(final Address address) {
      final BonsaiValue<BonsaiAccount> bonsaiValue = accountsToUpdate.get(address);
      if (bonsaiValue == null) {
        final BonsaiAccount storedAccount =
            accountStorage
                .get(address.toArrayUnsafe())
                .map(
                    bytes ->
                        fromRLP(BonsaiPersistdWorldState.this, address, Bytes.wrap(bytes), true))
                .orElse(null);
        if (storedAccount != null) {
          accountsToUpdate.put(
              address, new BonsaiValue<>(new BonsaiAccount(storedAccount), storedAccount));
        }
        return storedAccount;
      } else {
        return bonsaiValue.getUpdated();
      }
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      // FIXME ?
      return getUpdatedAccounts();
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      // FIXME ?
      return getDeletedAccounts();
    }

    @Override
    public void revert() {
      // FIXME ?
      getDeletedAccounts().clear();
      getUpdatedAccounts().clear();
    }

    @Override
    public void commit() {
      for (final Address deletedAddress : getDeletedAccounts()) {
        storageToClear.add(deletedAddress);
        final BonsaiValue<Bytes> codeValue = codeToUpdate.get(deletedAddress);
        if (codeValue != null) {
          codeValue.setUpdated(null);
        } else {
          codeStorage
              .get(deletedAddress.toArrayUnsafe())
              .ifPresent(
                  bytes ->
                      codeToUpdate.put(deletedAddress, new BonsaiValue<>(Bytes.wrap(bytes), null)));
        }
        final BonsaiValue<BonsaiAccount> accountValue =
            accountsToUpdate.computeIfAbsent(
                deletedAddress,
                addr -> loadAccountFromStorage(deletedAddress, new BonsaiValue<>(null, null)));

        // mark all updated storage as to be cleared
        final Map<Hash, BonsaiValue<UInt256>> deletedStorageUpdates =
            storageToUpdate.computeIfAbsent(deletedAddress, k -> new HashMap<>());
        final Iterator<Map.Entry<Hash, BonsaiValue<UInt256>>> iter =
            deletedStorageUpdates.entrySet().iterator();
        while (iter.hasNext()) {
          final Map.Entry<Hash, BonsaiValue<UInt256>> updateEntry = iter.next();
          final BonsaiValue<UInt256> updatedSlot = updateEntry.getValue();
          if (updatedSlot.getOriginal() == null || updatedSlot.getOriginal().isZero()) {
            iter.remove();
          } else {
            updatedSlot.setUpdated(null);
          }
        }

        final BonsaiAccount effective = accountValue.effective();
        if (effective != null) {
          // Enumerate and delete addresses not updated
          final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
              new StoredMerklePatriciaTrie<>(
                  key -> getStorageTrieNode(deletedAddress, key),
                  effective.getStorageRoot(),
                  Function.identity(),
                  Function.identity());
          Map<Bytes32, Bytes> entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
          while (!entriesToDelete.isEmpty()) {
            entriesToDelete.forEach(
                (slotBytes, v) -> {
                  final Hash slotHash = Hash.wrap(slotBytes);
                  if (!deletedStorageUpdates.containsKey(slotHash)) {
                    final UInt256 value = UInt256.fromBytes(RLP.decodeOne(v));
                    deletedStorageUpdates.put(slotHash, new BonsaiValue<>(value, null));
                  }
                });
            if (entriesToDelete.size() == 256) {
              entriesToDelete.keySet().forEach(storageTrie::remove);
              entriesToDelete = storageTrie.entriesFrom(Bytes32.ZERO, 256);
            } else {
              break;
            }
          }
        }
        if (deletedStorageUpdates.isEmpty()) {
          storageToUpdate.remove(deletedAddress);
        }

        if (accountValue != null) {
          accountValue.setUpdated(null);
        }
      }

      for (final UpdateTrackingAccount<BonsaiAccount> tracked : getUpdatedAccounts()) {
        BonsaiAccount updatedAccount = tracked.getWrappedAccount();
        if (updatedAccount == null) {
          updatedAccount = new BonsaiAccount(BonsaiPersistdWorldState.this, tracked);
          accountsToUpdate.put(tracked.getAddress(), new BonsaiValue<>(null, updatedAccount));
        } else {
          updatedAccount.setBalance(tracked.getBalance());
          updatedAccount.setNonce(tracked.getNonce());
          updatedAccount.setCode(tracked.getCode());
          if (tracked.getStorageWasCleared()) {
            updatedAccount.clearStorage();
          }
          tracked.getUpdatedStorage().forEach(updatedAccount::setStorageValue);
        }
        final Address updatedAddress = updatedAccount.getAddress();

        final BonsaiValue<Bytes> pendingCode =
            codeToUpdate.computeIfAbsent(updatedAddress, __ -> new BonsaiValue<>(null, null));
        pendingCode.setUpdated(updatedAccount.getCode());

        final Map<Hash, BonsaiValue<UInt256>> pendingStorageUpdates =
            storageToUpdate.computeIfAbsent(updatedAddress, __ -> new HashMap<>());
        if (tracked.getStorageWasCleared()) {
          // TODO mark that we need to clear out an accounts storage
          storageToClear.add(tracked.getAddress());
          pendingStorageUpdates.clear();
        }

        final TreeSet<Map.Entry<UInt256, UInt256>> entries =
            new TreeSet<>(
                Comparator.comparing(
                    (Function<Map.Entry<UInt256, UInt256>, UInt256>) Map.Entry::getKey));
        entries.addAll(updatedAccount.getUpdatedStorage().entrySet());

        for (final Map.Entry<UInt256, UInt256> storageUpdate : entries) {
          final UInt256 keyUInt = storageUpdate.getKey();
          final Hash slotHash = Hash.hash(keyUInt.toBytes());
          final UInt256 value = storageUpdate.getValue();
          final BonsaiValue<UInt256> pendingValue = pendingStorageUpdates.get(slotHash);
          if (pendingValue == null) {
            pendingStorageUpdates.put(
                slotHash,
                new BonsaiValue<>(updatedAccount.getOriginalStorageValue(keyUInt), value));
          } else {
            pendingValue.setUpdated(value);
          }
        }
        updatedAccount.getUpdatedStorage().clear();

        // TODO address preimage
      }
    }
  }
}
