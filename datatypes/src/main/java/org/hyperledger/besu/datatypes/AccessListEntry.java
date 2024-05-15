/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.datatypes;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * An access list entry as defined in EIP-2930
 *
 * @param address The Address
 * @param storageKeys List of storage keys
 */
public record AccessListEntry(Address address, List<Bytes32> storageKeys) {
  /**
   * Create access list entry.
   *
   * @param address the address
   * @param storageKeys the storage keys
   * @return the access list entry
   */
  @JsonCreator
  public static AccessListEntry createAccessListEntry(
      @JsonProperty("address") final Address address,
      @JsonProperty("storageKeys") final List<String> storageKeys) {
    return new AccessListEntry(
        address, storageKeys.stream().map(Bytes32::fromHexString).collect(Collectors.toList()));
  }

  /**
   * Gets address string.
   *
   * @return the address string in hex format
   */
  @JsonProperty("address")
  public String getAddressString() {
    return address.toHexString();
  }

  /**
   * Gets storage keys string.
   *
   * @return the list of storage keys in hex format
   */
  @JsonProperty("storageKeys")
  public List<String> getStorageKeysString() {
    return storageKeys.stream().map(Bytes::toHexString).collect(Collectors.toList());
  }
}
