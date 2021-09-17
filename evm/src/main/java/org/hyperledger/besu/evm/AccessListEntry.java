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
package org.hyperledger.besu.evm;

import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class AccessListEntry {
  private final Address address;
  private final List<Bytes32> storageKeys;

  public AccessListEntry(final Address address, final List<Bytes32> storageKeys) {
    this.address = address;
    this.storageKeys = storageKeys;
  }

  @JsonCreator
  public static AccessListEntry createAccessListEntry(
      @JsonProperty("address") final Address address,
      @JsonProperty("storageKeys") final List<String> storageKeys) {
    return new AccessListEntry(
        address, storageKeys.stream().map(Bytes32::fromHexString).collect(Collectors.toList()));
  }

  @JsonIgnore
  public Address getAddress() {
    return address;
  }

  @JsonIgnore
  public List<Bytes32> getStorageKeys() {
    return storageKeys;
  }

  @JsonProperty("address")
  public String getAddressString() {
    return address.toHexString();
  }

  @JsonProperty("storageKeys")
  public List<String> getStorageKeysString() {
    return storageKeys.stream().map(Bytes::toHexString).collect(Collectors.toList());
  }
}
