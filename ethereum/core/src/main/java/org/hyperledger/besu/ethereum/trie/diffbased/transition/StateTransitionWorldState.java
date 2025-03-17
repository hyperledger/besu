/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Manages state transitions between Bonsai and Verkle world states, ensuring proper migration of
 * storage and accounts.
 *
 * <p>This class is responsible for converting and merging data structures from the Bonsai world
 * state into the Verkle world state, ensuring data consistency and preventing overwriting of
 * existing state.
 */
@SuppressWarnings("unchecked")
public class StateTransitionWorldState implements MutableWorldState, DiffBasedWorldView {

  private final BonsaiWorldState bonsaiWorldState;
  private final VerkleWorldState verkleWorldState;
  private DiffBasedWorldStateUpdateAccumulator<?> accumulator;

  private boolean isVerkleActive;
  private final long verkleTimeStamp;

  public StateTransitionWorldState(
      final BonsaiWorldState bonsaiWorldState,
      final VerkleWorldState verkleWorldState,
      final boolean isVerkleActive,
      final long verkleTimeStamp) {
    this.bonsaiWorldState = bonsaiWorldState;
    this.verkleWorldState = verkleWorldState;
    this.isVerkleActive = isVerkleActive;
    this.verkleTimeStamp = verkleTimeStamp;
    this.accumulator = loadAccumulator();
  }

  @Override
  public void announceBlockToImport(final BlockHeader blockToImport) {
    this.isVerkleActive = blockToImport.getTimestamp() >= verkleTimeStamp;
    this.accumulator = loadAccumulator();
  }

  @Override
  public Account get(final Address address) {
    if (isVerkleActive) {
      Account account = verkleWorldState.get(address);
      if (account == null) {
        account = bonsaiWorldState.get(address);
        if (account instanceof BonsaiAccount bonsaiAccount) {
          return new VerkleAccount(
              getAccumulator(),
              address,
              address.addressHash(),
              bonsaiAccount.getNonce(),
              bonsaiAccount.getBalance(),
              bonsaiAccount.getCodeSize().orElse(0L),
              bonsaiAccount.getCodeHash(),
              true);
        }
      }
      return account;
    } else {
      return bonsaiWorldState.get(address);
    }
  }

  @Override
  public Optional<Bytes> getCode(final Address address, final Hash codeHash) {
    return getWorldState().getCode(address, codeHash);
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 key) {
    return getStorageValueByStorageSlotKey(address, new StorageSlotKey(key)).orElse(UInt256.ZERO);
  }

  @Override
  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    if (isVerkleActive) {
      return verkleWorldState
          .getStorageValueByStorageSlotKey(address, storageSlotKey)
          .or(() -> bonsaiWorldState.getStorageValueByStorageSlotKey(address, storageSlotKey));
    } else {
      return bonsaiWorldState.getStorageValueByStorageSlotKey(address, storageSlotKey);
    }
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 key) {
    return getStorageValue(address, key);
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    return getWorldState().getAllAccountStorage(address, rootHash);
  }

  @Override
  public boolean isModifyingHeadWorldState() {
    return getWorldState().isModifyingHeadWorldState();
  }

  @Override
  public DiffBasedWorldStateKeyValueStorage getWorldStateStorage() {
    return getWorldState().getWorldStateStorage();
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    if (isVerkleActive) {
      verkleWorldState
          .getAccumulator()
          .importStateChangesFromSource(
              (DiffBasedWorldStateUpdateAccumulator<VerkleAccount>) accumulator);
      PatriciaToVerkleConverter.convert(bonsaiWorldState, verkleWorldState, 7);

      verkleWorldState.persist(blockHeader);

    } else {
      bonsaiWorldState
          .getAccumulator()
          .importStateChangesFromSource(
              (DiffBasedWorldStateUpdateAccumulator<BonsaiAccount>) accumulator);
      // TODO REMOVE IT TO GENERATE PRE IMAGE
      bonsaiWorldState
          .getAccumulator()
          .getAccountsToUpdate()
          .forEach(
              (address, bonsaiAccountDiffBasedValue) -> {
                PatriciaToVerkleConverter.addPreImage(Hash.hash(address), address);
              });
      bonsaiWorldState
          .getAccumulator()
          .getStorageToUpdate()
          .forEach(
              (address, bonsaiAccountDiffBasedValue) ->
                  bonsaiAccountDiffBasedValue.forEach(
                      (storageSlotKey, value) -> {
                        PatriciaToVerkleConverter.addPreImage(
                            storageSlotKey.getSlotHash(),
                            storageSlotKey.getSlotKey().orElseThrow());
                      }));
      // TODO END REMOVE IT TO GENERATE PRE IMAGE
      bonsaiWorldState.persist(blockHeader);
    }
    accumulator.reset();
  }

  @Override
  public WorldUpdater updater() {
    return getAccumulator();
  }

  @Override
  public Hash rootHash() {
    return getWorldState().rootHash();
  }

  @Override
  public Hash frontierRootHash() {
    return getWorldState().frontierRootHash();
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    return getWorldState().streamAccounts(startKeyHash, limit);
  }

  private DiffBasedWorldStateUpdateAccumulator<?> loadAccumulator() {
    if (isVerkleActive) {
      return new VerkleWorldStateUpdateAccumulator(this, verkleWorldState.getAccumulator());
    } else {
      return new BonsaiWorldStateUpdateAccumulator(this, bonsaiWorldState.getAccumulator());
    }
  }

  private DiffBasedWorldState getWorldState() {
    if (isVerkleActive) {
      return verkleWorldState;
    } else {
      return bonsaiWorldState;
    }
  }

  private DiffBasedWorldStateUpdateAccumulator<?> getAccumulator() {
    return this.accumulator;
  }
}
