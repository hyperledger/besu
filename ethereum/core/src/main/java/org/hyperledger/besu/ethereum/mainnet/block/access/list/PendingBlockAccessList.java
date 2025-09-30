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
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.Eip7928AccessList;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.impl.ConcurrentHashSet;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class PendingBlockAccessList implements Eip7928AccessList {

  private final int blockAccessIndex;
  private final Map<Address, AccountAccessList> touchedAccounts = new ConcurrentHashMap<>();
  private Optional<BlockAccessList> maybeGeneratedBlockAccessList;

  public PendingBlockAccessList(final int blockAccessIndex) {
    this.blockAccessIndex = blockAccessIndex;
  }

  @Override
  public void clear() {
    touchedAccounts.clear();
  }

  @Override
  public void addTouchedAccount(final Address address) {
    touchedAccounts.putIfAbsent(address, new AccountAccessList(address));
  }

  @Override
  public void addSlotAccessForAccount(final Address address, final UInt256 slotKey) {
    addTouchedAccount(address);
    touchedAccounts.get(address).addSlotAccess(slotKey);
  }

  public Optional<BlockAccessList> getMaybeGeneratedBlockAccessList() {
    return maybeGeneratedBlockAccessList;
  }

  public void generateBlockAccessList(final StackedUpdater<?, ?> updater) {
    BlockAccessList.BlockAccessListBuilder balBuilder = BlockAccessList.builder();
    for (Map.Entry<Address, AccountAccessList> accountAccessListEntry :
        touchedAccounts.entrySet()) {
      final Address address = accountAccessListEntry.getKey();

      BlockAccessList.BlockAccessListBuilder.AccountBuilder accountBuilder =
          balBuilder.getAccountBuilder(address);

      if (updater.getDeletedAccountAddresses().contains(address)
          || updater.getUpdatedAccounts().stream()
              .map(UpdateTrackingAccount::getAddress)
              .filter(a -> a.equals(address))
              .findAny()
              .isEmpty()) {
        for (UInt256 slot : accountAccessListEntry.getValue().getSlots()) {
          final StorageSlotKey slotKeyObj = new StorageSlotKey(slot);
          accountBuilder.addStorageRead(slotKeyObj);
        }
        continue;
      }

      final UpdateTrackingAccount<?> account = (UpdateTrackingAccount<?>) updater.get(address);

      if (account != null) {
        final Account wrappedAccount = account.getWrappedAccount();

        if (wrappedAccount != null) {
          Wei newBalance = account.getBalance();
          Wei originalBalance = accountBuilder.getLastBalance().orElse(wrappedAccount.getBalance());
          if (!newBalance.equals(originalBalance)) {
            accountBuilder.addBalanceChange(blockAccessIndex, newBalance.toBytes());
          }

          long newNonce = account.getNonce();
          long originalNonce = accountBuilder.getLastNonce().orElse(wrappedAccount.getNonce());
          if (newNonce > 0 && newNonce > originalNonce) {
            accountBuilder.addNonceChange(blockAccessIndex, newNonce);
          }

          Bytes newCode = account.getCode();
          Bytes originalCode = accountBuilder.getLastCode().orElse(wrappedAccount.getCode());
          if (!newCode.isEmpty() && !newCode.equals(originalCode)) {
            accountBuilder.addCodeChange(blockAccessIndex, newCode);
          }
        } else {
          Wei newBalance = account.getBalance();
          if (!newBalance.isZero()) {
            accountBuilder.addBalanceChange(blockAccessIndex, newBalance.toBytes());
          }

          final long newNonce = account.getNonce();
          if (newNonce > 0) {
            accountBuilder.addNonceChange(blockAccessIndex, newNonce);
          }

          Bytes newCode = account.getCode();
          if (!newCode.isEmpty()) {
            accountBuilder.addCodeChange(blockAccessIndex, newCode);
          }
        }

        final Map<UInt256, UInt256> updatedStorage = account.getUpdatedStorage();
        final Set<UInt256> txListTouchedSlots = accountAccessListEntry.getValue().getSlots();
        for (UInt256 touchedSlot : txListTouchedSlots) {
          StorageSlotKey slotKeyObj = new StorageSlotKey(touchedSlot);

          if (updatedStorage.containsKey(touchedSlot)) {
            final UInt256 originalValue =
                accountBuilder
                    .getLastWriteValue(touchedSlot)
                    .orElse(account.getOriginalStorageValue(touchedSlot));
            final UInt256 updatedValue = updatedStorage.get(touchedSlot);

            final boolean isSet = originalValue == null;
            final boolean isReset = updatedValue == null;
            final boolean isUpdate = originalValue != null && !originalValue.equals(updatedValue);
            final boolean isWrite = isSet || isReset || isUpdate;

            if (isWrite) {
              accountBuilder.addStorageWrite(slotKeyObj, blockAccessIndex, updatedValue);
            } else {
              accountBuilder.addStorageRead(slotKeyObj);
            }
          } else {
            accountBuilder.addStorageRead(slotKeyObj);
          }
        }
      } else {
        for (UInt256 slot : accountAccessListEntry.getValue().getSlots()) {
          final StorageSlotKey slotKeyObj = new StorageSlotKey(slot);
          accountBuilder.addStorageRead(slotKeyObj);
        }
      }
    }
    maybeGeneratedBlockAccessList = Optional.of(balBuilder.build());
  }

  public static final class AccountAccessList {
    private final Address address;
    private final Set<UInt256> slots = new ConcurrentHashSet<>();

    public AccountAccessList(final Address address) {
      this.address = address;
    }

    public void addSlotAccess(final UInt256 slotKey) {
      slots.add(slotKey);
    }

    public Address getAddress() {
      return address;
    }

    public Set<UInt256> getSlots() {
      return slots;
    }

    @Override
    public String toString() {
      return "AccountAccessList{" + "address=" + address + ", slots=" + slots + '}';
    }
  }
}
