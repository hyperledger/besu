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
 *
 */
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** Represent a worldState for testing. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceTestWorldState extends DefaultMutableWorldState {

  public static class AccountMock {
    private final long nonce;
    private final Wei balance;
    private final Bytes code;
    private final Map<UInt256, UInt256> storage;

    private static Map<UInt256, UInt256> parseStorage(final Map<String, String> values) {
      final Map<UInt256, UInt256> storage = new HashMap<>();
      for (final Map.Entry<String, String> entry : values.entrySet()) {
        storage.put(UInt256.fromHexString(entry.getKey()), UInt256.fromHexString(entry.getValue()));
      }
      return storage;
    }

    public AccountMock(
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("balance") final String balance,
        @JsonProperty("storage") final Map<String, String> storage,
        @JsonProperty("code") final String code) {
      this.nonce = Long.parseUnsignedLong(nonce.startsWith("0x") ? nonce.substring(2) : nonce, 16);
      this.balance = Wei.fromHexString(balance);
      this.code = Bytes.fromHexString(code);
      this.storage = parseStorage(storage);
    }

    public long getNonce() {
      return nonce;
    }

    public Wei getBalance() {
      return balance;
    }

    public Bytes getCode() {
      return code;
    }

    public Map<UInt256, UInt256> getStorage() {
      return storage;
    }
  }

  static void insertAccount(
      final WorldUpdater updater, final Address address, final AccountMock toCopy) {
    final MutableAccount account = updater.getOrCreate(address).getMutable();
    account.setNonce(toCopy.getNonce());
    account.setBalance(toCopy.getBalance());
    account.setCode(toCopy.getCode());
    for (final Map.Entry<UInt256, UInt256> entry : toCopy.getStorage().entrySet()) {
      account.setStorageValue(entry.getKey(), entry.getValue());
    }
  }

  @JsonCreator
  public static ReferenceTestWorldState create(final Map<String, AccountMock> accounts) {
    final ReferenceTestWorldState worldState = new ReferenceTestWorldState();
    final WorldUpdater updater = worldState.updater();

    for (final Map.Entry<String, AccountMock> entry : accounts.entrySet()) {
      insertAccount(updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }

    updater.commit();
    return worldState;
  }

  public ReferenceTestWorldState() {
    super(
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  }
}
