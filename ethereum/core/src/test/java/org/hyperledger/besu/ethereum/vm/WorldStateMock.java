/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represent a mock worldState for testing. */
public class WorldStateMock extends DefaultMutableWorldState {

  public static class AccountMock {
    private final long nonce;
    private final Wei balance;
    private final BytesValue code;
    private final int version;
    private final Map<UInt256, UInt256> storage;

    private static final Map<UInt256, UInt256> parseStorage(final Map<String, String> values) {
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
        @JsonProperty("code") final String code,
        @JsonProperty("version") final String version) {
      this.nonce = Long.decode(nonce);
      this.balance = Wei.fromHexString(balance);
      this.code = BytesValue.fromHexString(code);
      this.storage = parseStorage(storage);
      if (version != null) {
        this.version = Integer.decode(version);
      } else {
        this.version = 0;
      }
    }

    public long getNonce() {
      return nonce;
    }

    public Wei getBalance() {
      return balance;
    }

    public BytesValue getCode() {
      return code;
    }

    public int getVersion() {
      return version;
    }

    public Map<UInt256, UInt256> getStorage() {
      return storage;
    }
  }

  public static void insertAccount(
      final WorldUpdater updater, final Address address, final AccountMock toCopy) {
    final MutableAccount account = updater.getOrCreate(address);
    account.setNonce(toCopy.getNonce());
    account.setBalance(toCopy.getBalance());
    account.setCode(toCopy.getCode());
    account.setVersion(toCopy.getVersion());
    for (final Map.Entry<UInt256, UInt256> entry : toCopy.getStorage().entrySet()) {
      account.setStorageValue(entry.getKey(), entry.getValue());
    }
  }

  @JsonCreator
  public static WorldStateMock create(final Map<String, AccountMock> accounts) {
    final WorldStateMock worldState = new WorldStateMock();
    final WorldUpdater updater = worldState.updater();

    for (final Map.Entry<String, AccountMock> entry : accounts.entrySet()) {
      insertAccount(updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }

    updater.commit();
    return worldState;
  }

  private WorldStateMock() {
    super(
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  }
}
