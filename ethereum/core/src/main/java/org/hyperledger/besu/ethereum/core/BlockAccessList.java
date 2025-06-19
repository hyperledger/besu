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
import org.hyperledger.besu.ethereum.core.encoding.AccountCodeDiffDecoder;
import org.hyperledger.besu.ethereum.core.encoding.AccountCodeDiffEncoder;
import org.hyperledger.besu.ethereum.core.encoding.AccountNonceDiffDecoder;
import org.hyperledger.besu.ethereum.core.encoding.AccountNonceDiffEncoder;
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

public class BlockAccessList {

  private final List<AccountAccess> accountAccesses;
  private final List<AccountBalanceDiff> balanceDiffs;
  private final List<AccountCodeDiff> codeDiffs;
  private final List<AccountNonceDiff> nonceDiffs;

  public BlockAccessList(
      final List<AccountAccess> accountAccesses,
      final List<AccountBalanceDiff> balanceDiffs,
      final List<AccountCodeDiff> codeDiffs,
      final List<AccountNonceDiff> nonceDiffs) {
    this.accountAccesses = accountAccesses;
    this.balanceDiffs = balanceDiffs;
    this.codeDiffs = codeDiffs;
    this.nonceDiffs = nonceDiffs;
  }

  public List<AccountAccess> getAccountAccesses() {
    return accountAccesses;
  }

  public List<AccountBalanceDiff> getAccountBalanceDiffs() {
    return balanceDiffs;
  }

  public List<AccountCodeDiff> getAccountCodeDiffs() {
    return codeDiffs;
  }

  public List<AccountNonceDiff> getAccountNonceDiffs() {
    return nonceDiffs;
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

  public static class AccountCodeDiff {
    private final Address address;
    private final CodeChange change;

    public AccountCodeDiff(final Address address, final CodeChange change) {
      this.address = address;
      this.change = change;
    }

    public Address getAddress() {
      return address;
    }

    public CodeChange getChange() {
      return change;
    }

    public void writeTo(final RLPOutput out) {
      AccountCodeDiffEncoder.encode(this, out);
    }

    public static AccountCodeDiff readFrom(final RLPInput rlpInput) {
      return AccountCodeDiffDecoder.decode(rlpInput);
    }

    @Override
    public String toString() {
      return "AccountCodeDiff{" + "address=" + address + ", change=" + change + '}';
    }
  }

  public static class CodeChange {
    private final int txIndex;
    private final Bytes newCode;

    public CodeChange(final int txIndex, final Bytes newCode) {
      this.txIndex = txIndex;
      this.newCode = newCode;
    }

    public int getTxIndex() {
      return txIndex;
    }

    public Bytes getNewCode() {
      return newCode;
    }

    @Override
    public String toString() {
      return "CodeChange{" + "txIndex=" + txIndex + ", newCode=" + newCode + '}';
    }
  }

  public static class AccountNonceDiff {
    private final Address address;
    private final long nonceBefore;

    public AccountNonceDiff(final Address address, final long nonceBefore) {
      this.address = address;
      this.nonceBefore = nonceBefore;
    }

    public Address getAddress() {
      return address;
    }

    public long getNonceBefore() {
      return nonceBefore;
    }

    @Override
    public String toString() {
      return "AccountNonceDiff{" + "address=" + address + ", nonceBefore=" + nonceBefore + '}';
    }

    public void writeTo(final RLPOutput out) {
      AccountNonceDiffEncoder.encode(this, out);
    }

    public static AccountNonceDiff readFrom(final RLPInput rlpInput) {
      return AccountNonceDiffDecoder.decode(rlpInput);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<Hash, AccountAccessBuilder> accounts = new LinkedHashMap<>();
    private final Map<Hash, AccountBalanceDiffBuilder> changes = new LinkedHashMap<>();
    private final Map<Hash, AccountCodeDiff> codeChanges = new LinkedHashMap<>();
    private final Map<Hash, AccountNonceDiff> nonceChanges = new LinkedHashMap<>();

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

    public void accountCodeChange(final Address address, final int txIndex, final Bytes code) {
      codeChanges.put(
          address.addressHash(), new AccountCodeDiff(address, new CodeChange(txIndex, code)));
    }

    public void accountNonceDiff(final Address address, final long nonceBefore) {
      nonceChanges.putIfAbsent(address.addressHash(), new AccountNonceDiff(address, nonceBefore));
    }

    public void updateFromTransactionAccumulator(
        final WorldUpdater txnAccumulator, final int txIndex, final boolean isContractCreation) {
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
                  final BigInteger priorBalance =
                      Optional.ofNullable(value.getPrior())
                          .map(PathBasedAccount::getBalance)
                          .map(Wei::getAsBigInteger)
                          .orElse(BigInteger.ZERO);
                  final BigInteger updatedBalance =
                      Optional.ofNullable(value.getUpdated())
                          .map(PathBasedAccount::getBalance)
                          .map(Wei::getAsBigInteger)
                          .orElse(BigInteger.ZERO);
                  final BigInteger delta = updatedBalance.subtract(priorBalance);
                  if (!value.isEvmRead() && delta.compareTo(BigInteger.ZERO) != 0) {
                    this.accountBalanceChange(address, txIndex, delta);
                  }

                  final Bytes priorCode =
                      Optional.ofNullable(value.getPrior())
                          .map(PathBasedAccount::getCode)
                          .orElse(Bytes.EMPTY);
                  final Bytes updatedCode =
                      Optional.ofNullable(value.getUpdated())
                          .map(PathBasedAccount::getCode)
                          .orElse(Bytes.EMPTY);
                  if (priorCode != updatedCode) {
                    this.accountCodeChange(address, txIndex, updatedCode);
                  }

                  final long priorNonce =
                      Optional.ofNullable(value.getPrior())
                          .map(PathBasedAccount::getNonce)
                          .orElse(0L);
                  if (isContractCreation) {
                    this.accountNonceDiff(address, priorNonce);
                  }
                });
      } else {
        throw new RuntimeException(
            "Attempted to update update BAL with unexpected accumulator instance");
      }
    }

    public BlockAccessList build() {
      final List<AccountAccess> accesses =
          accounts.values().stream().map(AccountAccessBuilder::build).toList();
      final List<AccountBalanceDiff> diffs =
          changes.values().stream().map(AccountBalanceDiffBuilder::build).toList();
      final List<AccountCodeDiff> codes = new ArrayList<>(codeChanges.values());
      final List<AccountNonceDiff> nonces = new ArrayList<>(nonceChanges.values());
      return new BlockAccessList(accesses, diffs, codes, nonces);
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
