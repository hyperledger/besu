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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;

@SuppressWarnings("unused") // reflected by GraphQL
public class AccessListEntryAdapter extends AdapterBase {
  private final AccessListEntry accessListEntry;

  public AccessListEntryAdapter(final AccessListEntry accessListEntry) {
    this.accessListEntry = accessListEntry;
  }

  public List<Bytes32> getStorageKeys() {
    final var storage = accessListEntry.storageKeys();
    return new ArrayList<>(storage);
  }

  public Optional<Address> getAddress() {
    return Optional.of(accessListEntry.address());
  }
}
