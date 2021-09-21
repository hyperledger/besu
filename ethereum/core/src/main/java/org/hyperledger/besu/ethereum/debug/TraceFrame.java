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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.internal.MemoryEntry;

import java.util.Map;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class TraceFrame {

  private final int pc;
  private final Optional<String> opcode;
  private final Gas gasRemaining;
  private final Optional<Gas> gasCost;
  private final Gas gasRefund;
  private final int depth;
  private Optional<ExceptionalHaltReason> exceptionalHaltReason;
  private final Address recipient;
  private final Wei value;
  private final Bytes inputData;
  private final Bytes outputData;
  private final Optional<Bytes32[]> stack;
  private final Optional<Bytes[]> memory;
  private final Optional<Map<UInt256, UInt256>> storage;
  private final WorldUpdater worldUpdater;
  private final Optional<Bytes> revertReason;
  private final Optional<Map<Address, Wei>> maybeRefunds;
  private final Optional<Code> maybeCode;
  private final int stackItemsProduced;
  private final Optional<Bytes32[]> stackPostExecution;

  private Gas gasRemainingPostExecution;
  private final boolean virtualOperation;
  private final Optional<MemoryEntry> maybeUpdatedMemory;
  private final Optional<MemoryEntry> maybeUpdatedStorage;
  private Optional<Gas> precompiledGasCost;

  public TraceFrame(
      final int pc,
      final Optional<String> opcode,
      final Gas gasRemaining,
      final Optional<Gas> gasCost,
      final Gas gasRefund,
      final int depth,
      final Optional<ExceptionalHaltReason> exceptionalHaltReason,
      final Address recipient,
      final Wei value,
      final Bytes inputData,
      final Bytes outputData,
      final Optional<Bytes32[]> stack,
      final Optional<Bytes[]> memory,
      final Optional<Map<UInt256, UInt256>> storage,
      final WorldUpdater worldUpdater,
      final Optional<Bytes> revertReason,
      final Optional<Map<Address, Wei>> maybeRefunds,
      final Optional<Code> maybeCode,
      final int stackItemsProduced,
      final Optional<Bytes32[]> stackPostExecution,
      final boolean virtualOperation,
      final Optional<MemoryEntry> maybeUpdatedMemory,
      final Optional<MemoryEntry> maybeUpdatedStorage) {
    this.pc = pc;
    this.opcode = opcode;
    this.gasRemaining = gasRemaining;
    this.gasCost = gasCost;
    this.gasRefund = gasRefund;
    this.depth = depth;
    this.exceptionalHaltReason = exceptionalHaltReason;
    this.recipient = recipient;
    this.value = value;
    this.inputData = inputData.copy();
    this.outputData = outputData;
    this.stack = stack;
    this.memory = memory;
    this.storage = storage;
    this.worldUpdater = worldUpdater;
    this.revertReason = revertReason;
    this.maybeRefunds = maybeRefunds;
    this.maybeCode = maybeCode;
    this.stackItemsProduced = stackItemsProduced;
    this.stackPostExecution = stackPostExecution;
    this.virtualOperation = virtualOperation;
    this.maybeUpdatedMemory = maybeUpdatedMemory;
    this.maybeUpdatedStorage = maybeUpdatedStorage;
    precompiledGasCost = Optional.empty();
  }

  public int getPc() {
    return pc;
  }

  public String getOpcode() {
    return opcode.orElse("");
  }

  public Gas getGasRemaining() {
    return gasRemaining;
  }

  public Optional<Gas> getGasCost() {
    return gasCost;
  }

  public Gas getGasRefund() {
    return gasRefund;
  }

  public int getDepth() {
    return depth;
  }

  public Optional<ExceptionalHaltReason> getExceptionalHaltReason() {
    return exceptionalHaltReason;
  }

  public void setExceptionalHaltReason(final ExceptionalHaltReason exceptionalHaltReason) {
    this.exceptionalHaltReason = Optional.ofNullable(exceptionalHaltReason);
  }

  public Address getRecipient() {
    return recipient;
  }

  public Wei getValue() {
    return value;
  }

  public Bytes getInputData() {
    return inputData;
  }

  public Bytes getOutputData() {
    return outputData;
  }

  public Optional<Bytes32[]> getStack() {
    return stack;
  }

  public Optional<Bytes[]> getMemory() {
    return memory;
  }

  public Optional<Map<UInt256, UInt256>> getStorage() {
    return storage;
  }

  public WorldUpdater getWorldUpdater() {
    return worldUpdater;
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
        .add("exceptionalHaltReason", exceptionalHaltReason)
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

  public Gas getGasRemainingPostExecution() {
    return gasRemainingPostExecution;
  }

  public void setGasRemainingPostExecution(final Gas gasRemainingPostExecution) {
    this.gasRemainingPostExecution = gasRemainingPostExecution;
  }

  public boolean isVirtualOperation() {
    return virtualOperation;
  }

  public Optional<MemoryEntry> getMaybeUpdatedMemory() {
    return maybeUpdatedMemory;
  }

  public Optional<MemoryEntry> getMaybeUpdatedStorage() {
    return maybeUpdatedStorage;
  }

  public Optional<Gas> getPrecompiledGasCost() {
    return precompiledGasCost;
  }

  public void setPrecompiledGasCost(final Optional<Gas> precompiledGasCost) {
    this.precompiledGasCost = precompiledGasCost;
  }
}
