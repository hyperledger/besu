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
package org.hyperledger.besu.ethereum.debug;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class TraceFrame {

  private final int pc;
  private final String opcode;
  private final Gas gasRemaining;
  private final Optional<Gas> gasCost;
  private final int depth;
  private final EnumSet<ExceptionalHaltReason> exceptionalHaltReasons;
  private final Optional<Bytes32[]> stack;
  private final Optional<Bytes32[]> memory;
  private final Optional<Map<UInt256, UInt256>> storage;
  private final Optional<Bytes> revertReason;
  private final Optional<Map<Address, Wei>> maybeRefunds;
  private final Optional<Code> maybeCode;
  private final int stackItemsProduced;
  private final Optional<Bytes32[]> stackPostExecution;
  private final Optional<Bytes32[]> memoryPostExecution;
  private Optional<Integer> maybeNextDepth;

  private Gas gasRemainingPostExecution;
  private final Optional<Map<UInt256, UInt256>> storagePreExecution;
  private final boolean virtualOperation;

  public TraceFrame(
      final int pc,
      final String opcode,
      final Gas gasRemaining,
      final Optional<Gas> gasCost,
      final int depth,
      final EnumSet<ExceptionalHaltReason> exceptionalHaltReasons,
      final Optional<Bytes32[]> stack,
      final Optional<Bytes32[]> memory,
      final Optional<Map<UInt256, UInt256>> storage,
      final Optional<Bytes> revertReason,
      final Optional<Map<Address, Wei>> maybeRefunds,
      final Optional<Code> maybeCode,
      final int stackItemsProduced,
      final Optional<Bytes32[]> stackPostExecution,
      final Optional<Bytes32[]> memoryPostExecution,
      final Optional<Map<UInt256, UInt256>> storagePreExecution,
      final boolean virtualOperation) {
    this.pc = pc;
    this.opcode = opcode;
    this.gasRemaining = gasRemaining;
    this.gasCost = gasCost;
    this.depth = depth;
    this.exceptionalHaltReasons = exceptionalHaltReasons;
    this.stack = stack;
    this.memory = memory;
    this.storage = storage;
    this.revertReason = revertReason;
    this.maybeRefunds = maybeRefunds;
    this.maybeCode = maybeCode;
    this.stackItemsProduced = stackItemsProduced;
    this.stackPostExecution = stackPostExecution;
    this.memoryPostExecution = memoryPostExecution;
    this.maybeNextDepth = Optional.empty();
    this.storagePreExecution = storagePreExecution;
    this.virtualOperation = virtualOperation;
  }

  public TraceFrame(
      final int pc,
      final String opcode,
      final Gas gasRemaining,
      final Optional<Gas> gasCost,
      final int depth,
      final EnumSet<ExceptionalHaltReason> exceptionalHaltReasons,
      final Optional<Bytes32[]> stack,
      final Optional<Bytes32[]> memory,
      final Optional<Map<UInt256, UInt256>> storage,
      final Optional<Bytes> revertReason,
      final Optional<Map<Address, Wei>> maybeRefunds,
      final Optional<Code> maybeCode,
      final int stackItemsProduced,
      final Optional<Bytes32[]> stackPostExecution,
      final Optional<Bytes32[]> memoryPostExecution,
      final Optional<Map<UInt256, UInt256>> storagePreExecution) {
    this(
        pc,
        opcode,
        gasRemaining,
        gasCost,
        depth,
        exceptionalHaltReasons,
        stack,
        memory,
        storage,
        revertReason,
        maybeRefunds,
        maybeCode,
        stackItemsProduced,
        stackPostExecution,
        memoryPostExecution,
        storagePreExecution,
        false);
  }

  public int getPc() {
    return pc;
  }

  public String getOpcode() {
    return opcode;
  }

  public Gas getGasRemaining() {
    return gasRemaining;
  }

  public Optional<Gas> getGasCost() {
    return gasCost;
  }

  public int getDepth() {
    return depth;
  }

  public EnumSet<ExceptionalHaltReason> getExceptionalHaltReasons() {
    return exceptionalHaltReasons;
  }

  public Optional<Bytes32[]> getStack() {
    return stack;
  }

  public Optional<Bytes32[]> getMemory() {
    return memory;
  }

  public Optional<Map<UInt256, UInt256>> getStorage() {
    return storage;
  }

  public Optional<Bytes> getRevertReason() {
    return revertReason;
  }

  public Optional<Map<Address, Wei>> getMaybeRefunds() {
    return maybeRefunds;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pc", pc)
        .add("opcode", opcode)
        .add("getGasRemaining", gasRemaining)
        .add("gasCost", gasCost)
        .add("depth", depth)
        .add("exceptionalHaltReasons", exceptionalHaltReasons)
        .add("stack", stack)
        .add("memory", memory)
        .add("storage", storage)
        .toString();
  }

  public Optional<Code> getMaybeCode() {
    return maybeCode;
  }

  public int getStackItemsProduced() {
    return stackItemsProduced;
  }

  public Optional<Bytes32[]> getStackPostExecution() {
    return stackPostExecution;
  }

  public Optional<Bytes32[]> getMemoryPostExecution() {
    return memoryPostExecution;
  }

  public boolean depthHasIncreased() {
    return maybeNextDepth.map(next -> next > depth).orElse(false);
  }

  public boolean depthHasDecreased() {
    return maybeNextDepth.map(next -> next < depth).orElse(false);
  }

  public Optional<Integer> getMaybeNextDepth() {
    return maybeNextDepth;
  }

  public void setMaybeNextDepth(final Optional<Integer> maybeNextDepth) {
    this.maybeNextDepth = maybeNextDepth;
  }

  public Gas getGasRemainingPostExecution() {
    return gasRemainingPostExecution;
  }

  public Optional<Map<UInt256, UInt256>> getStoragePreExecution() {
    return storagePreExecution;
  }

  public void setGasRemainingPostExecution(final Gas gasRemainingPostExecution) {
    this.gasRemainingPostExecution = gasRemainingPostExecution;
  }

  public boolean isVirtualOperation() {
    return virtualOperation;
  }
}
