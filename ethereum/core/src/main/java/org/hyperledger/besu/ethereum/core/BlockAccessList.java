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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Maybe implementing an interface defined in plugin-api will be useful?
public class BlockAccessList {

  private static final Logger LOG = LoggerFactory.getLogger(BlockAccessList.class);

  private final List<AccountAccess> accountAccesses;

  private BlockAccessList(final List<AccountAccess> accountAccesses) {
    this.accountAccesses = accountAccesses;
  }

  public List<AccountAccess> getAccountAccesses() {
    return accountAccesses;
  }

  public static class PerTxAccess {
    private final Optional<Integer> txIndex;
    private final Optional<Bytes> valueAfter;

    private PerTxAccess(final Integer txIndex, final Bytes valueAfter) {
      this.txIndex = Optional.of(txIndex);
      this.valueAfter = Optional.of(valueAfter);
    }

    private PerTxAccess() {
      this.txIndex = Optional.empty();
      this.valueAfter = Optional.empty();
    }

    public Optional<Integer> getTxIndex() {
      return txIndex;
    }

    public Optional<Bytes> valueAfter() {
      return valueAfter;
    }
  }

  public static class SlotAccess {
    private final StorageSlotKey slot;
    private final List<PerTxAccess> accesses;

    private SlotAccess(final StorageSlotKey slot, final List<PerTxAccess> accesses) {
      this.slot = slot;
      this.accesses = accesses;
    }

    public StorageSlotKey getSlot() {
      return slot;
    }

    public List<PerTxAccess> getAccesses() {
      return accesses;
    }
  }

  public static class AccountAccess {
    private final Address address;
    private final List<SlotAccess> accesses;

    private AccountAccess(final Address address, final List<SlotAccess> accesses) {
      this.address = address;
      this.accesses = accesses;
    }

    public Address getAddress() {
      return address;
    }

    public List<SlotAccess> getAccesses() {
      return accesses;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<Hash, AccountAccessBuilder> accounts = new LinkedHashMap<>();

    public SlotAccessBuilder accessSlot(final Address address, final StorageSlotKey slot) {
      return accounts
          .computeIfAbsent(address.addressHash(), k -> new AccountAccessBuilder(address))
          .slot(slot);
    }

  public void updateFromTransactionAccumulator(final WorldUpdater txnAccumulator, final int txnIndex) {
    if (txnAccumulator instanceof PathBasedWorldStateUpdateAccumulator<?> accum) {
      accum
          .getStorageToUpdate()
          .forEach((address, slotMap) -> {
              slotMap.forEach((slotKey, value) -> {
                  var prior = value.getPrior();
                  var updated = value.getUpdated();
                  var isEvmRead = value.isEvmRead();
                  if (prior.equals(updated) && isEvmRead) {
                      this.accessSlot(address, slotKey).read();
                  } else {
                      this.accessSlot(address, slotKey).write(txnIndex, updated.toBytes());
                  }
              });
          });
    } else {
      LOG.error("Attempted to update update BAL with unexpected accumulator instance");
    }
  }

    public BlockAccessList build() {
      return new BlockAccessList(
          accounts.values().stream().map(AccountAccessBuilder::build).toList());
    }
  }

  private static class AccountAccessBuilder {
    private final Address address;
    private final Map<Address, SlotAccessBuilder> slots = new LinkedHashMap<>();

    AccountAccessBuilder(final Address address) {
      this.address = address;
    }

    public SlotAccessBuilder slot(final StorageSlotKey slot) {
      return slots.computeIfAbsent(address, s -> new SlotAccessBuilder(slot));
    }

    public AccountAccess build() {
      return new AccountAccess(
          address, slots.values().stream().map(SlotAccessBuilder::build).toList());
    }
  }

  public static class SlotAccessBuilder {
    private final StorageSlotKey slot;
    private final List<PerTxAccess> accesses = new ArrayList<>();

    SlotAccessBuilder(final StorageSlotKey slot) {
      this.slot = slot;
    }

    public SlotAccessBuilder read() {
      accesses.add(new PerTxAccess());
      return this;
    }

    public SlotAccessBuilder write(final int txIndex, final Bytes valueAfter) {
      // TODO: If there was a previous read get rid of it
      // TODO: If there was a write with the same tx index get rid of it (or maybe when building)
      accesses.add(new PerTxAccess(txIndex, valueAfter));
      return this;
    }

    public SlotAccess build() {
      return new SlotAccess(slot, List.copyOf(accesses));
    }
  }
}
