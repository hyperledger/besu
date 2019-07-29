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
  Stream<Account> streamAccounts(Bytes32 startKeyHash, int limit);
}
