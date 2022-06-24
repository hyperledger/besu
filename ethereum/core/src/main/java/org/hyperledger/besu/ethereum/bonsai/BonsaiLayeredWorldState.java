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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A World State backed first by trie log layer and then by another world state. */
public class BonsaiLayeredWorldState implements MutableWorldState, BonsaiWorldView, WorldState {
  private Optional<BonsaiWorldView> nextWorldView;
  protected final long height;
  protected final TrieLogLayer trieLog;
  private final Hash worldStateRootHash;

  private final Blockchain blockchain;
  private final BonsaiWorldStateArchive archive;

  BonsaiLayeredWorldState(
      final Blockchain blockchain,
      final BonsaiWorldStateArchive archive,
      final Optional<BonsaiWorldView> nextWorldView,
      final long height,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog) {
    this.blockchain = blockchain;
    this.archive = archive;
    this.nextWorldView = nextWorldView;
    this.height = height;
    this.worldStateRootHash = worldStateRootHash;
    this.trieLog = trieLog;
  }

  public Optional<BonsaiWorldView> getNextWorldView() {
    if (nextWorldView.isEmpty()) {
      final Optional<Hash> blockHashByNumber = blockchain.getBlockHashByNumber(height + 1);
      nextWorldView =
          blockHashByNumber
              .map(hash -> archive.getMutable(null, hash, false).map(BonsaiWorldView.class::cast))
              .orElseGet(() -> Optional.of(archive.getMutable()).map(BonsaiWorldView.class::cast));
    }
    return nextWorldView;
  }

  public void setNextWorldView(final Optional<BonsaiWorldView> nextWorldView) {
    this.nextWorldView = nextWorldView;
  }

  public TrieLogLayer getTrieLog() {
    return trieLog;
  }

  public long getHeight() {
    return height;
  }

  @Override
  public Optional<Bytes> getCode(final Address address) {
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      final Optional<Bytes> maybeCode = currentLayer.trieLog.getCode(address);
      final Optional<Bytes> maybePriorCode = currentLayer.trieLog.getPriorCode(address);
      if (currentLayer == this && maybeCode.isPresent()) {
        return maybeCode;
      } else if (maybePriorCode.isPresent()) {
        return maybePriorCode;
      } else if (maybeCode.isPresent()) {
        return Optional.empty();
      }
      if (currentLayer.getNextWorldView().isEmpty()) {
        currentLayer = null;
      } else if (currentLayer.getNextWorldView().get() instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.getNextWorldView().get();
      } else {
        return currentLayer.getNextWorldView().get().getCode(address);
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      if (currentLayer.getNextWorldView().isEmpty()) {
        currentLayer = null;
      } else if (currentLayer.getNextWorldView().get() instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.getNextWorldView().get();
      } else {
        return currentLayer.getNextWorldView().get().getStateTrieNode(location);
      }
    }
    return Optional.empty();
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 key) {
    return getStorageValueBySlotHash(address, Hash.hash(key)).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueBySlotHash(final Address address, final Hash slotHash) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      final Optional<UInt256> maybeValue =
          currentLayer.trieLog.getStorageBySlotHash(address, slotHash);
      final Optional<UInt256> maybePriorValue =
          currentLayer.trieLog.getPriorStorageBySlotHash(address, slotHash);
      if (currentLayer == this && maybeValue.isPresent()) {
        return maybeValue;
      } else if (maybePriorValue.isPresent()) {
        return maybePriorValue;
      } else if (maybeValue.isPresent()) {
        return Optional.empty();
      }
      if (currentLayer.getNextWorldView().isEmpty()) {
        currentLayer = null;
      } else if (currentLayer.getNextWorldView().get() instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.getNextWorldView().get();
      } else {
        return currentLayer.getNextWorldView().get().getStorageValueBySlotHash(address, slotHash);
      }
    }
    return Optional.empty();
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 key) {
    // This is the base layer for a block, all values are original.
    return getStorageValue(address, key);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    final Map<Bytes32, Bytes> results = new HashMap<>();
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      if (currentLayer.trieLog.hasStorageChanges(address)) {
        currentLayer
            .trieLog
            .streamStorageChanges(address)
            .forEach(
                entry -> {
                  if (!results.containsKey(entry.getKey())) {
                    final UInt256 value = entry.getValue().getUpdated();
                    // yes, store the nulls.  If it was deleted it should stay deleted
                    results.put(entry.getKey(), value);
                  }
                });
      }
      if (currentLayer.getNextWorldView().isEmpty()) {
        currentLayer = null;
      } else if (currentLayer.getNextWorldView().get() instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.getNextWorldView().get();
      } else {
        final Account account = currentLayer.getNextWorldView().get().get(address);
        if (account != null) {
          account
              .storageEntriesFrom(Hash.ZERO, Integer.MAX_VALUE)
              .forEach(
                  (k, v) -> {
                    if (!results.containsKey(k)) {
                      results.put(k, v.getValue());
                    }
                  });
        }
        currentLayer = null;
      }
    }
    return results;
  }

  @Override
  public Account get(final Address address) {
    // this must be iterative and lambda light because the stack may blow up
    // mainly because we don't have tail calls.
    BonsaiLayeredWorldState currentLayer = this;
    while (currentLayer != null) {
      final Optional<StateTrieAccountValue> maybeStateTrieAccount =
          currentLayer.trieLog.getAccount(address);
      final Optional<StateTrieAccountValue> maybePriorStateTrieAccount =
          currentLayer.trieLog.getPriorAccount(address);
      if (currentLayer == this && maybeStateTrieAccount.isPresent()) {
        return new BonsaiAccount(
            BonsaiLayeredWorldState.this, address, maybeStateTrieAccount.get(), false);
      } else if (maybePriorStateTrieAccount.isPresent()) {
        return new BonsaiAccount(
            BonsaiLayeredWorldState.this, address, maybePriorStateTrieAccount.get(), false);
      } else if (maybeStateTrieAccount.isPresent()) {
        return null;
      }
      if (currentLayer.getNextWorldView().isEmpty()) {
        currentLayer = null;
      } else if (currentLayer.getNextWorldView().get() instanceof BonsaiLayeredWorldState) {
        currentLayer = (BonsaiLayeredWorldState) currentLayer.getNextWorldView().get();
      } else {
        return currentLayer.getNextWorldView().get().get(address);
      }
    }
    return null;
  }

  @Override
  public Hash rootHash() {
    return worldStateRootHash;
  }

  @Override
  public Hash frontierRootHash() {
    // maybe throw?
    return rootHash();
  }

  public Hash blockHash() {
    return trieLog.getBlockHash();
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new UnsupportedOperationException("Bonsai does not support pruning and debug RPCs");
  }

  @Override
  public MutableWorldState copy() {
    final BonsaiPersistedWorldState bonsaiPersistedWorldState =
        ((BonsaiPersistedWorldState) archive.getMutable());
    return new BonsaiInMemoryWorldState(
        archive,
        new BonsaiInMemoryWorldStateKeyValueStorage(
            bonsaiPersistedWorldState.getWorldStateStorage().accountStorage,
            bonsaiPersistedWorldState.getWorldStateStorage().codeStorage,
            bonsaiPersistedWorldState.getWorldStateStorage().storageStorage,
            bonsaiPersistedWorldState.getWorldStateStorage().trieBranchStorage,
            bonsaiPersistedWorldState.getWorldStateStorage().trieLogStorage));
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    throw new UnsupportedOperationException("Layered worldState can not be persisted.");
  }

  @Override
  public WorldUpdater updater() {
    return new BonsaiWorldStateUpdater(this);
  }
}
