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
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.internal.MemoryEntry;
import org.hyperledger.besu.evm.internal.StorageEntry;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class TraceFrame {

  private final int pc;
  private final Optional<String> opcode;
  private final long gasRemaining;
  private final OptionalLong gasCost;
  private final long gasRefund;
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

  private long gasRemainingPostExecution;
  private final boolean virtualOperation;
  private final Optional<MemoryEntry> maybeUpdatedMemory;
  private final Optional<StorageEntry> maybeUpdatedStorage;
  private OptionalLong precompiledGasCost;

  public TraceFrame(
      final int pc,
      final Optional<String> opcode,
      final long gasRemaining,
      final OptionalLong gasCost,
      final long gasRefund,
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
      final Optional<StorageEntry> maybeUpdatedStorage) {
    this.pc = pc;
    this.opcode = opcode;
    this.gasRemaining = gasRemaining;
    this.gasCost = gasCost;
    this.gasRefund = gasRefund;
    this.depth = depth;
    this.exceptionalHaltReason = exceptionalHaltReason;
    this.recipient = recipient;
    this.value = value;
    this.inputData = inputData;
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
    precompiledGasCost = OptionalLong.empty();
  }

  public int getPc() {
    return pc;
  }

  public String getOpcode() {
    return opcode.orElse("");
  }

  public long getGasRemaining() {
    return gasRemaining;
  }

  public OptionalLong getGasCost() {
    return gasCost;
  }

  public long getGasRefund() {
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

  public long getGasRemainingPostExecution() {
    return gasRemainingPostExecution;
  }

  public void setGasRemainingPostExecution(final long gasRemainingPostExecution) {
    this.gasRemainingPostExecution = gasRemainingPostExecution;
  }

  public boolean isVirtualOperation() {
    return virtualOperation;
  }

  public Optional<MemoryEntry> getMaybeUpdatedMemory() {
    return maybeUpdatedMemory;
  }

  public Optional<StorageEntry> getMaybeUpdatedStorage() {
    return maybeUpdatedStorage;
  }

  public OptionalLong getPrecompiledGasCost() {
    return precompiledGasCost;
  }

  public void setPrecompiledGasCost(final OptionalLong precompiledGasCost) {
    this.precompiledGasCost = precompiledGasCost;
  }
}
