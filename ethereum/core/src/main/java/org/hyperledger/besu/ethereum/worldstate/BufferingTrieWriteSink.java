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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Hash;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BufferingTrieWriteSink implements TrieWriteSink {

  private final WorldStateWriteSet writeSet;

  public BufferingTrieWriteSink(final WorldStateWriteSet writeSet) {
    this.writeSet = writeSet;
  }

  @Override
  public void putAccountInfoState(final Hash addressHash, final Bytes accountValue) {
    final Bytes valueCopy = accountValue.copy();
    writeSet.record(sink -> sink.putAccountInfoState(addressHash, valueCopy));
  }

  @Override
  public void removeAccountInfoState(final Hash addressHash) {
    writeSet.record(sink -> sink.removeAccountInfoState(addressHash));
  }

  @Override
  public void putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
    final Bytes codeCopy = code.copy();
    writeSet.record(sink -> sink.putCode(accountHash, codeHash, codeCopy));
  }

  @Override
  public void removeCode(final Hash accountHash, final Hash codeHash) {
    writeSet.record(sink -> sink.removeCode(accountHash, codeHash));
  }

  @Override
  public void putStorageValueBySlotHash(
      final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
    final Bytes valueCopy = storageValue.copy();
    writeSet.record(sink -> sink.putStorageValueBySlotHash(accountHash, slotHash, valueCopy));
  }

  @Override
  public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
    writeSet.record(sink -> sink.removeStorageValueBySlotHash(accountHash, slotHash));
  }

  @Override
  public void putAccountStateTrieNode(
      final Bytes location, final Bytes32 nodeHash, final Bytes nodeValue) {
    final Bytes locationCopy = location.copy();
    final Bytes valueCopy = nodeValue.copy();
    writeSet.record(sink -> sink.putAccountStateTrieNode(locationCopy, nodeHash, valueCopy));
  }

  @Override
  public void putAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes nodeValue) {
    final Bytes locationCopy = location.copy();
    final Bytes valueCopy = nodeValue.copy();
    writeSet.record(
        sink -> sink.putAccountStorageTrieNode(accountHash, locationCopy, nodeHash, valueCopy));
  }
}
