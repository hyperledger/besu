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

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A wrapper for the environmental information that corresponds to a particular message call or
 * contract creation.
 *
 * <p>Note: this is meant to map to I in Section 9.3 "Execution Environment" in the Yellow Paper
 * Revision 59dccd. Its implementation will be completed as the VM implementation itself becomes
 * more complete.
 */
public class EnvironmentInformation {

  private final Address accountAddress;

  private final Wei accountBalance;

  private BlockHeader blockHeader;

  private final Address callerAddress;

  private final Code code;

  private final int version;

  private final BytesValue data;

  private final int depth;

  private final Wei gasPrice;

  private final Address originAddress;

  private final Wei value;

  private final Gas gas;

  /**
   * Public constructor.
   *
   * @param code The code to be executed.
   * @param account The address of the currently executing account.
   * @param caller The caller address.
   * @param origin The sender address of the original transaction.
   * @param data The data of the current environment that pertains to the input data passed with the
   *     message call instruction or transaction.
   * @param value The deposited value by the instruction/transaction responsible for this execution.
   * @param gasPrice The gas price specified by the originating transaction.
   */
  @JsonCreator
  public EnvironmentInformation(
      @JsonProperty("address") final String account,
      @JsonProperty("balance") final String balance,
      @JsonProperty("caller") final String caller,
      @JsonProperty("code") final CodeMock code,
      @JsonProperty("data") final String data,
      @JsonProperty("gas") final String gas,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("origin") final String origin,
      @JsonProperty("value") final String value,
      @JsonProperty("version") final String version) {
    this(
        code,
        0,
        account == null ? null : Address.fromHexString(account),
        balance == null ? Wei.ZERO : Wei.fromHexString(balance),
        caller == null ? null : Address.fromHexString(caller),
        origin == null ? null : Address.fromHexString(origin),
        data == null ? null : BytesValue.fromHexString(data),
        value == null ? null : Wei.fromHexString(value),
        gasPrice == null ? null : Wei.fromHexString(gasPrice),
        gas == null ? null : Gas.fromHexString(gas),
        version == null ? Account.DEFAULT_VERSION : Integer.decode(version));
  }

  private EnvironmentInformation(
      final Code code,
      final int depth,
      final Address accountAddress,
      final Wei accountBalance,
      final Address callerAddress,
      final Address originAddress,
      final BytesValue data,
      final Wei value,
      final Wei gasPrice,
      final Gas gas,
      final int version) {
    this.code = code;
    this.depth = depth;
    this.accountAddress = accountAddress;
    this.accountBalance = accountBalance;
    this.callerAddress = callerAddress;
    this.originAddress = originAddress;
    this.data = data;
    this.value = value;
    this.gasPrice = gasPrice;
    this.gas = gas;
    this.version = version;
  }

  /**
   * Assigns the block header.
   *
   * @param blockHeader A @{link BlockHeader}.
   */
  public void setBlockHeader(final BlockHeader blockHeader) {
    this.blockHeader = blockHeader;
  }

  /** @return The block header. */
  public BlockHeader getBlockHeader() {
    return blockHeader;
  }

  /** @return The address of the currently executing account. */
  public Address getAccountAddress() {
    return accountAddress;
  }

  /** @return The balance of the currently executing account */
  public Wei getAccountBalance() {
    return accountBalance;
  }

  /** @return Address of the caller. */
  public Address getCallerAddress() {
    return callerAddress;
  }

  /** @return The call value. */
  public Wei getValue() {
    return value;
  }

  /** @return Code to be executed. */
  public Code getCode() {
    return code;
  }

  /** @return The input data to be used. */
  public BytesValue getData() {
    return data;
  }

  /** @return The call depth of the current message-call/contract creation. */
  public int getDepth() {
    return depth;
  }

  /** @return The gas price specified by the originating transaction. */
  public Wei getGasPrice() {
    return gasPrice;
  }

  /** @return The amount of gas available. */
  public Gas getGas() {
    return gas;
  }

  /** @return The sender address of the original transaction. */
  public Address getOriginAddress() {
    return originAddress;
  }

  public int getVersion() {
    return version;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder
        .append("Executing ")
        .append(code.toString())
        .append("\nCode: ")
        .append(code)
        .append("\nData: ")
        .append(data)
        .append("\nAccount: ")
        .append(accountAddress)
        .append("\nBlock header: \n  ")
        .append(blockHeader.toString().replaceAll("\n", "\n  "))
        .append("\nCaller: ")
        .append(callerAddress)
        .append("\nVersion: ")
        .append(version);

    return builder.toString();
  }
}
