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
package org.hyperledger.besu.ethereum.worldstate.writesink;

import org.hyperledger.besu.datatypes.Hash;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public final class NoOpWorldStateWriteSink implements WorldStateWriteSink {

  @Override
  public void putAccountInfoState(final Hash addressHash, final Bytes accountValue) {
    // no-op
  }

  @Override
  public void removeAccountInfoState(final Hash addressHash) {
    // no-op
  }

  @Override
  public void putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
    // no-op
  }

  @Override
  public void removeCode(final Hash accountHash, final Hash codeHash) {
    // no-op
  }

  @Override
  public void putStorageValueBySlotHash(
      final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
    // no-op
  }

  @Override
  public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
    // no-op
  }

  @Override
  public void putAccountStateTrieNode(
      final Bytes location, final Bytes32 nodeHash, final Bytes nodeValue) {
    // no-op
  }

  @Override
  public void putAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes nodeValue) {
    // no-op
  }
}
