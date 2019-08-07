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
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A specific state of the world.
 *
 * <p>Note that while this interface represents an immutable view of a world state (it doesn't have
 * mutation methods), it does not guarantee in and of itself that the underlying implementation is
 * not mutable. In other words, objects implementing this interface are not guaranteed to be
 * thread-safe, though some particular implementations may provide such guarantees.
 */
public interface WorldState extends WorldView {

  /**
   * The root hash of the world state this represents.
   *
   * @return the world state root hash.
   */
  Hash rootHash();

  /**
   * A stream of all the accounts in this world state.
   *
   * @param startKeyHash The trie key at which to start iterating
   * @param limit The maximum number of results to return
   * @return a stream of all the accounts (in no particular order) contained in the world state
   *     represented by the root hash of this object at the time of the call.
   */
  Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit);

  class StreamableAccount implements AccountState {
    private final Optional<Address> address;
    private final AccountState accountState;

    public StreamableAccount(final Optional<Address> address, final AccountState accountState) {
      this.address = address;
      this.accountState = accountState;
    }

    public Optional<Address> getAddress() {
      return address;
    }

    @Override
    public Hash getAddressHash() {
      return accountState.getAddressHash();
    }

    @Override
    public long getNonce() {
      return accountState.getNonce();
    }

    @Override
    public Wei getBalance() {
      return accountState.getBalance();
    }

    @Override
    public BytesValue getCode() {
      return accountState.getCode();
    }

    @Override
    public Hash getCodeHash() {
      return accountState.getCodeHash();
    }

    @Override
    public int getVersion() {
      return accountState.getVersion();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
      return accountState.getStorageValue(key);
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
      return accountState.getOriginalStorageValue(key);
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
        final Bytes32 startKeyHash, final int limit) {
      return accountState.storageEntriesFrom(startKeyHash, limit);
    }
  }
}
