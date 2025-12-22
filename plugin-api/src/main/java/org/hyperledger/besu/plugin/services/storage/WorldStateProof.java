/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugin.services.storage;

import org.hyperledger.besu.datatypes.AccountValue;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Represents a cryptographic proof of a world state for a given account and its associated storage
 * in Ethereum.
 *
 * <p>The {@code WorldStateProof} provides access to:
 *
 * <ul>
 *   <li>The proof for the account node in the state trie,
 *   <li>The decoded account value (if available),
 *   <li>Proofs for each storage key belonging to the account.
 * </ul>
 *
 * The proofs are used to verify the inclusion and values of account and storage data within the
 * Ethereum world state trie.
 */
public interface WorldStateProof {

  /**
   * Returns the state trie account value if present.
   *
   * @return an {@link Optional} containing the state trie account value, or empty if not present
   */
  Optional<AccountValue> getStateTrieAccountValue();

  /**
   * Returns the list of RLP-encoded nodes comprising the account proof.
   *
   * @return a list of bytes representing the proof nodes leading to the account
   */
  List<Bytes> getAccountProof();

  /**
   * Returns the list of storage keys for which proofs are present in this world state proof.
   *
   * @return a list of storage keys
   */
  List<UInt256> getStorageKeys();

  /**
   * Returns the value stored at the given storage key. If the value is not present, returns {@link
   * UInt256#ZERO}.
   *
   * @param key the storage key
   * @return the value at the specified storage key, or zero if not present
   */
  UInt256 getStorageValue(final UInt256 key);

  /**
   * Returns the list of RLP-encoded nodes comprising the storage proof for a given key.
   *
   * @param key the storage key
   * @return a list of bytes representing the proof nodes leading to the storage value
   */
  List<Bytes> getStorageProof(final UInt256 key);
}
