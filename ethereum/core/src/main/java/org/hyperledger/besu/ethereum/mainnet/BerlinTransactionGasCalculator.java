/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.AccessListEntry;
import org.hyperledger.besu.ethereum.core.GasAndAccessedState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.Gas;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.tuweni.bytes.Bytes32;

public class BerlinTransactionGasCalculator extends IstanbulTransactionGasCalculator {

  // new constants for EIP-2929
  private static final Gas ACCESS_LIST_ADDRESS_COST = Gas.of(2400);
  protected static final Gas ACCESS_LIST_STORAGE_COST = Gas.of(1900);

  @Override
  public GasAndAccessedState transactionIntrinsicGasCostAndAccessedState(
      final Transaction transaction) {
    // As per https://eips.ethereum.org/EIPS/eip-2930
    final List<AccessListEntry> accessList = transaction.getAccessList().orElse(emptyList());

    long accessedStorageCount = 0;
    final Set<Address> accessedAddresses = new HashSet<>();
    final Multimap<Address, Bytes32> accessedStorage = HashMultimap.create();

    for (final AccessListEntry accessListEntry : accessList) {
      final Address address = accessListEntry.getAddress();

      accessedAddresses.add(address);
      for (final Bytes32 storageKeyBytes : accessListEntry.getStorageKeysBytes()) {
        accessedStorage.put(address, storageKeyBytes);
        ++accessedStorageCount;
      }
    }

    return new GasAndAccessedState(
        super.transactionIntrinsicGasCostAndAccessedState(transaction)
            .getGas()
            .plus(ACCESS_LIST_ADDRESS_COST.times(accessList.size()))
            .plus(ACCESS_LIST_STORAGE_COST.times(accessedStorageCount)),
        accessedAddresses,
        accessedStorage);
  }
}
