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
import org.hyperledger.besu.ethereum.mainnet.block.access.list.TransactionLevelAccessList.AccountAccessList;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

public class BlockLevelAccessList {
  private final List<AccountChanges> accountChanges;

  public BlockLevelAccessList(final List<AccountChanges> accountChanges) {
    this.accountChanges = accountChanges;
  }

  public List<AccountChanges> getAccountChanges() {
    return accountChanges;
  }

  public void writeTo(final RLPOutput out) {
    BlockAccessListEncoder.encode(this, out);
  }

  public static BlockAccessListBuilder builder() {
    return new BlockAccessListBuilder();
  }

  public record StorageChange(long txIndex, UInt256 newValue) {
    @NotNull
    @Override
    public String toString() {
      return "StorageChange{txIndex=" + txIndex + ", newValue=" + newValue + '}';
    }
  }

  public record BalanceChange(long txIndex, Bytes postBalance) {
    @NotNull
    @Override
    public String toString() {
      return "BalanceChange{txIndex=" + txIndex + ", postBalance=" + postBalance + '}';
    }
  }

  public record NonceChange(long txIndex, long newNonce) {
    @NotNull
    @Override
    public String toString() {
      return "NonceChange{txIndex=" + txIndex + ", newNonce=" + newNonce + '}';
    }
  }

  public record CodeChange(long txIndex, Bytes newCode) {
    @NotNull
    @Override
    public String toString() {
      return "CodeChange{txIndex=" + txIndex + ", newCode=" + newCode + '}';
    }
  }

  public record SlotChanges(StorageSlotKey slot, List<StorageChange> changes) {
    @NotNull
    @Override
    public String toString() {
      return "SlotChanges{slot=" + slot + ", changes=" + changes + '}';
    }
  }

  public record SlotRead(StorageSlotKey slot) {
    @NotNull
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
    @NotNull
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

    public void addTransactionLevelAccessList(final TransactionLevelAccessList txList) {

      for (Map.Entry<Address, AccountAccessList> account : txList.getAccounts().entrySet()) {
        Address address = account.getKey();
        AccountAccessList accountState = account.getValue();

        BlockAccessListBuilder.AccountBuilder builder =
            accountChangesBuilders.computeIfAbsent(
                address,
                __ -> {
                  return new AccountBuilder(address);
                });

        Wei newBalance = accountState.getAccount().getBalance();
        if (!newBalance.equals(accountState.getPrevBalance())) {
          builder.addBalanceChange(txList.getIndex(), newBalance.toBytes());
        }

        long newNonce = accountState.getAccount().getNonce();
        if (newNonce != accountState.getPrevNonce()) {
          builder.addNonceChange(txList.getIndex(), newNonce);
        }

        Bytes newCode = accountState.getAccount().getCodeHash();
        if (!newCode.equals(accountState.getPrevCodeHash())) {
          builder.addCodeChange(txList.getIndex(), newCode);
        }

        // Storage slots
        Map<UInt256, Pair<UInt256, Boolean>> slots = accountState.getSlots();
        for (Map.Entry<UInt256, Pair<UInt256, Boolean>> slotEntry : slots.entrySet()) {
          final UInt256 slotKey = slotEntry.getKey();
          final UInt256 slotValue = slotEntry.getValue().getFirst();
          final Boolean isSlotUpdated = slotEntry.getValue().getSecond();

          StorageSlotKey slotKeyObj = new StorageSlotKey(slotKey);

          if (isSlotUpdated) {
            builder.addStorageWrite(slotKeyObj, txList.getIndex(), slotValue);
          } else {
            builder.addStorageRead(slotKeyObj);
          }
        }
      }
    }

    public BlockLevelAccessList build() {

      return new BlockLevelAccessList(
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

      void addStorageWrite(final StorageSlotKey slot, final long txIndex, final UInt256 value) {
        final List<StorageChange> changes =
            slotWrites.computeIfAbsent(slot, __ -> new ArrayList<>());
        changes.add(new StorageChange(txIndex, value));
      }

      void addStorageRead(final StorageSlotKey slot) {
        if (!slotWrites.containsKey(slot)) {
          slotReads.add(slot);
        }
      }

      void addBalanceChange(final long txIndex, final Bytes postBalance) {
        balances.add(new BalanceChange(txIndex, postBalance));
      }

      void addNonceChange(final long txIndex, final long newNonce) {
        nonces.add(new NonceChange(txIndex, newNonce));
      }

      void addCodeChange(final long txIndex, final Bytes code) {
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
                                .sorted(Comparator.comparingLong(StorageChange::txIndex))
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
