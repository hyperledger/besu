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

import static java.util.Collections.emptySet;

import org.hyperledger.besu.datatypes.Address;

import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.tuweni.bytes.Bytes32;

public class GasAndAccessedState {
  final long gas;
  final Set<Address> accessListAddressSet;
  final Multimap<Address, Bytes32> accessListStorageByAddress;

  public GasAndAccessedState(
      final long gas,
      final Set<Address> accessListAddressSet,
      final Multimap<Address, Bytes32> accessedStorage) {
    this.gas = gas;
    this.accessListAddressSet = accessListAddressSet;
    this.accessListStorageByAddress = accessedStorage;
  }

  public GasAndAccessedState(final long gas) {
    this.gas = gas;
    this.accessListAddressSet = emptySet();
    this.accessListStorageByAddress = HashMultimap.create();
  }

  public long getGas() {
    return gas;
  }

  public Set<Address> getAccessListAddressSet() {
    return accessListAddressSet;
  }

  public Multimap<Address, Bytes32> getAccessListStorageByAddress() {
    return accessListStorageByAddress;
  }
}
