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
package org.hyperledger.besu.ethereum.vm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.AbstractMessageProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.vm.internal.MemoryEntry;
import org.hyperledger.besu.ethereum.vm.operations.ReturnStack;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * A container object for all of the state associated with a message.
 *
 * <p>A message corresponds to an interaction between two accounts. A {@link Transaction} spawns at
 * least one message when its processed. Messages can also spawn messages depending on the code
 * executed within a message.
 *
 * <p>Note that there is no specific Message object in the code base. Instead message executions
 * correspond to a {@code MessageFrame} and a specific {@link AbstractMessageProcessor}. Currently
 * there are two such {@link AbstractMessageProcessor} types:
 *
 * <p><b>Message Call ({@code MESSAGE_CALL})</b>
 *
 * <p>A message call consists of applying a set of changes to an account on behalf of another
 * account. At the minimal end of changes is a value transfer between a sender and recipient
 * account. If the recipient account contains code, that code is also executed.
 *
 * <p><b>Contract Creation ({@code CONTRACT_CREATION})</b>
 *
 * <p>A contract creation, as its name suggests, creates contract accounts. Contract initialization
 * code and a value are supplied to initialize the contract account code and balance, respectively.
 */
public class MessageFrame {

  /**
   * Message Frame State.
   *
   * <h2>Message Frame Lifecycle</h2>
   *
   * <p>The diagram below presents the message frame lifecycle:
   *
   * <pre>
   *            ------------------------------------------------------
   *            |                                                    |
   *            |                                                    v
   *            |               ---------------------     ---------------------
   *            |               |                   |     |                   |
   *            |               |    CODE_SUCCESS   | --&gt; | COMPLETED_SUCCESS |
   *            |               |                   |     |                   |
   *            |               ---------------------     ---------------------
   *            |                         ^
   *            |                         |
   *  ---------------------     ---------------------     ---------------------
   *  |                   |     |                   | --&gt; |                   |
   *  |    NOT_STARTED    | --&gt; |   CODE_EXECUTING  |     |   CODE_SUSPENDED  |
   *  |                   |     |                   | &lt;-- |                   |
   *  ---------------------     ---------------------     ---------------------
   *            |                         |
   *            |                         |
   *            |                         |                 ---------------------
   *            |                         |                 |                   |
   *            |                         |------------&gt; |      REVERTED     |
   *            |                         |                 |                   |
   *            |                         |                 ---------------------
   *            |                         |
   *            |                         v
   *            |               ---------------------     ---------------------
   *            |               |                   |     |                   |
   *            |-------------&gt; |  EXCEPTIONAL_HALT | --&gt; | COMPLETED_FAILURE |
   *                            |                   |     |                   |
   *                            ---------------------     ---------------------
   * </pre>
   *
   * <h3>Message Not Started ({@link #NOT_STARTED})</h3>
   *
   * <p>The message has not begun to execute yet.
   *
   * <h3>Code Executing ({@link #CODE_EXECUTING})</h3>
   *
   * <p>The message contains code and has begun executing it. The execution will continue until it
   * is halted due to (1) spawning a child message (2) encountering an exceptional halting condition
   * (2) completing successfully.
   *
   * <h3>Code Suspended Execution ({@link #CODE_SUSPENDED})</h3>
   *
   * <p>The message has spawned a child message and has suspended its execution until the child
   * message has completed and notified its parent message. The message will then continue executing
   * code ({@link #CODE_EXECUTING}) again.
   *
   * <h3>Code Execution Completed Successfully ({@link #CODE_SUSPENDED})</h3>
   *
   * <p>The code within the message has executed to completion successfully.
   *
   * <h3>Message Exceptionally Halted ({@link #EXCEPTIONAL_HALT})</h3>
   *
   * <p>The message execution has encountered an exceptional halting condition at some point during
   * its execution.
   *
   * <h3>Message Reverted ({@link #REVERT})</h3>
   *
   * <p>The message execution has requested to revert state during execution.
   *
   * <h3>Message Execution Failed ({@link #COMPLETED_FAILED})</h3>
   *
   * <p>The message execution failed to execute successfully; most likely due to encountering an
   * exceptional halting condition. At this point the message frame is finalized and the parent is
   * notified.
   *
   * <h3>Message Execution Completed Successfully ({@link #COMPLETED_SUCCESS})</h3>
   *
   * <p>The message execution completed successfully and needs to finalized and propagated to the
   * parent message that spawned it.
   */
  public enum State {

    /** Message execution has not started. */
    NOT_STARTED,

    /** Code execution within the message is in progress. */
    CODE_EXECUTING,

    /** Code execution within the message has finished successfully. */
    CODE_SUCCESS,

    /** Code execution within the message has been suspended. */
    CODE_SUSPENDED,

    /** An exceptional halting condition has occurred. */
    EXCEPTIONAL_HALT,

    /** State changes were reverted during execution. */
    REVERT,

    /** The message execution has failed to complete successfully. */
    COMPLETED_FAILED,

    /** The message execution has completed successfully. */
    COMPLETED_SUCCESS,
  }

  /** The message type the frame corresponds to. */
  public enum Type {

    /** A Contract creation message. */
    CONTRACT_CREATION,

    /** A message call message. */
    MESSAGE_CALL,
  }

  public static final int DEFAULT_MAX_STACK_SIZE = 1024;

  // Global data fields.
  private final WorldUpdater worldState;
  private final Blockchain blockchain;

  // Metadata fields.
  private final Type type;
  private State state;

  // Machine state fields.
  private Gas gasRemaining;
  private final BlockHashLookup blockHashLookup;
  private final int maxStackSize;
  private int pc;
  private final Memory memory;
  private final OperandStack stack;
  private Bytes output;
  private Bytes returnData;
  private final boolean isStatic;

  // Transaction substate fields.
  private final List<Log> logs;
  private Gas gasRefund;
  private final Set<Address> selfDestructs;
  private final Map<Address, Wei> refunds;

  // Execution Environment fields.
  private final Address recipient;
  private final Address originator;
  private final Address contract;
  private final int contractAccountVersion;
  private final Wei gasPrice;
  private final Bytes inputData;
  private final Address sender;
  private final Wei value;
  private final Wei apparentValue;
  private final Code code;
  private final ProcessableBlockHeader blockHeader;
  private final int depth;
  private final Deque<MessageFrame> messageFrameStack;
  private final Address miningBeneficiary;
  private final Boolean isPersistingPrivateState;
  private final PrivateMetadataUpdater privateMetadataUpdater;
  private Optional<Bytes> revertReason;

  // as defined on https://eips.ethereum.org/EIPS/eip-2315
  private final ReturnStack returnStack;

  // Privacy Execution Environment fields.
  private final Hash transactionHash;

  // Miscellaneous fields.
  private Optional<ExceptionalHaltReason> exceptionalHaltReason = Optional.empty();
  private Operation currentOperation;
  private Optional<Gas> gasCost = Optional.empty();
  private final Consumer<MessageFrame> completer;
  private Optional<MemoryEntry> maybeUpdatedMemory = Optional.empty();
  private Optional<MemoryEntry> maybeUpdatedStorage = Optional.empty();

  public static Builder builder() {
    return new Builder();
  }

  private MessageFrame(
      final Type type,
      final Blockchain blockchain,
      final Deque<MessageFrame> messageFrameStack,
      final ReturnStack returnStack,
      final WorldUpdater worldState,
      final Gas initialGas,
      final Address recipient,
      final Address originator,
      final Address contract,
      final int contractAccountVersion,
      final Wei gasPrice,
      final Bytes inputData,
      final Address sender,
      final Wei value,
      final Wei apparentValue,
      final Code code,
      final ProcessableBlockHeader blockHeader,
      final int depth,
      final boolean isStatic,
      final Consumer<MessageFrame> completer,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final Hash transactionHash,
      final Optional<Bytes> revertReason,
      final int maxStackSize) {
    this.type = type;
    this.blockchain = blockchain;
    this.messageFrameStack = messageFrameStack;
    this.returnStack = returnStack;
    this.worldState = worldState;
    this.gasRemaining = initialGas;
    this.blockHashLookup = blockHashLookup;
    this.maxStackSize = maxStackSize;
    this.pc = 0;
    this.memory = new Memory();
    this.stack = new OperandStack(maxStackSize);
    this.output = Bytes.EMPTY;
    this.returnData = Bytes.EMPTY;
    this.logs = new ArrayList<>();
    this.gasRefund = Gas.ZERO;
    this.selfDestructs = new HashSet<>();
    this.refunds = new HashMap<>();
    this.recipient = recipient;
    this.originator = originator;
    this.contract = contract;
    this.contractAccountVersion = contractAccountVersion;
    this.gasPrice = gasPrice;
    this.inputData = inputData;
    this.sender = sender;
    this.value = value;
    this.apparentValue = apparentValue;
    this.code = code;
    this.blockHeader = blockHeader;
    this.depth = depth;
    this.state = State.NOT_STARTED;
    this.isStatic = isStatic;
    this.completer = completer;
    this.miningBeneficiary = miningBeneficiary;
    this.isPersistingPrivateState = isPersistingPrivateState;
    this.privateMetadataUpdater = privateMetadataUpdater;
    this.transactionHash = transactionHash;
    this.revertReason = revertReason;
  }

  /**
   * Return the program counter.
   *
   * @return the program counter
   */
  public int getPC() {
    return pc;
  }

  /**
   * Set the program counter.
   *
   * @param pc The new program counter value
   */
  public void setPC(final int pc) {
    this.pc = pc;
  }

  /** Deducts the remaining gas. */
  public void clearGasRemaining() {
    this.gasRemaining = Gas.ZERO;
  }

  /**
   * Decrement the amount of remaining gas.
   *
   * @param amount The amount of gas to deduct
   */
  public void decrementRemainingGas(final Gas amount) {
    this.gasRemaining = gasRemaining.minus(amount);
  }

  /**
   * Return the amount of remaining gas.
   *
   * @return the amount of remaining gas
   */
  public Gas getRemainingGas() {
    return gasRemaining;
  }

  /**
   * Increment the amount of remaining gas.
   *
   * @param amount The amount of gas to increment
   */
  public void incrementRemainingGas(final Gas amount) {
    this.gasRemaining = gasRemaining.plus(amount);
  }

  /**
   * Set the amount of remaining gas.
   *
   * @param amount The amount of remaining gas
   */
  public void setGasRemaining(final Gas amount) {
    this.gasRemaining = amount;
  }

  /**
   * Return the output data.
   *
   * @return the output data
   */
  public Bytes getOutputData() {
    return output;
  }

  /**
   * Set the output data.
   *
   * @param output The output data
   */
  public void setOutputData(final Bytes output) {
    this.output = output;
  }

  /** Clears the output data buffer. */
  public void clearOutputData() {
    setOutputData(Bytes.EMPTY);
  }

  /**
   * Return the return data.
   *
   * @return the return data
   */
  public Bytes getReturnData() {
    return returnData;
  }

  /**
   * Set the return data.
   *
   * @param returnData The return data
   */
  public void setReturnData(final Bytes returnData) {
    this.returnData = returnData;
  }

  /** Clear the return data buffer. */
  public void clearReturnData() {
    setReturnData(Bytes.EMPTY);
  }

  /**
   * Returns the item at the specified offset in the stack.
   *
   * @param offset The item's position relative to the top of the stack
   * @return The item at the specified offset in the stack
   * @throws IndexOutOfBoundsException if the offset is out of range
   */
  public Bytes32 getStackItem(final int offset) {
    return stack.get(offset);
  }

  /**
   * Removes the item at the top of the stack.
   *
   * @return the item at the top of the stack
   * @throws IllegalStateException if the stack is empty
   */
  public Bytes32 popStackItem() {
    return stack.pop();
  }

  /**
   * Removes the corresponding number of items from the top of the stack.
   *
   * @param n The number of items to pop off the stack
   */
  public void popStackItems(final int n) {
    stack.bulkPop(n);
  }

  /**
   * Pushes the corresponding item onto the top of the stack
   *
   * @param value The value to push onto the stack.
   */
  public void pushStackItem(final Bytes32 value) {
    stack.push(value);
  }

  /**
   * Sets the stack item at the specified offset from the top of the stack to the value
   *
   * @param offset The item's position relative to the top of the stack
   * @param value The value to set the stack item to
   * @throws IllegalStateException if the stack is too small
   */
  public void setStackItem(final int offset, final Bytes32 value) {
    stack.set(offset, value);
  }

  /**
   * Return the current stack size.
   *
   * @return The current stack size
   */
  public int stackSize() {
    return stack.size();
  }

  /**
   * Tests if the return stack is full
   *
   * @return true is the return stack is full, else false
   */
  public boolean isReturnStackFull() {
    return returnStack.isFull();
  }

  /**
   * Tests if the return stack is empty
   *
   * @return true is the return stack is empty, else false
   */
  public boolean isReturnStackEmpty() {
    return returnStack.isEmpty();
  }

  /**
   * Removes the item at the top of the return stack.
   *
   * @return the item at the top of the return stack
   * @throws IllegalStateException if the return stack is empty
   */
  public int popReturnStackItem() {
    return returnStack.pop();
  }

  /**
   * Return the return stack.
   *
   * @return the return stack
   */
  public ReturnStack getReturnStack() {
    return returnStack;
  }

  /**
   * Pushes the corresponding item onto the top of the return stack
   *
   * @param value The value to push onto the return stack.
   * @throws IllegalStateException if the stack is full
   */
  public void pushReturnStackItem(final int value) {
    returnStack.push(value);
  }

  /**
   * Returns whether or not the message frame is static or not.
   *
   * @return {@code} true if the frame is static; otherwise {@code false}
   */
  public boolean isStatic() {
    return isStatic;
  }

  /**
   * Returns the memory size for specified memory access.
   *
   * @param offset The offset in memory
   * @param length The length of the memory access
   * @return the memory size for specified memory access
   */
  public UInt256 calculateMemoryExpansion(final UInt256 offset, final UInt256 length) {
    return memory.calculateNewActiveWords(offset, length);
  }

  /**
   * Expands memory to accommodate the specified memory access.
   *
   * @param offset The offset in memory
   * @param length The length of the memory access
   */
  public void expandMemory(final UInt256 offset, final UInt256 length) {
    memory.ensureCapacityForBytes(offset, length);
  }

  /**
   * Returns the number of bytes in memory.
   *
   * @return the number of bytes in memory
   */
  public long memoryByteSize() {
    return memory.getActiveBytes();
  }

  /**
   * Returns the number of words in memory.
   *
   * @return the number of words in memory
   */
  public UInt256 memoryWordSize() {
    return memory.getActiveWords();
  }

  /**
   * Returns the revertReason as string
   *
   * @return the revertReason string
   */
  public Optional<Bytes> getRevertReason() {
    return revertReason;
  }

  public void setRevertReason(final Bytes revertReason) {
    this.revertReason = Optional.ofNullable(revertReason);
  }

  /**
   * Read bytes in memory.
   *
   * @param offset The offset in memory
   * @param length The length of the bytes to read
   * @return The bytes in the specified range
   */
  public Bytes readMemory(final UInt256 offset, final UInt256 length) {
    return readMemory(offset, length, false);
  }

  /**
   * Read bytes in memory.
   *
   * @param offset The offset in memory
   * @param length The length of the bytes to read
   * @param explicitMemoryRead true if triggered by a memory opcode, false otherwise
   * @return The bytes in the specified range
   */
  public Bytes readMemory(
      final UInt256 offset, final UInt256 length, final boolean explicitMemoryRead) {
    final Bytes value = memory.getBytes(offset, length);
    if (explicitMemoryRead) {
      setUpdatedMemory(offset, value);
    }
    return value;
  }

  /**
   * Write byte to memory
   *
   * @param offset The offset in memory
   * @param value The value to set in memory
   * @param explicitMemoryUpdate true if triggered by a memory opcode, false otherwise
   */
  public void writeMemory(
      final UInt256 offset, final byte value, final boolean explicitMemoryUpdate) {
    memory.setByte(offset, value);
    if (explicitMemoryUpdate) {
      setUpdatedMemory(offset, Bytes.of(value));
    }
  }

  /**
   * Write bytes to memory
   *
   * @param offset The offset in memory
   * @param length The length of the bytes to write
   * @param value The value to write
   */
  public void writeMemory(final UInt256 offset, final UInt256 length, final Bytes value) {
    writeMemory(offset, length, value, false);
  }

  /**
   * Write bytes to memory
   *
   * @param offset The offset in memory
   * @param length The length of the bytes to write
   * @param value The value to write
   * @param explicitMemoryUpdate true if triggered by a memory opcode, false otherwise
   */
  public void writeMemory(
      final UInt256 offset,
      final UInt256 length,
      final Bytes value,
      final boolean explicitMemoryUpdate) {
    memory.setBytes(offset, length, value);
    if (explicitMemoryUpdate) {
      setUpdatedMemory(offset, UInt256.ZERO, length, value);
    }
  }

  /**
   * Write bytes to memory
   *
   * @param offset The offset in memory to start the write
   * @param sourceOffset The offset in the source value to start the write
   * @param length The length of the bytes to write
   * @param value The value to write
   */
  public void writeMemory(
      final UInt256 offset, final UInt256 sourceOffset, final UInt256 length, final Bytes value) {
    writeMemory(offset, sourceOffset, length, value, false);
  }

  /**
   * Write bytes to memory
   *
   * @param offset The offset in memory to start the write
   * @param sourceOffset The offset in the source value to start the write
   * @param length The length of the bytes to write
   * @param value The value to write
   * @param explicitMemoryUpdate true if triggered by a memory opcode, false otherwise
   */
  public void writeMemory(
      final UInt256 offset,
      final UInt256 sourceOffset,
      final UInt256 length,
      final Bytes value,
      final boolean explicitMemoryUpdate) {
    memory.setBytes(offset, sourceOffset, length, value);
    if (explicitMemoryUpdate && length.toLong() > 0) {
      setUpdatedMemory(offset, sourceOffset, length, value);
    }
  }

  private void setUpdatedMemory(
      final UInt256 offset, final UInt256 sourceOffset, final UInt256 length, final Bytes value) {
    final int srcOff = sourceOffset.fitsInt() ? sourceOffset.intValue() : Integer.MAX_VALUE;
    final int len = length.fitsInt() ? length.intValue() : Integer.MAX_VALUE;
    final int endIndex = srcOff + len;
    if (srcOff >= 0 && endIndex > 0) {
      final int srcSize = value.size();
      if (endIndex > srcSize) {
        final MutableBytes paddedAnswer = MutableBytes.create(len);
        if (srcOff < srcSize) {
          value.slice(srcOff, srcSize - srcOff).copyTo(paddedAnswer, 0);
        }
        setUpdatedMemory(offset, paddedAnswer.copy());
      } else {
        setUpdatedMemory(offset, value.slice(srcOff, len).copy());
      }
    }
  }

  private void setUpdatedMemory(final UInt256 offset, final Bytes value) {
    maybeUpdatedMemory = Optional.of(new MemoryEntry(offset, value));
  }

  public void storageWasUpdated(final UInt256 storageAddress, final Bytes value) {
    maybeUpdatedStorage = Optional.of(new MemoryEntry(storageAddress, value));
  }
  /**
   * Accumulate a log.
   *
   * @param log The log to accumulate
   */
  public void addLog(final Log log) {
    logs.add(log);
  }

  /**
   * Accumulate logs.
   *
   * @param logs The logs to accumulate
   */
  public void addLogs(final List<Log> logs) {
    this.logs.addAll(logs);
  }

  /** Clear the accumulated logs. */
  public void clearLogs() {
    logs.clear();
  }

  /**
   * Return the accumulated logs.
   *
   * @return the accumulated logs
   */
  public List<Log> getLogs() {
    return logs;
  }

  /**
   * Increment the gas refund.
   *
   * @param amount The amount to increment the refund
   */
  public void incrementGasRefund(final Gas amount) {
    this.gasRefund = gasRefund.plus(amount);
  }

  /** Clear the accumulated gas refund. */
  public void clearGasRefund() {
    gasRefund = Gas.ZERO;
  }

  /**
   * Return the accumulated gas refund.
   *
   * @return accumulated gas refund
   */
  public Gas getGasRefund() {
    return gasRefund;
  }

  /**
   * Add recipient to the self-destruct set if not already present.
   *
   * @param address The recipient to self-destruct
   */
  public void addSelfDestruct(final Address address) {
    selfDestructs.add(address);
  }

  /**
   * Add addresses to the self-destruct set if they are not already present.
   *
   * @param addresses The addresses to self-destruct
   */
  public void addSelfDestructs(final Set<Address> addresses) {
    selfDestructs.addAll(addresses);
  }

  /** Removes all entries in the self-destruct set. */
  public void clearSelfDestructs() {
    selfDestructs.clear();
  }

  /**
   * Returns the self-destruct set.
   *
   * @return the self-destruct set
   */
  public Set<Address> getSelfDestructs() {
    return selfDestructs;
  }

  /**
   * Add refund to the refunds map if not already present.
   *
   * @param beneficiary the beneficiary of the refund.
   * @param amount the amount of the refund.
   */
  public void addRefund(final Address beneficiary, final Wei amount) {
    refunds.put(beneficiary, amount);
  }

  /**
   * Returns the refunds map.
   *
   * @return the refunds map
   */
  Map<Address, Wei> getRefunds() {
    return refunds;
  }

  /**
   * Returns the current blockchain.
   *
   * @return the current blockchain
   */
  public Blockchain getBlockchain() {
    return blockchain;
  }

  /**
   * Return the world state.
   *
   * @return the world state
   */
  public WorldUpdater getWorldState() {
    return worldState;
  }

  /**
   * Returns the message frame type.
   *
   * @return the message frame type
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns the current execution state.
   *
   * @return the current execution state
   */
  public State getState() {
    return state;
  }

  /**
   * Sets the current execution state.
   *
   * @param state The new execution state
   */
  public void setState(final State state) {
    this.state = state;
  }

  /**
   * Returns the code currently being executed.
   *
   * @return the code currently being executed
   */
  public Code getCode() {
    return code;
  }

  /**
   * Returns the current input data.
   *
   * @return the current input data
   */
  public Bytes getInputData() {
    return inputData;
  }

  /**
   * Returns the recipient account recipient
   *
   * @return the callee account recipient
   */
  public Address getRecipientAddress() {
    return recipient;
  }

  /**
   * Returns the message stack depth.
   *
   * @return the message stack depth
   */
  public int getMessageStackDepth() {
    return depth;
  }

  /**
   * Returns the recipient that originated the message.
   *
   * @return the recipient that originated the message
   */
  public Address getOriginatorAddress() {
    return originator;
  }

  /**
   * Returns the recipient of the code currently executing.
   *
   * @return the recipient of the code currently executing
   */
  public Address getContractAddress() {
    return contract;
  }

  /**
   * Returns the current gas price.
   *
   * @return the current gas price
   */
  public Wei getGasPrice() {
    return gasPrice;
  }

  /**
   * Returns the recipient of the sender.
   *
   * @return the recipient of the sender
   */
  public Address getSenderAddress() {
    return sender;
  }

  /**
   * Returns the value being transferred.
   *
   * @return the value being transferred
   */
  public Wei getValue() {
    return value;
  }

  /**
   * Returns the apparent value being transferred.
   *
   * @return the apparent value being transferred
   */
  public Wei getApparentValue() {
    return apparentValue;
  }

  /**
   * Returns the current block header.
   *
   * @return the current block header
   */
  public ProcessableBlockHeader getBlockHeader() {
    return blockHeader;
  }

  /** Performs updates based on the message frame's execution. */
  public void notifyCompletion() {
    completer.accept(this);
  }

  /**
   * Returns the current message frame stack.
   *
   * @return the current message frame stack
   */
  public Deque<MessageFrame> getMessageFrameStack() {
    return messageFrameStack;
  }

  void setExceptionalHaltReason(final Optional<ExceptionalHaltReason> exceptionalHaltReason) {
    this.exceptionalHaltReason = exceptionalHaltReason;
  }

  public Optional<ExceptionalHaltReason> getExceptionalHaltReason() {
    return exceptionalHaltReason;
  }

  /**
   * Returns the current miningBeneficiary (aka coinbase)
   *
   * @return the current mining beneficiary
   */
  public Address getMiningBeneficiary() {
    return miningBeneficiary;
  }

  public BlockHashLookup getBlockHashLookup() {
    return blockHashLookup;
  }

  public Operation getCurrentOperation() {
    return currentOperation;
  }

  public Optional<Gas> getGasCost() {
    return gasCost;
  }

  public int getMaxStackSize() {
    return maxStackSize;
  }

  /**
   * Returns whether Message calls will be persisted
   *
   * @return whether Message calls will be persisted
   */
  public Boolean isPersistingPrivateState() {
    return isPersistingPrivateState;
  }

  public PrivateMetadataUpdater getPrivateMetadataUpdater() {
    return privateMetadataUpdater;
  }

  /**
   * Returns the transaction hash of the transaction being processed
   *
   * @return the transaction hash of the transaction being processed
   */
  public Hash getTransactionHash() {
    return transactionHash;
  }

  public void setCurrentOperation(final Operation currentOperation) {
    this.currentOperation = currentOperation;
  }

  public void setGasCost(final Optional<Gas> gasCost) {
    this.gasCost = gasCost;
  }

  public int getContractAccountVersion() {
    return contractAccountVersion;
  }

  Optional<MemoryEntry> getMaybeUpdatedMemory() {
    return maybeUpdatedMemory;
  }

  Optional<MemoryEntry> getMaybeUpdatedStorage() {
    return maybeUpdatedStorage;
  }

  public void reset() {
    maybeUpdatedMemory = Optional.empty();
    maybeUpdatedStorage = Optional.empty();
  }

  public static class Builder {

    private Type type;
    private Blockchain blockchain;
    private Deque<MessageFrame> messageFrameStack;
    private WorldUpdater worldState;
    private Gas initialGas;
    private Address address;
    private Address originator;
    private Address contract;
    private int contractAccountVersion = -1;
    private Wei gasPrice;
    private Bytes inputData;
    private Address sender;
    private Wei value;
    private Wei apparentValue;
    private Code code;
    private ProcessableBlockHeader blockHeader;
    private int depth = -1;
    private int maxStackSize = DEFAULT_MAX_STACK_SIZE;
    private boolean isStatic = false;
    private Consumer<MessageFrame> completer;
    private Address miningBeneficiary;
    private BlockHashLookup blockHashLookup;
    private Boolean isPersistingPrivateState = false;
    private PrivateMetadataUpdater privateMetadataUpdater = null;
    private Hash transactionHash;
    private Optional<Bytes> reason = Optional.empty();
    private ReturnStack returnStack = new ReturnStack();

    public Builder type(final Type type) {
      this.type = type;
      return this;
    }

    public Builder returnStack(final ReturnStack returnStack) {
      this.returnStack = returnStack;
      return this;
    }

    public Builder messageFrameStack(final Deque<MessageFrame> messageFrameStack) {
      this.messageFrameStack = messageFrameStack;
      return this;
    }

    public Builder blockchain(final Blockchain blockchain) {
      this.blockchain = blockchain;
      return this;
    }

    public Builder worldState(final WorldUpdater worldState) {
      this.worldState = worldState;
      return this;
    }

    public Builder initialGas(final Gas initialGas) {
      this.initialGas = initialGas;
      return this;
    }

    public Builder address(final Address address) {
      this.address = address;
      return this;
    }

    public Builder originator(final Address originator) {
      this.originator = originator;
      return this;
    }

    public Builder contract(final Address contract) {
      this.contract = contract;
      return this;
    }

    public Builder contractAccountVersion(final int contractAccountVersion) {
      checkArgument(contractAccountVersion >= 0, "Contract account version cannot be negative");
      this.contractAccountVersion = contractAccountVersion;
      return this;
    }

    public Builder gasPrice(final Wei gasPrice) {
      this.gasPrice = gasPrice;
      return this;
    }

    public Builder inputData(final Bytes inputData) {
      this.inputData = inputData;
      return this;
    }

    public Builder sender(final Address sender) {
      this.sender = sender;
      return this;
    }

    public Builder value(final Wei value) {
      this.value = value;
      return this;
    }

    public Builder apparentValue(final Wei apparentValue) {
      this.apparentValue = apparentValue;
      return this;
    }

    public Builder code(final Code code) {
      this.code = code;
      return this;
    }

    public Builder blockHeader(final ProcessableBlockHeader blockHeader) {
      this.blockHeader = blockHeader;
      return this;
    }

    public Builder depth(final int depth) {
      this.depth = depth;
      return this;
    }

    public Builder isStatic(final boolean isStatic) {
      this.isStatic = isStatic;
      return this;
    }

    public Builder maxStackSize(final int maxStackSize) {
      this.maxStackSize = maxStackSize;
      return this;
    }

    public Builder completer(final Consumer<MessageFrame> completer) {
      this.completer = completer;
      return this;
    }

    public Builder miningBeneficiary(final Address miningBeneficiary) {
      this.miningBeneficiary = miningBeneficiary;
      return this;
    }

    public Builder blockHashLookup(final BlockHashLookup blockHashLookup) {
      this.blockHashLookup = blockHashLookup;
      return this;
    }

    public Builder isPersistingPrivateState(final Boolean isPersistingPrivateState) {
      this.isPersistingPrivateState = isPersistingPrivateState;
      return this;
    }

    public Builder privateMetadataUpdater(final PrivateMetadataUpdater privateMetadataUpdater) {
      this.privateMetadataUpdater = privateMetadataUpdater;
      return this;
    }

    public Builder transactionHash(final Hash transactionHash) {
      this.transactionHash = transactionHash;
      return this;
    }

    public Builder reason(final Bytes reason) {
      this.reason = Optional.ofNullable(reason);
      return this;
    }

    private void validate() {
      checkState(type != null, "Missing message frame type");
      checkState(blockchain != null, "Missing message frame blockchain");
      checkState(messageFrameStack != null, "Missing message frame message frame stack");
      checkState(returnStack != null, "Missing return stack");
      checkState(worldState != null, "Missing message frame world state");
      checkState(initialGas != null, "Missing message frame initial getGasRemaining");
      checkState(address != null, "Missing message frame recipient");
      checkState(originator != null, "Missing message frame originator");
      checkState(contract != null, "Missing message frame contract");
      checkState(gasPrice != null, "Missing message frame getGasRemaining price");
      checkState(inputData != null, "Missing message frame input data");
      checkState(sender != null, "Missing message frame sender");
      checkState(value != null, "Missing message frame value");
      checkState(apparentValue != null, "Missing message frame apparent value");
      checkState(code != null, "Missing message frame code");
      checkState(blockHeader != null, "Missing message frame block header");
      checkState(depth > -1, "Missing message frame depth");
      checkState(completer != null, "Missing message frame completer");
      checkState(miningBeneficiary != null, "Missing mining beneficiary");
      checkState(blockHashLookup != null, "Missing block hash lookup");
      checkState(isPersistingPrivateState != null, "Missing isPersistingPrivateState");
      checkState(contractAccountVersion != -1, "Missing contractAccountVersion");
    }

    public MessageFrame build() {
      validate();

      return new MessageFrame(
          type,
          blockchain,
          messageFrameStack,
          returnStack,
          worldState,
          initialGas,
          address,
          originator,
          contract,
          contractAccountVersion,
          gasPrice,
          inputData,
          sender,
          value,
          apparentValue,
          code,
          blockHeader,
          depth,
          isStatic,
          completer,
          miningBeneficiary,
          blockHashLookup,
          isPersistingPrivateState,
          privateMetadataUpdater,
          transactionHash,
          reason,
          maxStackSize);
    }
  }
}
