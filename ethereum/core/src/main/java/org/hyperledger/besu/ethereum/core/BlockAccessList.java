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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.encoding.AccountAccessDecoder;
import org.hyperledger.besu.ethereum.core.encoding.AccountAccessEncoder;
import org.hyperledger.besu.ethereum.core.encoding.AccountBalanceDiffDecoder;
import org.hyperledger.besu.ethereum.core.encoding.AccountBalanceDiffEncoder;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockAccessList {

  private static final Logger LOG = LoggerFactory.getLogger(BlockAccessList.class);

  private final List<AccountAccess> accountAccesses;
  private final List<AccountBalanceDiff> balanceDiffs;

  public BlockAccessList(
      final List<AccountAccess> accountAccesses, final List<AccountBalanceDiff> balanceDiffs) {
    this.accountAccesses = accountAccesses;
    this.balanceDiffs = balanceDiffs;
  }

  public List<AccountAccess> getAccountAccesses() {
    return accountAccesses;
  }

  public List<AccountBalanceDiff> getAccountBalanceDiffs() {
    return balanceDiffs;
  }

  @Override
  public String toString() {
    return "BlockAccessList{"
        + "accountAccesses="
        + accountAccesses
        + ", balanceDiffs="
        + balanceDiffs
        + '}';
  }

  public static class PerTxAccess {
    private final Integer txIndex;
    private final Optional<Bytes> valueAfter;

    public PerTxAccess(final Integer txIndex, final Optional<Bytes> valueAfter) {
      this.txIndex = txIndex;
      this.valueAfter = valueAfter;
    }

    public Integer getTxIndex() {
      return txIndex;
    }

    public Optional<Bytes> getValueAfter() {
      return valueAfter;
    }

    @Override
    public String toString() {
      return "PerTxAccess{" + "txIndex=" + txIndex + ", valueAfter=" + valueAfter + '}';
    }
  }

  public static class SlotAccess {
    private final StorageSlotKey slot;
    private final List<PerTxAccess> accesses;

    public SlotAccess(final StorageSlotKey slot, final List<PerTxAccess> accesses) {
      this.slot = slot;
      this.accesses = accesses;
    }

    public StorageSlotKey getSlot() {
      return slot;
    }

    public List<PerTxAccess> getPerTxAccesses() {
      return accesses;
    }

    @Override
    public String toString() {
      return "SlotAccess{" + "slot=" + slot + ", accesses=" + accesses + '}';
    }
  }

  public static class AccountAccess {
    private final Address address;
    private final List<SlotAccess> accesses;

    public AccountAccess(final Address address, final List<SlotAccess> accesses) {
      this.address = address;
      this.accesses = accesses;
    }

    public Address getAddress() {
      return address;
    }

    public List<SlotAccess> getSlotAccesses() {
      return accesses;
    }

    public void writeTo(final RLPOutput out) {
      AccountAccessEncoder.encode(this, out);
    }

    public static AccountAccess readFrom(final RLPInput rlpInput) {
      return AccountAccessDecoder.decode(rlpInput);
    }

    @Override
    public String toString() {
      return "AccountAccess{" + "address=" + address + ", accesses=" + accesses + '}';
    }
  }

  public static class BalanceChange {
    private final Integer txIndex;
    private final BigInteger delta;

    public BalanceChange(final int txIndex, final BigInteger delta) {
      this.txIndex = txIndex;
      this.delta = delta;
    }

    public Integer getTxIndex() {
      return txIndex;
    }

    public BigInteger getDelta() {
      return delta;
    }

    @Override
    public String toString() {
      return "BalanceChange{" + "txIndex=" + txIndex + ", delta=" + delta + '}';
    }
  }

  public static class AccountBalanceDiff {
    private final Address address;
    private final List<BalanceChange> changes;

    public AccountBalanceDiff(final Address address, final List<BalanceChange> changes) {
      this.address = address;
      this.changes = changes;
    }

    public Address getAddress() {
      return address;
    }

    public List<BalanceChange> getBalanceChanges() {
      return changes;
    }

    public void writeTo(final RLPOutput out) {
      AccountBalanceDiffEncoder.encode(this, out);
    }

    public static AccountBalanceDiff readFrom(final RLPInput rlpInput) {
      return AccountBalanceDiffDecoder.decode(rlpInput);
    }

    @Override
    public String toString() {
      return "AccountBalanceDiff{" + "address=" + address + ", changes=" + changes + '}';
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<Hash, AccountAccessBuilder> accounts = new LinkedHashMap<>();
    private final Map<Hash, AccountBalanceDiffBuilder> changes = new LinkedHashMap<>();

    public SlotAccessBuilder accessSlot(final Address address, final StorageSlotKey slot) {
      return accounts
          .computeIfAbsent(address.addressHash(), k -> new AccountAccessBuilder(address))
          .slot(slot);
    }

    public void accountBalanceChange(
        final Address address, final int txIndex, final BigInteger delta) {
      changes
          .computeIfAbsent(address.addressHash(), k -> new AccountBalanceDiffBuilder(address))
          .addBalanceChange(txIndex, delta);
    }

    public void updateFromTransactionAccumulator(
        final WorldUpdater txnAccumulator, final int txIndex) {
      if (txnAccumulator instanceof PathBasedWorldStateUpdateAccumulator<?> accum) {
        accum
            .getStorageToUpdate()
            .forEach(
                (address, slotMap) -> {
                  slotMap.forEach(
                      (slotKey, value) -> {
                        final UInt256 prior = value.getPrior();
                        final UInt256 updated = value.getUpdated();
                        final boolean isEvmRead = value.isEvmRead();
                        final boolean areEqual =
                            ((prior == null && updated == null)
                                || (prior != null && prior.equals(updated)));
                        if (areEqual && isEvmRead) {
                          this.accessSlot(address, slotKey).read();
                        } else {
                          this.accessSlot(address, slotKey)
                              .write(
                                  txIndex,
                                  Optional.ofNullable(updated).orElse(UInt256.ZERO).toBytes());
                        }
                      });
                });

        accum
            .getAccountsToUpdate()
            .forEach(
                (address, value) -> {
                  final BigInteger prior =
                      Optional.ofNullable(value.getPrior())
                          .map(PathBasedAccount::getBalance)
                          .map(Wei::getAsBigInteger)
                          .orElse(BigInteger.ZERO);
                  final BigInteger updated =
                      Optional.ofNullable(value.getUpdated())
                          .map(PathBasedAccount::getBalance)
                          .map(Wei::getAsBigInteger)
                          .orElse(BigInteger.ZERO);
                  final BigInteger delta = updated.subtract(prior);
                  if (!value.isEvmRead() && delta.compareTo(BigInteger.ZERO) > 0) {
                    this.accountBalanceChange(address, txIndex, delta);
                  }
                });
      } else {
        LOG.error("Attempted to update update BAL with unexpected accumulator instance");
      }
    }

    public BlockAccessList build() {
      final List<AccountAccess> accesses =
          accounts.values().stream().map(AccountAccessBuilder::build).toList();
      final List<AccountBalanceDiff> diffs =
          changes.values().stream().map(AccountBalanceDiffBuilder::build).toList();
      return new BlockAccessList(accesses, diffs);
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
      return this;
    }

    public SlotAccessBuilder write(final int txIndex, final Bytes valueAfter) {
      accesses.removeIf(access -> access.getTxIndex().equals(txIndex));
      accesses.add(new PerTxAccess(txIndex, Optional.of(valueAfter)));
      return this;
    }

    public SlotAccess build() {
      return new SlotAccess(slot, List.copyOf(accesses));
    }
  }

  public static class AccountBalanceDiffBuilder {
    private final Address address;
    private final List<BalanceChange> changes = new ArrayList<>();

    public AccountBalanceDiffBuilder(final Address address) {
      this.address = address;
    }

    public void addBalanceChange(final int txIndex, final BigInteger delta) {
      changes.add(new BalanceChange(txIndex, delta));
    }

    public AccountBalanceDiff build() {
      return new AccountBalanceDiff(address, List.copyOf(changes));
    }
  }
}
