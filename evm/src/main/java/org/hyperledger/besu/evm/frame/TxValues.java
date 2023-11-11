/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.evm.frame;

import org.hyperledger.besu.collections.undo.UndoSet;
import org.hyperledger.besu.collections.undo.UndoTable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes32;

/**
 * Transaction Values used by various EVM Opcodes. These are the values that either do not change or
 * the backing stores whose changes transcend message frames and are not part of state, such as
 * transient storage and address warming.
 */
public record TxValues(
    Function<Long, Hash> blockHashLookup,
    int maxStackSize,
    UndoSet<Address> warmedUpAddresses,
    UndoTable<Address, Bytes32, Boolean> warmedUpStorage,
    Address originator,
    Wei gasPrice,
    Wei blobGasPrice,
    BlockValues blockValues,
    Deque<MessageFrame> messageFrameStack,
    Address miningBeneficiary,
    Optional<List<VersionedHash>> versionedHashes,
    UndoTable<Address, Bytes32, Bytes32> transientStorage,
    UndoSet<Address> creates,
    UndoSet<Address> selfDestructs) {

  /**
   * For all data stored in this record, undo the changes since the mark.
   *
   * @param mark the mark to which it should be rolled back to
   */
  public void undoChanges(final long mark) {
    warmedUpAddresses.undo(mark);
    warmedUpStorage.undo(mark);
    transientStorage.undo(mark);
    creates.undo(mark);
    selfDestructs.undo(mark);
  }
}
