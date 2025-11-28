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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessListLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.PartialBlockAccessView;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tuweni.units.bigints.UInt256;

public class AccessListTraceTracker implements AccessListLocationTracker {
  final Set<Address> touchedAccounts = new java.util.HashSet<>();
  final Map<Address, List<UInt256>> accessedSlots = new java.util.HashMap<>();

  @Override
  public PartialBlockAccessView createPartialBlockAccessView(final WorldUpdater updater) {
    return null;
  }

  @Override
  public void addTouchedAccount(final Address address) {
    touchedAccounts.add(address);
  }

  @Override
  public void addSlotAccessForAccount(final Address address, final UInt256 slotKey) {
    addTouchedAccount(address);
    accessedSlots.computeIfAbsent(address, k -> new java.util.ArrayList<>()).add(slotKey);
  }

  @Override
  public void clear() {
    touchedAccounts.clear();
    accessedSlots.clear();
  }

  public Set<Address> getTouchedAccounts() {
    return touchedAccounts;
  }
}
