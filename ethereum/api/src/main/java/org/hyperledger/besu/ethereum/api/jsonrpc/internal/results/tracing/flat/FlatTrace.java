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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Flat trace. */
@JsonPropertyOrder({
  "action",
  "blockHash",
  "blockNumber",
  "result",
  "error",
  "revertReason",
  "subtraces",
  "traceAddress",
  "transactionHash",
  "transactionPosition",
  "type"
})
public class FlatTrace implements Trace {
  private final Action action;
  private final Result result;
  private final Long blockNumber;
  private final String blockHash;
  private final Integer transactionPosition;
  private final String transactionHash;
  private final Optional<String> error;
  private final String revertReason;
  private final int subtraces;
  private final List<Integer> traceAddress;
  private final String type;

  /**
   * Instantiates a new Flat trace.
   *
   * @param actionBuilder the action builder
   * @param resultBuilder the result builder
   * @param subtraces the subtraces
   * @param traceAddress the trace address
   * @param type the type
   * @param blockNumber the block number
   * @param blockHash the block hash
   * @param transactionPosition the transaction position
   * @param transactionHash the transaction hash
   * @param error the error
   */
  protected FlatTrace(
      final Action.Builder actionBuilder,
      final Result.Builder resultBuilder,
      final int subtraces,
      final List<Integer> traceAddress,
      final String type,
      final Long blockNumber,
      final String blockHash,
      final Integer transactionPosition,
      final String transactionHash,
      final Optional<String> error) {
    this(
        actionBuilder != null ? actionBuilder.build() : null,
        resultBuilder != null ? resultBuilder.build() : null,
        subtraces,
        traceAddress,
        type,
        blockNumber,
        blockHash,
        transactionPosition,
        transactionHash,
        error,
        null);
  }

  /**
   * Instantiates a new Flat trace.
   *
   * @param actionBuilder the action builder
   * @param resultBuilder the result builder
   * @param subtraces the subtraces
   * @param traceAddress the trace address
   * @param type the type
   * @param blockNumber the block number
   * @param blockHash the block hash
   * @param transactionPosition the transaction position
   * @param transactionHash the transaction hash
   * @param error the error
   * @param revertReason the revert reason
   */
  protected FlatTrace(
      final Action.Builder actionBuilder,
      final Result.Builder resultBuilder,
      final int subtraces,
      final List<Integer> traceAddress,
      final String type,
      final Long blockNumber,
      final String blockHash,
      final Integer transactionPosition,
      final String transactionHash,
      final Optional<String> error,
      final String revertReason) {
    this(
        actionBuilder != null ? actionBuilder.build() : null,
        resultBuilder != null ? resultBuilder.build() : null,
        subtraces,
        traceAddress,
        type,
        blockNumber,
        blockHash,
        transactionPosition,
        transactionHash,
        error,
        revertReason);
  }

  /**
   * Instantiates a new Flat trace.
   *
   * @param action the action
   * @param result the result
   * @param subtraces the subtraces
   * @param traceAddress the trace address
   * @param type the type
   * @param blockNumber the block number
   * @param blockHash the block hash
   * @param transactionPosition the transaction position
   * @param transactionHash the transaction hash
   * @param error the error
   * @param revertReason the revert reason
   */
  protected FlatTrace(
      final Action action,
      final Result result,
      final int subtraces,
      final List<Integer> traceAddress,
      final String type,
      final Long blockNumber,
      final String blockHash,
      final Integer transactionPosition,
      final String transactionHash,
      final Optional<String> error,
      final String revertReason) {
    this.action = action;
    this.result = result;
    this.subtraces = subtraces;
    this.traceAddress = traceAddress;
    this.type = type;
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.transactionPosition = transactionPosition;
    this.transactionHash = transactionHash;
    this.error = error;
    this.revertReason = revertReason;
  }

  /**
   * Fresh builder builder.
   *
   * @param transactionTrace the transaction trace
   * @return the builder
   */
  static Builder freshBuilder(final TransactionTrace transactionTrace) {
    return FlatTrace.builder()
        .resultBuilder(Result.builder())
        .actionBuilder(Action.Builder.from(transactionTrace));
  }

  /**
   * Gets action.
   *
   * @return the action
   */
  public Action getAction() {
    return action;
  }

  /**
   * Gets block number.
   *
   * @return the block number
   */
  @JsonInclude(NON_NULL)
  public Long getBlockNumber() {
    return blockNumber;
  }

  /**
   * Gets block hash.
   *
   * @return the block hash
   */
  @JsonInclude(NON_NULL)
  public String getBlockHash() {
    return blockHash;
  }

  /**
   * Gets transaction hash.
   *
   * @return the transaction hash
   */
  @JsonInclude(NON_NULL)
  public String getTransactionHash() {
    return transactionHash;
  }

  /**
   * Gets transaction position.
   *
   * @return the transaction position
   */
  @JsonInclude(NON_NULL)
  public Integer getTransactionPosition() {
    return transactionPosition;
  }

  /**
   * Gets error.
   *
   * @return the error
   */
  @JsonInclude(NON_NULL)
  public String getError() {
    return error.orElse(null);
  }

  /**
   * Gets revert reason.
   *
   * @return the revert reason
   */
  @JsonInclude(NON_NULL)
  public String getRevertReason() {
    return revertReason;
  }

  /**
   * This ridiculous construction returns a true "null" when we have a value in error, resulting in
   * jackson not serializing it, or a wrapped reference of either null or the value, resulting in
   * jackson serializing a null if we don't have an error.
   *
   * <p>This is done for binary compatibility: we need either an absent value, a present null value,
   * or a real value. And since Java Optionals refuse to hold nulls (by design) an atomic reference
   * is used instead.
   *
   * @return the jackson optimized result
   */
  @JsonInclude(NON_NULL)
  public AtomicReference<Result> getResult() {
    return (error.isPresent()) ? null : new AtomicReference<>(result);
  }

  /**
   * Gets subtraces.
   *
   * @return the subtraces
   */
  public int getSubtraces() {
    return subtraces;
  }

  /**
   * Gets trace address.
   *
   * @return the trace address
   */
  public List<Integer> getTraceAddress() {
    return traceAddress;
  }

  /**
   * Gets type.
   *
   * @return the type
   */
  public String getType() {
    return type;
  }

  /**
   * Builder builder.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** The type Context. */
  public static class Context {

    private final Builder builder;
    private long gasUsed = 0;
    private boolean createOp;

    /**
     * Instantiates a new Context.
     *
     * @param builder the builder
     */
    Context(final Builder builder) {
      this.builder = builder;
    }

    /**
     * Gets builder.
     *
     * @return the builder
     */
    public Builder getBuilder() {
      return builder;
    }

    /**
     * Inc gas used.
     *
     * @param gas the gas
     */
    void incGasUsed(final long gas) {
      setGasUsed(gasUsed + gas);
    }

    /**
     * Dec gas used.
     *
     * @param gas the gas
     */
    void decGasUsed(final long gas) {
      setGasUsed(gasUsed - gas);
    }

    /**
     * Gets gas used.
     *
     * @return the gas used
     */
    public long getGasUsed() {
      return gasUsed;
    }

    /**
     * Sets gas used.
     *
     * @param gasUsed the gas used
     */
    public void setGasUsed(final long gasUsed) {
      this.gasUsed = gasUsed;
      builder.getResultBuilder().gasUsed("0x" + Long.toHexString(gasUsed));
    }

    /**
     * Is create op boolean.
     *
     * @return the boolean
     */
    boolean isCreateOp() {
      return createOp;
    }

    /**
     * Sets create op.
     *
     * @param createOp the create op
     */
    void setCreateOp(final boolean createOp) {
      this.createOp = createOp;
    }
  }

  /** The type Builder. */
  public static class Builder {

    private Action.Builder actionBuilder;
    private Result.Builder resultBuilder;
    private int subtraces;
    private List<Integer> traceAddress = new ArrayList<>();
    private String type = "call";
    private Long blockNumber;
    private String blockHash;
    private String transactionHash;
    private Integer transactionPosition;
    private Optional<String> error = Optional.empty();
    private String revertReason;

    /** Instantiates a new Builder. */
    protected Builder() {}

    /**
     * Result builder builder.
     *
     * @param resultBuilder the result builder
     * @return the builder
     */
    Builder resultBuilder(final Result.Builder resultBuilder) {
      this.resultBuilder = resultBuilder;
      return this;
    }

    /**
     * Action builder builder.
     *
     * @param actionBuilder the action builder
     * @return the builder
     */
    Builder actionBuilder(final Action.Builder actionBuilder) {
      this.actionBuilder = actionBuilder;
      return this;
    }

    /**
     * Trace address builder.
     *
     * @param traceAddress the trace address
     * @return the builder
     */
    public Builder traceAddress(final List<Integer> traceAddress) {
      this.traceAddress = traceAddress;
      return this;
    }

    /**
     * Type builder.
     *
     * @param type the type
     * @return the builder
     */
    public Builder type(final String type) {
      this.type = type;
      return this;
    }

    /**
     * Block number builder.
     *
     * @param blockNumber the block number
     * @return the builder
     */
    public Builder blockNumber(final Long blockNumber) {
      this.blockNumber = blockNumber;
      return this;
    }

    /**
     * Block hash builder.
     *
     * @param blockHash the block hash
     * @return the builder
     */
    public Builder blockHash(final String blockHash) {
      this.blockHash = blockHash;
      return this;
    }

    /**
     * Transaction hash builder.
     *
     * @param transactionHash the transaction hash
     * @return the builder
     */
    public Builder transactionHash(final String transactionHash) {
      this.transactionHash = transactionHash;
      return this;
    }

    /**
     * Transaction position builder.
     *
     * @param transactionPosition the transaction position
     * @return the builder
     */
    public Builder transactionPosition(final Integer transactionPosition) {
      this.transactionPosition = transactionPosition;
      return this;
    }

    /**
     * Error builder.
     *
     * @param error the error
     * @return the builder
     */
    public Builder error(final Optional<String> error) {
      this.error = error;
      return this;
    }

    /**
     * Revert reason builder.
     *
     * @param revertReason the revert reason
     * @return the builder
     */
    public Builder revertReason(final String revertReason) {
      this.revertReason = revertReason;
      return this;
    }

    /**
     * Gets type.
     *
     * @return the type
     */
    public String getType() {
      return type;
    }

    /**
     * Gets subtraces.
     *
     * @return the subtraces
     */
    public int getSubtraces() {
      return subtraces;
    }

    /**
     * Gets trace address.
     *
     * @return the trace address
     */
    public List<Integer> getTraceAddress() {
      return traceAddress;
    }

    /**
     * Gets block number.
     *
     * @return the block number
     */
    public Long getBlockNumber() {
      return blockNumber;
    }

    /**
     * Gets block hash.
     *
     * @return the block hash
     */
    public String getBlockHash() {
      return blockHash;
    }

    /**
     * Gets transaction hash.
     *
     * @return the transaction hash
     */
    public String getTransactionHash() {
      return transactionHash;
    }

    /**
     * Gets transaction position.
     *
     * @return the transaction position
     */
    public Integer getTransactionPosition() {
      return transactionPosition;
    }

    /**
     * Gets error.
     *
     * @return the error
     */
    public Optional<String> getError() {
      return error;
    }

    /**
     * Gets revert reason.
     *
     * @return the revert reason
     */
    public String getRevertReason() {
      return revertReason;
    }

    /** Inc sub traces. */
    void incSubTraces() {
      this.subtraces++;
    }

    /**
     * Build flat trace.
     *
     * @return the flat trace
     */
    public FlatTrace build() {
      return new FlatTrace(
          actionBuilder,
          resultBuilder,
          subtraces,
          traceAddress,
          type,
          blockNumber,
          blockHash,
          transactionPosition,
          transactionHash,
          error,
          revertReason);
    }

    /**
     * Gets result builder.
     *
     * @return the result builder
     */
    public Result.Builder getResultBuilder() {
      return resultBuilder;
    }

    /**
     * Gets action builder.
     *
     * @return the action builder
     */
    public Action.Builder getActionBuilder() {
      return actionBuilder;
    }
  }
}
