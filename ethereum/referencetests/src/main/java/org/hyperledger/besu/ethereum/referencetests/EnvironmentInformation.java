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
 *
 */
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.Code;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

/**
 * A wrapper for the environmental information that corresponds to a particular message call or
 * contract creation.
 *
 * <p>Note: this is meant to map to I in Section 9.3 "Execution Environment" in the Yellow Paper
 * Revision 59dccd. Its implementation will be completed as the VM implementation itself becomes
 * more complete.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentInformation {

  private final Address accountAddress;

  private final Wei accountBalance;

  private BlockHeader blockHeader;

  private final Address callerAddress;

  private final Code code;

  private final Bytes data;

  private final int depth;

  private final Wei gasPrice;

  private final Address originAddress;

  private final Wei value;

  private final long gas;

  /**
   * Public constructor.
   *
   * @param account The address of the currently executing account.
   * @param balance Balance of the account being executed
   * @param caller The caller address.
   * @param code The code to be executed.
   * @param data The data of the current environment that pertains to the input data passed with the
   * @param gas The amount of gas allocated to the transaction
   * @param gasPrice The gas price specified by the originating transaction.
   * @param origin The sender address of the original transaction. message call instruction or
   *     transaction.
   * @param value The deposited value by the instruction/transaction responsible for this execution.
   */
  @SuppressWarnings("unused") // jackson reflected constructor
  @JsonCreator
  public EnvironmentInformation(
      @JsonProperty("address") final String account,
      @JsonProperty("balance") final String balance,
      @JsonProperty("caller") final String caller,
      @JsonProperty("code") final ReferenceTestCode code,
      @JsonProperty("data") final String data,
      @JsonProperty("gas") final String gas,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("origin") final String origin,
      @JsonProperty("value") final String value) {
    this(
        code,
        0,
        account == null ? null : Address.fromHexString(account),
        balance == null ? Wei.ZERO : Wei.fromHexString(balance),
        caller == null ? null : Address.fromHexString(caller),
        origin == null ? null : Address.fromHexString(origin),
        data == null ? null : Bytes.fromHexString(data),
        value == null ? null : Wei.fromHexString(value),
        gasPrice == null ? null : Wei.fromHexString(gasPrice),
        gas == null ? 0 : Long.decode(gas));
  }

  private EnvironmentInformation(
      final Code code,
      final int depth,
      final Address accountAddress,
      final Wei accountBalance,
      final Address callerAddress,
      final Address originAddress,
      final Bytes data,
      final Wei value,
      final Wei gasPrice,
      final long gas) {
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
  }

  /**
   * Assigns the block header.
   *
   * @param blockHeader A {@link BlockHeader}.
   */
  public void setBlockHeader(final BlockHeader blockHeader) {
    this.blockHeader = blockHeader;
  }

  /**
   * Returns the block header.
   *
   * @return the block header
   */
  public BlockHeader getBlockHeader() {
    return blockHeader;
  }

  /**
   * Returns the address of the currently executing account.
   *
   * @return the address of the currently executing account.
   */
  public Address getAccountAddress() {
    return accountAddress;
  }

  /**
   * Returns the balance of the currently executing account
   *
   * @return the balance of the currently executing account
   */
  public Wei getAccountBalance() {
    return accountBalance;
  }

  /**
   * Returns address of the caller.
   *
   * @return address of the caller.
   */
  public Address getCallerAddress() {
    return callerAddress;
  }

  /**
   * Returns the call value.
   *
   * @return the call value.
   */
  public Wei getValue() {
    return value;
  }

  /**
   * Returns code to be executed.
   *
   * @return code to be executed.
   */
  public Code getCode() {
    return code;
  }

  /**
   * Returns the input data to be used.
   *
   * @return the input data to be used.
   */
  public Bytes getData() {
    return data;
  }

  /**
   * Returns the call depth of the current message-call/contract creation.
   *
   * @return the call depth of the current message-call/contract creation.
   */
  public int getDepth() {
    return depth;
  }

  /**
   * Returns the gas price specified by the originating transaction.
   *
   * @return the gas price specified by the originating transaction.
   */
  public Wei getGasPrice() {
    return gasPrice;
  }

  /**
   * Returns the amount of gas available.
   *
   * @return the amount of gas available.
   */
  public long getGas() {
    return gas;
  }

  /**
   * Returns the sender address of the original transaction.
   *
   * @return the sender address of the original transaction.
   */
  public Address getOriginAddress() {
    return originAddress;
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
        .append(callerAddress);

    return builder.toString();
  }
}
