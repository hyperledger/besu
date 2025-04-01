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
package org.hyperledger.besu.evm.frame;

import org.hyperledger.besu.collections.undo.UndoScalar;
import org.hyperledger.besu.collections.undo.UndoSet;
import org.hyperledger.besu.collections.undo.UndoTable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Transaction Values used by various EVM Opcodes. These are the values that either do not change or
 * the backing stores whose changes transcend message frames and are not part of state, such as
 * transient storage and address warming.
 *
 * @param blockHashLookup The block hash lookup function
 * @param maxStackSize The maximum stack size
 * @param warmedUpAddresses The warmed-up addresses
 * @param warmedUpStorage The warmed-up storage
 * @param originator The originator address
 * @param gasPrice The gas price
 * @param blobGasPrice The blob gas price
 * @param blockValues The block values
 * @param messageFrameStack The message frame stack
 * @param miningBeneficiary The mining beneficiary address
 * @param versionedHashes The optional list of versioned hashes
 * @param initcodes the initcodes associated with the executing transaction
 * @param initcodeHashes the initcodes hashes associated with the executing transaction, filled on
 *     demand if the list is mutable
 * @param transientStorage The transient storage
 * @param creates The set of addresses that creates
 * @param selfDestructs The set of addresses that self-destructs
 * @param gasRefunds The gas refunds
 */
public record TxValues(
    BlockHashLookup blockHashLookup,
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
    Optional<List<Bytes>> initcodes,
    Optional<List<Bytes>> initcodeHashes,
    UndoTable<Address, Bytes32, Bytes32> transientStorage,
    UndoSet<Address> creates,
    UndoSet<Address> selfDestructs,
    UndoScalar<Long> gasRefunds) {

  /**
   * Preferred constructor for TX data
   *
   * @param blockHashLookup the function that looks up block hashes
   * @param maxStackSize maximum stack size for the vm
   * @param warmedUpAddresses Journaled list of warmed up addresses
   * @param warmedUpStorage Journaled list of warmed up storage
   * @param originator The original address that signed the transaction, the ORIGIN EOA
   * @param gasPrice gas price to use for this transaction
   * @param blobGasPrice blob gas price for the block
   * @param blockValues Data from the current block header
   * @param messageFrameStack Stack of message frames/call frames
   * @param miningBeneficiary Address of the mining benificiary
   * @param versionedHashes Versioned Hashes attached to this transaction
   * @param initcodes Initcodes attached to this transaction
   * @param transientStorage Transient storage map of maps
   * @param creates Journaled list of addresses created in this transaction
   * @param selfDestructs List of addresses self-destructed within the current transaction
   * @param gasRefunds the amount of gas refund pending
   */
  public TxValues(
      final BlockHashLookup blockHashLookup,
      final int maxStackSize,
      final UndoSet<Address> warmedUpAddresses,
      final UndoTable<Address, Bytes32, Boolean> warmedUpStorage,
      final Address originator,
      final Wei gasPrice,
      final Wei blobGasPrice,
      final BlockValues blockValues,
      final Deque<MessageFrame> messageFrameStack,
      final Address miningBeneficiary,
      final Optional<List<VersionedHash>> versionedHashes,
      final Optional<List<Bytes>> initcodes,
      final UndoTable<Address, Bytes32, Bytes32> transientStorage,
      final UndoSet<Address> creates,
      final UndoSet<Address> selfDestructs,
      final UndoScalar<Long> gasRefunds) {
    this(
        blockHashLookup,
        maxStackSize,
        warmedUpAddresses,
        warmedUpStorage,
        originator,
        gasPrice,
        blobGasPrice,
        blockValues,
        messageFrameStack,
        miningBeneficiary,
        versionedHashes,
        initcodes,
        initcodes.map(l -> new ArrayList<>(l.size())),
        transientStorage,
        creates,
        selfDestructs,
        gasRefunds);
  }

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
    gasRefunds.undo(mark);
  }

  /**
   * Retrieves an initcode (typically from an InitcodeTransaction) by keccak hash
   *
   * @param targetHash the hash of the desired initcode
   * @return The raw bytes of the initcode, if in the transaction, or null if not in the
   *     transaction.
   */
  public Bytes getInitcodeByHash(final Bytes targetHash) {
    if (initcodes.isEmpty() || initcodeHashes.isEmpty()) {
      return null;
    }

    var codes = initcodes.get();
    var hashes = initcodeHashes.get();
    for (int i = 0; i < codes.size(); i++) {
      Bytes hash;
      if (hashes.size() <= i) {
        hash = Hash.hash(codes.get(i));
        hashes.add(hash);
      } else {
        hash = hashes.get(i);
      }
      if (hash.equals(targetHash)) {
        return codes.get(i);
      }
    }

    return null;
  }
}
