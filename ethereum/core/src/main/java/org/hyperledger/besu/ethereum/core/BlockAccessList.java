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
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.encoding.BlockAccessListDecoder;
import org.hyperledger.besu.ethereum.core.encoding.BlockAccessListEncoder;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BlockAccessList {
  private final List<AccountChanges> accountChanges;

  public BlockAccessList(final List<AccountChanges> accountChanges) {
    this.accountChanges = accountChanges;
  }

  public List<AccountChanges> getAccountChanges() {
    return accountChanges;
  }

  public void writeTo(final RLPOutput out) {
    BlockAccessListEncoder.encode(this, out);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BlockAccessList readFrom(final RLPInput in) {
    return BlockAccessListDecoder.decode(in);
  }

  public static class StorageChange {
    private final int txIndex;
    private final UInt256 newValue;

    public StorageChange(final int txIndex, final UInt256 newValue) {
      this.txIndex = txIndex;
      this.newValue = newValue;
    }

    public int getTxIndex() {
      return txIndex;
    }

    public UInt256 getNewValue() {
      return newValue;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final StorageChange that)) return false;
      return txIndex == that.txIndex && newValue.equals(that.newValue);
    }

    @Override
    public int hashCode() {
      return 31 * txIndex + newValue.hashCode();
    }

    @Override
    public String toString() {
      return "StorageChange{txIndex=" + txIndex + ", newValue=" + newValue + '}';
    }
  }

  public static class BalanceChange {
    private final int txIndex;
    private final Bytes postBalance;

    public BalanceChange(final int txIndex, final Bytes postBalance) {
      this.txIndex = txIndex;
      this.postBalance = postBalance;
    }

    public int getTxIndex() {
      return txIndex;
    }

    public Bytes getPostBalance() {
      return postBalance;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final BalanceChange that)) return false;
      return txIndex == that.txIndex && postBalance.equals(that.postBalance);
    }

    @Override
    public int hashCode() {
      return 31 * txIndex + postBalance.hashCode();
    }

    @Override
    public String toString() {
      return "BalanceChange{txIndex=" + txIndex + ", postBalance=" + postBalance + '}';
    }
  }

  public static class NonceChange {
    private final int txIndex;
    private final long newNonce;

    public NonceChange(final int txIndex, final long newNonce) {
      this.txIndex = txIndex;
      this.newNonce = newNonce;
    }

    public int getTxIndex() {
      return txIndex;
    }

    public long getNewNonce() {
      return newNonce;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final NonceChange that)) return false;
      return txIndex == that.txIndex && newNonce == that.newNonce;
    }

    @Override
    public int hashCode() {
      return 31 * txIndex + Long.hashCode(newNonce);
    }

    @Override
    public String toString() {
      return "NonceChange{txIndex=" + txIndex + ", newNonce=" + newNonce + '}';
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
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final CodeChange that)) return false;
      return txIndex == that.txIndex && newCode.equals(that.newCode);
    }

    @Override
    public int hashCode() {
      return 31 * txIndex + newCode.hashCode();
    }

    @Override
    public String toString() {
      return "CodeChange{txIndex=" + txIndex + ", newCode=" + newCode + '}';
    }
  }

  public static class SlotChanges {
    private final StorageSlotKey slot;
    private final List<StorageChange> changes;

    public SlotChanges(final StorageSlotKey slot, final List<StorageChange> changes) {
      this.slot = slot;
      this.changes = changes;
    }

    public StorageSlotKey getSlot() {
      return slot;
    }

    public List<StorageChange> getChanges() {
      return changes;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final SlotChanges that)) return false;
      return slot.equals(that.slot) && changes.equals(that.changes);
    }

    @Override
    public int hashCode() {
      return 31 * slot.hashCode() + changes.hashCode();
    }

    @Override
    public String toString() {
      return "SlotChanges{slot=" + slot + ", changes=" + changes + '}';
    }
  }

  public static class SlotRead {
    private final StorageSlotKey slot;

    public SlotRead(final StorageSlotKey slot) {
      this.slot = slot;
    }

    public StorageSlotKey getSlot() {
      return slot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final SlotRead that)) return false;
      return slot.equals(that.slot);
    }

    @Override
    public int hashCode() {
      return slot.hashCode();
    }
  }

  public static class AccountChanges {
    private final Address address;
    private final List<SlotChanges> storageChanges;
    private final List<SlotRead> storageReads;
    private final List<BalanceChange> balanceChanges;
    private final List<NonceChange> nonceChanges;
    private final List<CodeChange> codeChanges;

    public AccountChanges(
        final Address address,
        final List<SlotChanges> storageChanges,
        final List<SlotRead> storageReads,
        final List<BalanceChange> balanceChanges,
        final List<NonceChange> nonceChanges,
        final List<CodeChange> codeChanges) {
      this.address = address;
      this.storageChanges = storageChanges;
      this.storageReads = storageReads;
      this.balanceChanges = balanceChanges;
      this.nonceChanges = nonceChanges;
      this.codeChanges = codeChanges;
    }

    public Address getAddress() {
      return address;
    }

    public List<SlotChanges> getStorageChanges() {
      return storageChanges;
    }

    public List<SlotRead> getStorageReads() {
      return storageReads;
    }

    public List<BalanceChange> getBalanceChanges() {
      return balanceChanges;
    }

    public List<NonceChange> getNonceChanges() {
      return nonceChanges;
    }

    public List<CodeChange> getCodeChanges() {
      return codeChanges;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof final AccountChanges that)) return false;
      return address.equals(that.address)
          && storageChanges.equals(that.storageChanges)
          && storageReads.equals(that.storageReads)
          && balanceChanges.equals(that.balanceChanges)
          && nonceChanges.equals(that.nonceChanges)
          && codeChanges.equals(that.codeChanges);
    }

    @Override
    public int hashCode() {
      int result = address.hashCode();
      result = 31 * result + storageChanges.hashCode();
      result = 31 * result + storageReads.hashCode();
      result = 31 * result + balanceChanges.hashCode();
      result = 31 * result + nonceChanges.hashCode();
      result = 31 * result + codeChanges.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "AccountChanges{"
          + "address="
          + address
          + ", storageChanges="
          + storageChanges
          + ", storageReads="
          + storageReads
          + ", balanceChanges="
          + balanceChanges
          + ", nonceChanges="
          + nonceChanges
          + ", codeChanges="
          + codeChanges
          + '}';
    }
  }

  public static class Builder {
    private static class AccountBuilder {
      final Address address;
      final Map<StorageSlotKey, List<StorageChange>> slotWrites = new TreeMap<>();
      final Set<StorageSlotKey> slotReads = new TreeSet<>();
      final List<BalanceChange> balances = new ArrayList<>();
      final List<NonceChange> nonces = new ArrayList<>();
      final List<CodeChange> codes = new ArrayList<>();

      AccountBuilder(final Address address) {
        this.address = address;
      }

      void addStorageWrite(final StorageSlotKey slot, final int txIndex, final UInt256 value) {
        // TODO: This is a temporary workaround for having to use block-level accumulator instead of
        // transaction-level
        final List<StorageChange> changes =
            slotWrites.computeIfAbsent(slot, __ -> new ArrayList<>());
        changes.removeIf(c -> c.getTxIndex() == txIndex);
        if (changes.isEmpty() || !changes.getLast().getNewValue().equals(value)) {
          changes.add(new StorageChange(txIndex, value));
        }
      }

      void addStorageRead(final StorageSlotKey slot) {
        if (!slotWrites.containsKey(slot)) {
          slotReads.add(slot);
        }
      }

      void addBalanceChange(final int txIndex, final Bytes postBalance) {
        // TODO: This is a temporary workaround for having to use block-level accumulator instead of
        // transaction-level
        balances.removeIf(b -> b.getTxIndex() == txIndex);
        if (balances.isEmpty() || !balances.getLast().getPostBalance().equals(postBalance)) {
          balances.add(new BalanceChange(txIndex, postBalance));
        }
      }

      void addNonceChange(final int txIndex, final long newNonce) {
        // TODO: This is a temporary workaround for having to use block-level accumulator instead of
        // transaction-level
        nonces.removeIf(n -> n.getTxIndex() == txIndex);
        if (nonces.isEmpty() || nonces.getLast().getNewNonce() != newNonce) {
          nonces.add(new NonceChange(txIndex, newNonce));
        }
      }

      void addCodeChange(final int txIndex, final Bytes code) {
        // TODO: This is a temporary workaround for having to use block-level accumulator instead of
        // transaction-level
        codes.removeIf(c -> c.getTxIndex() == txIndex);
        if (codes.isEmpty() || !codes.getLast().getNewCode().equals(code)) {
          codes.add(new CodeChange(txIndex, code));
        }
      }

      AccountChanges build() {
        final List<SlotChanges> slotChanges =
            slotWrites.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getSlotKey().orElseThrow().toBytes()))
                .map(
                    e ->
                        new SlotChanges(
                            e.getKey(),
                            e.getValue().stream()
                                .sorted(Comparator.comparingInt(StorageChange::getTxIndex))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());

        final List<SlotRead> reads =
            slotReads.stream()
                .sorted(Comparator.comparing(e -> e.getSlotKey().orElseThrow().toBytes()))
                .map(SlotRead::new)
                .collect(Collectors.toList());

        return new AccountChanges(
            address,
            slotChanges,
            reads,
            balances.stream().sorted(Comparator.comparingInt(BalanceChange::getTxIndex)).toList(),
            nonces.stream().sorted(Comparator.comparingInt(NonceChange::getTxIndex)).toList(),
            codes.stream().sorted(Comparator.comparingInt(CodeChange::getTxIndex)).toList());
      }
    }

    private final Map<Address, AccountBuilder> builders = new TreeMap<>();

    private AccountBuilder forAddress(final Address addr) {
      return builders.computeIfAbsent(addr, AccountBuilder::new);
    }

    public void storageWrite(
        final Address addr, final StorageSlotKey slot, final int txIndex, final UInt256 newValue) {
      forAddress(addr).addStorageWrite(slot, txIndex, newValue);
    }

    public void storageRead(final Address addr, final StorageSlotKey slot) {
      forAddress(addr).addStorageRead(slot);
    }

    public void balanceChange(final Address addr, final int txIndex, final Bytes postBalance) {
      forAddress(addr).addBalanceChange(txIndex, postBalance);
    }

    public void nonceChange(final Address addr, final int txIndex, final long newNonce) {
      forAddress(addr).addNonceChange(txIndex, newNonce);
    }

    public void codeChange(final Address addr, final int txIndex, final Bytes code) {
      forAddress(addr).addCodeChange(txIndex, code);
    }

    public BlockAccessList build() {
      return new BlockAccessList(
          builders.values().stream()
              .map(AccountBuilder::build)
              .sorted(Comparator.comparing(ac -> ac.getAddress().toUnprefixedHexString()))
              .toList());
    }

    public void updateFromTransactionAccumulator(
        final WorldUpdater txnAccumulator, final int txIndex, final boolean isContractCreation) {

      if (!(txnAccumulator instanceof final PathBasedWorldStateUpdateAccumulator<?> accum)) {
        throw new IllegalArgumentException("Expected PathBasedWorldStateUpdateAccumulator");
      }

      accum
          .getStorageToUpdate()
          .forEach(
              (address, slotMap) -> {
                slotMap.forEach(
                    (slotKey, value) -> {
                      final UInt256 prior =
                          Optional.ofNullable(value.getPrior()).orElse(UInt256.ZERO);
                      final UInt256 updated =
                          Optional.ofNullable(value.getUpdated()).orElse(UInt256.ZERO);
                      if (!prior.equals(updated)) {
                        this.storageWrite(address, slotKey, txIndex, updated);
                      } else if (value.isEvmRead()) {
                        this.storageRead(address, slotKey);
                      }
                    });
              });

      accum
          .getAccountsToUpdate()
          .forEach(
              (address, value) -> {
                final PathBasedAccount prior = value.getPrior();
                final PathBasedAccount updated = value.getUpdated();

                final BigInteger priorBalance =
                    prior != null ? prior.getBalance().getAsBigInteger() : BigInteger.ZERO;
                final BigInteger postBalance =
                    updated != null ? updated.getBalance().getAsBigInteger() : BigInteger.ZERO;

                if (priorBalance.compareTo(postBalance) != 0) {
                  this.balanceChange(address, txIndex, Wei.of(postBalance).toBytes());
                }

                final Bytes priorCode = prior != null ? prior.getCode() : Bytes.EMPTY;
                final Bytes postCode = updated != null ? updated.getCode() : Bytes.EMPTY;
                if (!priorCode.equals(postCode) && !postCode.isEmpty()) {
                  this.codeChange(address, txIndex, postCode);
                }

                if (isContractCreation && prior != null && updated != null) {
                  final long priorNonce = prior.getNonce();
                  final long postNonce = updated.getNonce();
                  if (postNonce > priorNonce) {
                    this.nonceChange(address, txIndex, postNonce);
                  }
                }
              });
    }
  }
}
