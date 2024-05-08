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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.debug.TraceFrame;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Action. */
@JsonInclude(NON_NULL)
@JsonPropertyOrder({
  "creationMethod",
  "callType",
  "from",
  "gas",
  "input",
  "to",
  "init",
  "author",
  "rewardType",
  "value",
  "address",
  "balance",
  "refundAddress",
})
public class Action {

  private final String creationMethod;
  private final String callType;
  private final String from;
  private final String gas;
  private final String input;
  private final String to;
  private final String init;
  private final String value;
  private final String address;
  private final String balance;
  private final String refundAddress;
  private final String author;
  private final String rewardType;

  private Action(
      final String creationMethod,
      final String callType,
      final String from,
      final String gas,
      final String input,
      final String to,
      final String init,
      final String value,
      final String address,
      final String balance,
      final String refundAddress,
      final String author,
      final String rewardType) {
    this.creationMethod = creationMethod;
    this.callType = callType;
    this.from = from;
    this.gas = gas;
    this.input = input;
    this.to = to;
    this.init = init;
    this.value = value;
    this.address = address;
    this.balance = balance;
    this.refundAddress = refundAddress;
    this.author = author;
    this.rewardType = rewardType;
  }

  /**
   * Instantiates new Builder.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets creation method.
   *
   * @return the creation method
   */
  public String getCreationMethod() {
    return creationMethod;
  }

  /**
   * Gets call type.
   *
   * @return the call type
   */
  public String getCallType() {
    return callType;
  }

  /**
   * Gets from.
   *
   * @return the from
   */
  public String getFrom() {
    return from;
  }

  /**
   * Gets gas.
   *
   * @return the gas
   */
  public String getGas() {
    return gas;
  }

  /**
   * Gets input.
   *
   * @return the input
   */
  public String getInput() {
    return input;
  }

  /**
   * Gets to.
   *
   * @return the to
   */
  public String getTo() {
    return to;
  }

  /**
   * Gets value.
   *
   * @return the value
   */
  public String getValue() {
    return value;
  }

  /**
   * Gets init.
   *
   * @return the init
   */
  public String getInit() {
    return init;
  }

  /**
   * Gets address.
   *
   * @return the address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Gets balance.
   *
   * @return the balance
   */
  public String getBalance() {
    return balance;
  }

  /**
   * Gets refund address.
   *
   * @return the refund address
   */
  public String getRefundAddress() {
    return refundAddress;
  }

  /**
   * Gets author.
   *
   * @return the author
   */
  public String getAuthor() {
    return author;
  }

  /**
   * Gets reward type.
   *
   * @return the reward type
   */
  public String getRewardType() {
    return rewardType;
  }

  /** The type Builder. */
  public static final class Builder {
    private String creationMethod;
    private String callType;
    private String from;
    private String gas;
    private String input;
    private String to;
    private String init;
    private String value;
    private String address;
    private String balance;
    private String refundAddress;
    private String author;
    private String rewardType;

    private Builder() {}

    /**
     * Of builder.
     *
     * @param action the action
     * @return the builder
     */
    public static Builder of(final Action action) {
      final Builder builder = new Builder();
      builder.creationMethod = action.creationMethod;
      builder.callType = action.callType;
      builder.from = action.from;
      builder.gas = action.gas;
      builder.input = action.input;
      builder.to = action.to;
      builder.init = action.init;
      builder.value = action.value;
      builder.address = action.address;
      builder.refundAddress = action.refundAddress;
      builder.balance = action.balance;
      builder.author = action.author;
      builder.rewardType = action.rewardType;
      return builder;
    }

    /**
     * From builder.
     *
     * @param trace the trace
     * @return the builder
     */
    public static Builder from(final TransactionTrace trace) {
      final Builder builder =
          new Builder()
              .from(trace.getTransaction().getSender().toHexString())
              .value(Quantity.create(trace.getTransaction().getValue()));
      if (!trace.getTraceFrames().isEmpty()) {
        final TraceFrame traceFrame = trace.getTraceFrames().get(0);
        builder.gas(
            "0x"
                + Long.toHexString(
                    traceFrame.getGasRemaining()
                        + (traceFrame.getPrecompiledGasCost().orElse(0L))));
      }
      return builder;
    }

    /**
     * Creation method builder.
     *
     * @param creationMethod the creation method
     * @return the builder
     */
    public Builder creationMethod(final String creationMethod) {
      this.creationMethod = creationMethod;
      return this;
    }

    /**
     * Call type builder.
     *
     * @param callType the call type
     * @return the builder
     */
    public Builder callType(final String callType) {
      this.callType = callType;
      return this;
    }

    /**
     * Gets call type.
     *
     * @return the call type
     */
    public String getCallType() {
      return callType;
    }

    /**
     * From builder.
     *
     * @param from the from
     * @return the builder
     */
    public Builder from(final String from) {
      this.from = from;
      return this;
    }

    /**
     * Gets from.
     *
     * @return the from
     */
    public String getFrom() {
      return from;
    }

    /**
     * Gas builder.
     *
     * @param gas the gas
     * @return the builder
     */
    public Builder gas(final String gas) {
      this.gas = gas;
      return this;
    }

    /**
     * Input builder.
     *
     * @param input the input
     * @return the builder
     */
    public Builder input(final String input) {
      this.input = input;
      return this;
    }

    /**
     * To builder.
     *
     * @param to the to
     * @return the builder
     */
    public Builder to(final String to) {
      this.to = to;
      return this;
    }

    /**
     * Author builder.
     *
     * @param author the author
     * @return the builder
     */
    public Builder author(final String author) {
      this.author = author;
      return this;
    }

    /**
     * Reward type builder.
     *
     * @param rewardType the reward type
     * @return the builder
     */
    public Builder rewardType(final String rewardType) {
      this.rewardType = rewardType;
      return this;
    }

    /**
     * Gets to.
     *
     * @return the to
     */
    public String getTo() {
      return to;
    }

    /**
     * Init builder.
     *
     * @param init the init
     * @return the builder
     */
    public Builder init(final String init) {
      this.init = init;
      return this;
    }

    /**
     * Value builder.
     *
     * @param value the value
     * @return the builder
     */
    public Builder value(final String value) {
      this.value = value;
      return this;
    }

    /**
     * Address builder.
     *
     * @param address the address
     * @return the builder
     */
    public Builder address(final String address) {
      this.address = address;
      return this;
    }

    /**
     * Balance builder.
     *
     * @param balance the balance
     * @return the builder
     */
    public Builder balance(final String balance) {
      this.balance = balance;
      return this;
    }

    /**
     * Refund address builder.
     *
     * @param refundAddress the refund address
     * @return the builder
     */
    Builder refundAddress(final String refundAddress) {
      this.refundAddress = refundAddress;
      return this;
    }

    /**
     * Gets gas.
     *
     * @return the gas
     */
    public String getGas() {
      return gas;
    }

    /**
     * Build action.
     *
     * @return the action
     */
    public Action build() {
      return new Action(
          creationMethod,
          callType,
          from,
          gas,
          input,
          to,
          init,
          value,
          address,
          balance,
          refundAddress,
          author,
          rewardType);
    }
  }
}
