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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The type Reward trace. */
public class RewardTrace extends FlatTrace {

  /**
   * Instantiates a new Reward trace.
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
  protected RewardTrace(
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
    super(
        actionBuilder,
        resultBuilder,
        subtraces,
        traceAddress,
        type,
        blockNumber,
        blockHash,
        transactionPosition,
        transactionHash,
        error);
  }

  /**
   * We have to override the {@link FlatTrace} method because in the case of a reward the
   * transactionHash value must be returned even if it is null
   *
   * @return transactionHash
   */
  @Override
  @JsonInclude(ALWAYS)
  public String getTransactionHash() {
    return super.getTransactionHash();
  }

  /**
   * We have to override the {@link FlatTrace} method because in the case of a reward the
   * transactionPosition value must be returned even if it is null
   *
   * @return transactionPosition
   */
  @Override
  @JsonInclude(ALWAYS)
  public Integer getTransactionPosition() {
    return super.getTransactionPosition();
  }

  /**
   * Builder builder.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** The type Builder. */
  public static final class Builder extends FlatTrace.Builder {

    /** Instantiates a new Builder. */
    public Builder() {
      super();
    }

    @Override
    public RewardTrace build() {
      return new RewardTrace(
          getActionBuilder(),
          getResultBuilder(),
          getSubtraces(),
          getTraceAddress(),
          getType(),
          getBlockNumber(),
          getBlockHash(),
          getTransactionPosition(),
          getTransactionHash(),
          getError());
    }
  }
}
