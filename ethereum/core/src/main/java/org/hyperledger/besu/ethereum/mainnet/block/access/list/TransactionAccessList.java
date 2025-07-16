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
import org.hyperledger.besu.evm.frame.Eip7928AccessList;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.impl.ConcurrentHashSet;
import org.apache.tuweni.units.bigints.UInt256;

public class TransactionAccessList implements Eip7928AccessList {

  private final int index;
  private final Map<Address, AccountAccessList> accounts = new ConcurrentHashMap<>();

  public TransactionAccessList(final int index) {
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  public Map<Address, AccountAccessList> getAccounts() {
    return accounts;
  }

  @Override
  public void clear() {
    accounts.clear();
  }

  @Override
  public void addAccount(final Address address) {
    accounts.putIfAbsent(address, new AccountAccessList(address));
  }

  @Override
  public void addSlotAccessForAccount(final Address address, final UInt256 slotKey) {
    addAccount(address);
    accounts.get(address).addSlotAccess(slotKey);
  }

  public static class AccountAccessList {
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
