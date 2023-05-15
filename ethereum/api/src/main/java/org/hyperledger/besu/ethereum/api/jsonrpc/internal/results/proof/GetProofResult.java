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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.proof;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class GetProofResult {

  private final List<Bytes> accountProof;

  private final Address address;

  private final Wei balance;

  private final Bytes32 codeHash;

  private final long nonce;

  private final Bytes32 storageHash;

  private final List<StorageEntryProof> storageEntries;

  public GetProofResult(
      final Address address,
      final Wei balance,
      final Bytes32 codeHash,
      final long nonce,
      final Bytes32 storageHash,
      final List<Bytes> accountProof,
      final List<StorageEntryProof> storageEntries) {
    this.address = address;
    this.balance = balance;
    this.codeHash = codeHash;
    this.nonce = nonce;
    this.storageHash = storageHash;
    this.accountProof = accountProof;
    this.storageEntries = storageEntries;
  }

  public static GetProofResult buildGetProofResult(
      final Address address, final WorldStateProof worldStateProof) {

    final StateTrieAccountValue stateTrieAccountValue = worldStateProof.getStateTrieAccountValue();

    final List<StorageEntryProof> storageEntries = new ArrayList<>();
    worldStateProof
        .getStorageKeys()
        .forEach(
            key ->
                storageEntries.add(
                    new StorageEntryProof(
                        key,
                        worldStateProof.getStorageValue(key),
                        worldStateProof.getStorageProof(key))));

    return new GetProofResult(
        address,
        stateTrieAccountValue.getBalance(),
        stateTrieAccountValue.getCodeHash(),
        stateTrieAccountValue.getNonce(),
        stateTrieAccountValue.getStorageRoot(),
        worldStateProof.getAccountProof(),
        storageEntries);
  }

  @JsonGetter(value = "address")
  public String getAddress() {
    return address.toString();
  }

  @JsonGetter(value = "balance")
  public String getBalance() {
    return Quantity.create(balance);
  }

  @JsonGetter(value = "codeHash")
  public String getCodeHash() {
    return codeHash.toString();
  }

  @JsonGetter(value = "nonce")
  public String getNonce() {
    return Quantity.create(nonce);
  }

  @JsonGetter(value = "storageHash")
  public String getStorageHash() {
    return storageHash.toString();
  }

  @JsonGetter(value = "accountProof")
  public List<String> getAccountProof() {
    return accountProof.stream().map(Bytes::toString).collect(Collectors.toList());
  }

  @JsonGetter(value = "storageProof")
  public List<StorageEntryProof> getStorageProof() {
    return storageEntries;
  }
}
