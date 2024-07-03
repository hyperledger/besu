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

  static Builder freshBuilder(final TransactionTrace transactionTrace) {
    return FlatTrace.builder()
        .resultBuilder(Result.builder())
        .actionBuilder(Action.Builder.from(transactionTrace));
  }

  public Action getAction() {
    return action;
  }

  @JsonInclude(NON_NULL)
  public Long getBlockNumber() {
    return blockNumber;
  }

  @JsonInclude(NON_NULL)
  public String getBlockHash() {
    return blockHash;
  }

  @JsonInclude(NON_NULL)
  public String getTransactionHash() {
    return transactionHash;
  }

  @JsonInclude(NON_NULL)
  public Integer getTransactionPosition() {
    return transactionPosition;
  }

  @JsonInclude(NON_NULL)
  public String getError() {
    return error.orElse(null);
  }

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

  public int getSubtraces() {
    return subtraces;
  }

  public List<Integer> getTraceAddress() {
    return traceAddress;
  }

  public String getType() {
    return type;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Context {

    private final Builder builder;
    private long gasUsed = 0;
    private boolean createOp;

    Context(final Builder builder) {
      this.builder = builder;
    }

    public Builder getBuilder() {
      return builder;
    }

    void incGasUsed(final long gas) {
      setGasUsed(gasUsed + gas);
    }

    void decGasUsed(final long gas) {
      setGasUsed(gasUsed - gas);
    }

    public long getGasUsed() {
      return gasUsed;
    }

    public void setGasUsed(final long gasUsed) {
      this.gasUsed = gasUsed;
      builder.getResultBuilder().gasUsed("0x" + Long.toHexString(gasUsed));
    }

    boolean isCreateOp() {
      return createOp;
    }

    void setCreateOp(final boolean createOp) {
      this.createOp = createOp;
    }
  }

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

    protected Builder() {}

    Builder resultBuilder(final Result.Builder resultBuilder) {
      this.resultBuilder = resultBuilder;
      return this;
    }

    Builder actionBuilder(final Action.Builder actionBuilder) {
      this.actionBuilder = actionBuilder;
      return this;
    }

    public Builder traceAddress(final List<Integer> traceAddress) {
      this.traceAddress = traceAddress;
      return this;
    }

    public Builder type(final String type) {
      this.type = type;
      return this;
    }

    public Builder blockNumber(final Long blockNumber) {
      this.blockNumber = blockNumber;
      return this;
    }

    public Builder blockHash(final String blockHash) {
      this.blockHash = blockHash;
      return this;
    }

    public Builder transactionHash(final String transactionHash) {
      this.transactionHash = transactionHash;
      return this;
    }

    public Builder transactionPosition(final Integer transactionPosition) {
      this.transactionPosition = transactionPosition;
      return this;
    }

    public Builder error(final Optional<String> error) {
      this.error = error;
      return this;
    }

    public Builder revertReason(final String revertReason) {
      this.revertReason = revertReason;
      return this;
    }

    public String getType() {
      return type;
    }

    public int getSubtraces() {
      return subtraces;
    }

    public List<Integer> getTraceAddress() {
      return traceAddress;
    }

    public Long getBlockNumber() {
      return blockNumber;
    }

    public String getBlockHash() {
      return blockHash;
    }

    public String getTransactionHash() {
      return transactionHash;
    }

    public Integer getTransactionPosition() {
      return transactionPosition;
    }

    public Optional<String> getError() {
      return error;
    }

    public String getRevertReason() {
      return revertReason;
    }

    void incSubTraces() {
      this.subtraces++;
    }

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

    public Result.Builder getResultBuilder() {
      return resultBuilder;
    }

    public Action.Builder getActionBuilder() {
      return actionBuilder;
    }
  }
}
