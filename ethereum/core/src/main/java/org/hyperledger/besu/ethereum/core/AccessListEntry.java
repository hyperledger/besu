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
import org.hyperledger.besu.ethereum.core.json.AccessListEntryDeserializer;
import org.hyperledger.besu.ethereum.core.json.AccessListEntrySerializer;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes32;

@JsonSerialize(using = AccessListEntrySerializer.class)
@JsonDeserialize(using = AccessListEntryDeserializer.class)
public class AccessListEntry {
  private final Address address;
  private final List<Bytes32> storageKeys;

  public AccessListEntry(final Address address, final List<Bytes32> storageKeys) {

    this.address = address;
    this.storageKeys = storageKeys;
  }

  public Address getAddress() {
    return address;
  }

  public List<Bytes32> getStorageKeys() {
    return storageKeys;
  }
}
