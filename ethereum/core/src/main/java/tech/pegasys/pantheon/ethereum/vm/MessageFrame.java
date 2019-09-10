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
package tech.pegasys.pantheon.ethereum.vm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractMessageProcessor;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;
import tech.pegasys.pantheon.util.uint.UInt256Value;

import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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
  private BytesValue output;
  private BytesValue returnData;
  private final boolean isStatic;

  // Transaction substate fields.
  private final LogSeries logs;
  private Gas gasRefund;
  private final Set<Address> selfDestructs;
  private final Map<Address, Wei> refunds;

  // Execution Environment fields.
  private final Address recipient;
  private final Address originator;
  private final Address contract;
  private final int contractAccountVersion;
  private final Wei gasPrice;
  private final BytesValue inputData;
  private final Address sender;
  private final Wei value;
  private final Wei apparentValue;
  private final Code code;
  private final ProcessableBlockHeader blockHeader;
  private final int depth;
  private final Deque<MessageFrame> messageFrameStack;
  private final Address miningBeneficiary;
  private final Boolean isPersistingState;
  private Optional<BytesValue> revertReason;

  // Miscellaneous fields.
  private final EnumSet<ExceptionalHaltReason> exceptionalHaltReasons =
      EnumSet.noneOf(ExceptionalHaltReason.class);
  private Operation currentOperation;
  private final Consumer<MessageFrame> completer;

  public static Builder builder() {
    return new Builder();
  }

  private MessageFrame(
      final Type type,
      final Blockchain blockchain,
      final Deque<MessageFrame> messageFrameStack,
      final WorldUpdater worldState,
      final Gas initialGas,
      final Address recipient,
      final Address originator,
      final Address contract,
      final int contractAccountVersion,
      final Wei gasPrice,
      final BytesValue inputData,
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
      final Boolean isPersistingState,
      final Optional<BytesValue> revertReason,
      final int maxStackSize) {
    this.type = type;
    this.blockchain = blockchain;
    this.messageFrameStack = messageFrameStack;
    this.worldState = worldState;
    this.gasRemaining = initialGas;
    this.blockHashLookup = blockHashLookup;
    this.maxStackSize = maxStackSize;
    this.pc = 0;
    this.memory = new Memory();
    this.stack = new PreAllocatedOperandStack(maxStackSize);
    this.output = BytesValue.EMPTY;
    this.returnData = BytesValue.EMPTY;
    this.logs = LogSeries.empty();
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
    this.isPersistingState = isPersistingState;
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
  public BytesValue getOutputData() {
    return output;
  }

  /**
   * Set the output data.
   *
   * @param output The output data
   */
  public void setOutputData(final BytesValue output) {
    this.output = output;
  }

  /** Clears the output data buffer. */
  public void clearOutputData() {
    setOutputData(BytesValue.EMPTY);
  }

  /**
   * Return the return data.
   *
   * @return the return data
   */
  public BytesValue getReturnData() {
    return returnData;
  }

  /**
   * Set the return data.
   *
   * @param returnData The return data
   */
  public void setReturnData(final BytesValue returnData) {
    this.returnData = returnData;
  }

  /** Clear the return data buffer. */
  public void clearReturnData() {
    setReturnData(BytesValue.EMPTY);
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
   * @throws IllegalStateException if the stack does not contain enough items
   */
  public void popStackItems(final int n) {
    stack.bulkPop(n);
  }

  /**
   * Pushes the corresponding item onto the top of the stack
   *
   * @param value The value to push onto the stack.
   * @throws IllegalStateException if the stack is full
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
  public UInt256 calculateMemoryExpansion(
      final UInt256Value<?> offset, final UInt256Value<?> length) {
    return memory.calculateNewActiveWords(offset, length);
  }

  /**
   * Expands memory to accomodate the specified memory access.
   *
   * @param offset The offset in memory
   * @param length The length of the memory access
   */
  public void expandMemory(final long offset, final int length) {
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
  public Optional<BytesValue> getRevertReason() {
    return revertReason;
  }

  public void setRevertReason(final BytesValue revertReason) {
    this.revertReason = Optional.ofNullable(revertReason);
  }

  /**
   * Read bytes in memory.
   *
   * @param offset The offset in memory
   * @param length The length of the bytes to read
   * @return The bytes in the specified range
   */
  public BytesValue readMemory(final UInt256 offset, final UInt256 length) {
    return memory.getBytes(offset, length);
  }

  /**
   * Write byte to memory
   *
   * @param offset The offset in memory
   * @param value The value to set in memory
   */
  public void writeMemory(final UInt256 offset, final byte value) {
    memory.setByte(offset, value);
  }

  /**
   * Write bytes to memory
   *
   * @param offset The offset in memory
   * @param length The length of the bytes to write
   * @param value The value to write
   */
  public void writeMemory(final UInt256 offset, final UInt256 length, final BytesValue value) {
    memory.setBytes(offset, length, value);
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
      final UInt256 offset,
      final UInt256 sourceOffset,
      final UInt256 length,
      final BytesValue value) {
    memory.setBytes(offset, sourceOffset, length, value);
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
  public void addLogs(final LogSeries logs) {
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
  public LogSeries getLogs() {
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
  public Map<Address, Wei> getRefunds() {
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
  public BytesValue getInputData() {
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

  public EnumSet<ExceptionalHaltReason> getExceptionalHaltReasons() {
    return exceptionalHaltReasons;
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

  public int getMaxStackSize() {
    return maxStackSize;
  }

  /**
   * Returns whether Message calls will be persisted
   *
   * @return whether Message calls will be persisted
   */
  public Boolean isPersistingState() {
    return isPersistingState;
  }

  public void setCurrentOperation(final Operation currentOperation) {
    this.currentOperation = currentOperation;
  }

  public int getContractAccountVersion() {
    return contractAccountVersion;
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
    private BytesValue inputData;
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
    private Boolean isPersistingState = false;
    private Optional<BytesValue> reason = Optional.empty();

    public Builder type(final Type type) {
      this.type = type;
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

    public Builder inputData(final BytesValue inputData) {
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

    public Builder isPersistingState(final Boolean isPersistingState) {
      this.isPersistingState = isPersistingState;
      return this;
    }

    public Builder reason(final BytesValue reason) {
      this.reason = Optional.ofNullable(reason);
      return this;
    }

    private void validate() {
      checkState(type != null, "Missing message frame type");
      checkState(blockchain != null, "Missing message frame blockchain");
      checkState(messageFrameStack != null, "Missing message frame message frame stack");
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
      checkState(isPersistingState != null, "Missing isPersistingState");
      checkState(contractAccountVersion != -1, "Missing contractAccountVersion");
    }

    public MessageFrame build() {
      validate();

      return new MessageFrame(
          type,
          blockchain,
          messageFrameStack,
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
          isPersistingState,
          reason,
          maxStackSize);
    }
  }
}
