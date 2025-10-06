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
package org.hyperledger.besu.ethereum.mainnet.block.access.list;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.encoding.BlockAccessListEncoder;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.TransactionAccessList.AccountAccessList;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BlockAccessList {
  private static final Set<Address> excludedAddresses =
      Set.of(Address.fromHexString("0x0000f90827f1c53a10cb7a02335b175320002935"));
  private final List<AccountChanges> accountChanges;

  public BlockAccessList(final List<AccountChanges> accountChanges) {
    this.accountChanges = accountChanges;
  }

  public List<AccountChanges> getAccountChanges() {
    return accountChanges;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BlockAccessList)) {
      return false;
    }
    final BlockAccessList that = (BlockAccessList) o;
    return Objects.equals(accountChanges, that.accountChanges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountChanges);
  }

  public void writeTo(final RLPOutput out) {
    BlockAccessListEncoder.encode(this, out);
  }

  public static BlockAccessListBuilder builder() {
    return new BlockAccessListBuilder();
  }

  @Override
  public String toString() {
    return "BlockAccessList{" + "accountChanges=" + accountChanges + '}';
  }

  public record StorageChange(int txIndex, UInt256 newValue) {
    @Override
    public String toString() {
      return "StorageChange{txIndex=" + txIndex + ", newValue=" + newValue + '}';
    }
  }

  public record BalanceChange(int txIndex, Bytes postBalance) {
    @Override
    public String toString() {
      return "BalanceChange{txIndex=" + txIndex + ", postBalance=" + postBalance + '}';
    }
  }

  public record NonceChange(int txIndex, long newNonce) {
    @Override
    public String toString() {
      return "NonceChange{txIndex=" + txIndex + ", newNonce=" + newNonce + '}';
    }
  }

  public record CodeChange(int txIndex, Bytes newCode) {
    @Override
    public String toString() {
      return "CodeChange{txIndex=" + txIndex + ", newCode=" + newCode + '}';
    }
  }

  public record SlotChanges(StorageSlotKey slot, List<StorageChange> changes) {
    @Override
    public String toString() {
      return "SlotChanges{slot=" + slot + ", changes=" + changes + '}';
    }
  }

  public record SlotRead(StorageSlotKey slot) {
    @Override
    public String toString() {
      return "SlotRead{slot=" + slot + '}';
    }
  }

  public record AccountChanges(
      Address address,
      List<SlotChanges> storageChanges,
      List<SlotRead> storageReads,
      List<BalanceChange> balanceChanges,
      List<NonceChange> nonceChanges,
      List<CodeChange> codeChanges) {
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

  public static class BlockAccessListBuilder {
    final Map<Address, AccountBuilder> accountChangesBuilders = new HashMap<>();

    public void addTransactionLevelAccessList(
        final TransactionAccessList txList, final StackedUpdater<?, ?> updater) {
      for (Map.Entry<Address, AccountAccessList> accountAccessListEntry :
          txList.getAccounts().entrySet()) {
        final Address address = accountAccessListEntry.getKey();

        if (excludedAddresses.contains(address)) {
          continue;
        }

        BlockAccessListBuilder.AccountBuilder builder =
            accountChangesBuilders.computeIfAbsent(
                address,
                __ -> {
                  return new AccountBuilder(address);
                });

        if (updater.getDeletedAccountAddresses().contains(address)
            || updater.getUpdatedAccounts().stream()
                .map(s -> s.getAddress())
                .filter(a -> a.equals(address))
                .findAny()
                .isEmpty()) {
          for (UInt256 slot : accountAccessListEntry.getValue().getSlots()) {
            final StorageSlotKey slotKeyObj = new StorageSlotKey(slot);
            builder.addStorageRead(slotKeyObj);
          }
          continue;
        }

        final UpdateTrackingAccount<?> account = (UpdateTrackingAccount<?>) updater.get(address);

        if (account != null) {
          final Account wrappedAccount = account.getWrappedAccount();

          if (wrappedAccount != null) {
            Wei newBalance = account.getBalance();
            Wei originalBalance = builder.getLastBalance().orElse(wrappedAccount.getBalance());
            if (!newBalance.equals(originalBalance)) {
              builder.addBalanceChange(txList.getIndex(), newBalance.toBytes());
            }

            long newNonce = account.getNonce();
            long originalNonce = builder.getLastNonce().orElse(wrappedAccount.getNonce());
            if (newNonce > 0 && newNonce > originalNonce) {
              builder.addNonceChange(txList.getIndex(), newNonce);
            }

            Bytes newCode = account.getCode();
            Bytes originalCode = builder.getLastCode().orElse(wrappedAccount.getCode());
            if (!newCode.isEmpty() && !newCode.isZero() && !newCode.equals(originalCode)) {
              builder.addCodeChange(txList.getIndex(), newCode);
            }
          } else {
            Wei newBalance = account.getBalance();
            if (!newBalance.isZero()) {
              builder.addBalanceChange(txList.getIndex(), newBalance.toBytes());
            }

            Bytes newCode = account.getCode();
            if (!newCode.isEmpty() && !newCode.isZero()) {
              long newNonce = account.getNonce();
              builder.addCodeChange(txList.getIndex(), newCode);
              builder.addNonceChange(txList.getIndex(), newNonce);
            }
          }

          final Map<UInt256, UInt256> updatedStorage = account.getUpdatedStorage();
          final Set<UInt256> txListTouchedSlots = accountAccessListEntry.getValue().getSlots();
          for (UInt256 touchedSlot : txListTouchedSlots) {
            StorageSlotKey slotKeyObj = new StorageSlotKey(touchedSlot);

            if (updatedStorage.containsKey(touchedSlot)) {
              final UInt256 originalValue =
                  builder
                      .getLastWriteValue(touchedSlot)
                      .orElse(account.getOriginalStorageValue(touchedSlot));
              final UInt256 updatedValue = updatedStorage.get(touchedSlot);

              final boolean isSet = originalValue == null;
              final boolean isReset = updatedValue == null;
              final boolean isUpdate =
                  originalValue == null ? false : !originalValue.equals(updatedValue);
              final boolean isWrite = isSet || isReset || isUpdate;

              if (isWrite) {
                builder.addStorageWrite(slotKeyObj, txList.getIndex(), updatedValue);
              } else {
                builder.addStorageRead(slotKeyObj);
              }
            } else {
              builder.addStorageRead(slotKeyObj);
            }
          }
        } else {
          for (UInt256 slot : accountAccessListEntry.getValue().getSlots()) {
            final StorageSlotKey slotKeyObj = new StorageSlotKey(slot);
            builder.addStorageRead(slotKeyObj);
          }
        }
      }
    }

    public BlockAccessList build() {

      return new BlockAccessList(
          accountChangesBuilders.values().stream()
              .map(AccountBuilder::build)
              .sorted(Comparator.comparing(ac -> ac.address().toUnprefixedHexString()))
              .toList());
    }

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

      Optional<UInt256> getLastWriteValue(final UInt256 slot) {
        final StorageSlotKey slotKeyObj = new StorageSlotKey(slot);
        final List<StorageChange> storageChanges = this.slotWrites.get(slotKeyObj);
        if (storageChanges != null && !storageChanges.isEmpty()) {
          return Optional.of(storageChanges.getLast().newValue());
        } else {
          return Optional.empty();
        }
      }

      Optional<Wei> getLastBalance() {
        if (this.balances.isEmpty()) {
          return Optional.empty();
        }
        final BalanceChange balanceChange = this.balances.getLast();
        if (balanceChange != null) {
          return Optional.of(Wei.fromHexString(balanceChange.postBalance().toHexString()));
        } else {
          return Optional.empty();
        }
      }

      Optional<Long> getLastNonce() {
        if (this.nonces.isEmpty()) {
          return Optional.empty();
        }
        final NonceChange nonceChange = this.nonces.getLast();
        if (nonceChange != null) {
          return Optional.of(nonceChange.newNonce());
        } else {
          return Optional.empty();
        }
      }

      Optional<Bytes> getLastCode() {
        if (this.codes.isEmpty()) {
          return Optional.empty();
        }
        final CodeChange codeChange = this.codes.getLast();
        if (codeChange != null) {
          return Optional.of(codeChange.newCode());
        } else {
          return Optional.empty();
        }
      }

      void addStorageWrite(final StorageSlotKey slot, final int txIndex, final UInt256 value) {
        final List<StorageChange> changes =
            slotWrites.computeIfAbsent(slot, __ -> new ArrayList<>());
        slotReads.remove(slot);
        changes.add(new StorageChange(txIndex, value));
      }

      void addStorageRead(final StorageSlotKey slot) {
        if (!slotWrites.containsKey(slot)) {
          slotReads.add(slot);
        }
      }

      void addBalanceChange(final int txIndex, final Bytes postBalance) {
        balances.add(new BalanceChange(txIndex, postBalance));
      }

      void addNonceChange(final int txIndex, final long newNonce) {
        nonces.add(new NonceChange(txIndex, newNonce));
      }

      void addCodeChange(final int txIndex, final Bytes code) {
        codes.add(new CodeChange(txIndex, code));
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
                                .sorted(Comparator.comparingInt(StorageChange::txIndex))
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
            balances.stream().sorted(Comparator.comparingLong(BalanceChange::txIndex)).toList(),
            nonces.stream().sorted(Comparator.comparingDouble(NonceChange::txIndex)).toList(),
            codes.stream().sorted(Comparator.comparingLong(CodeChange::txIndex)).toList());
      }
    }
  }
}
