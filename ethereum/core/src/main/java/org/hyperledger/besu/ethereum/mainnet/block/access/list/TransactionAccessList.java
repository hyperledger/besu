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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.Eip7928AccessList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class TransactionAccessList implements Eip7928AccessList {

  private final long index;
  private final Map<Address, AccountAccessList> accounts = new ConcurrentHashMap<>();

  public TransactionAccessList(final long index) {
    this.index = index;
  }

  public long getIndex() {
    return index;
  }

  public Map<Address, AccountAccessList> getAccounts() {
    return accounts;
  }

  @Override
  public void addAccount(final Address address, final Account account) {
    // TODO: Why is null being passed here
    if (account != null) {
      accounts.putIfAbsent(address, new AccountAccessList(account));
    }
  }

  @Override
  public void addSlotAccessForAccount(
      final Address address, final UInt256 slotKey, final UInt256 slotValue) {
    accounts.get(address).addSlotAccess(slotKey, slotValue);
  }

  @Override
  public void addSlotUpdateForAccount(
      final Address address, final UInt256 slotKey, final UInt256 slotValue) {
    accounts.get(address).addSlotUpdate(slotKey, slotValue);
  }

  public static class AccountAccessList {
    private final Account account;
    private final Address address;
    private final long prevNonce;
    private final Wei prevBalance;
    private final Bytes prevCodeHash;
    private final Map<UInt256, Pair<UInt256, Boolean>> slots = new ConcurrentHashMap<>();
    private final List<UInt256> slotsRead = new ArrayList<>();

    public AccountAccessList(final Account account) {
      this.account = account;
      this.address = account.getAddress();
      this.prevNonce = account.getNonce();
      this.prevBalance = account.getBalance();
      this.prevCodeHash = account.getCodeHash();
    }

    public void addSlotUpdate(final UInt256 slotKey, final UInt256 slotValue) {
      slots.put(slotKey, new Pair<>(slotValue, true));
    }

    public void addSlotAccess(final UInt256 slotKey, final UInt256 slotValue) {
      slots.putIfAbsent(slotKey, new Pair<>(slotValue, false));
    }

    public Address getAddress() {
      return address;
    }

    public long getPrevNonce() {
      return prevNonce;
    }

    public Wei getPrevBalance() {
      return prevBalance;
    }

    public Bytes getPrevCodeHash() {
      return prevCodeHash;
    }

    public Account getAccount() {
      return account;
    }

    public List<UInt256> getSlotsRead() {
      return slotsRead;
    }

    public Map<UInt256, Pair<UInt256, Boolean>> getSlots() {
      return slots;
    }
  }
}
