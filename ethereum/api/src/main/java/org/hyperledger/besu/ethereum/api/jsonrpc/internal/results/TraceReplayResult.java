/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff.StateDiffTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTrace;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Trace replay result. */
@JsonPropertyOrder({"output", "revertReason", "stateDiff", "trace", "transactionHash", "vmTrace"})
public class TraceReplayResult {
  private final String output;
  private final String revertReason;
  private final StateDiffTrace stateDiff;
  private final List<FlatTrace> traces;
  private final VmTrace vmTrace;
  private final String transactionHash;

  /**
   * Instantiates a new Trace replay result.
   *
   * @param output the output
   * @param revertReason the revert reason
   * @param stateDiff the state diff
   * @param traces the traces
   * @param transactionHash the transaction hash
   * @param vmTrace the vm trace
   */
  public TraceReplayResult(
      final String output,
      final String revertReason,
      final StateDiffTrace stateDiff,
      final List<FlatTrace> traces,
      final String transactionHash,
      final VmTrace vmTrace) {
    this.output = output;
    this.revertReason = revertReason;
    this.stateDiff = stateDiff;
    this.traces = traces;
    this.transactionHash = transactionHash;
    this.vmTrace = vmTrace;
  }

  /**
   * Gets output.
   *
   * @return the output
   */
  @JsonGetter(value = "output")
  public String getOutput() {
    return output;
  }

  /**
   * Gets revert reason.
   *
   * @return the revert reason
   */
  @JsonInclude(Include.NON_NULL)
  @JsonGetter(value = "revertReason")
  public String getRevertReason() {
    return revertReason;
  }

  /**
   * Gets state diff.
   *
   * @return the state diff
   */
  @JsonGetter(value = "stateDiff")
  public StateDiffTrace getStateDiff() {
    return stateDiff;
  }

  /**
   * Gets traces.
   *
   * @return the traces
   */
  @JsonGetter(value = "trace")
  public List<FlatTrace> getTraces() {
    return traces;
  }

  /**
   * Gets vm trace.
   *
   * @return the vm trace
   */
  @JsonGetter(value = "vmTrace")
  public VmTrace getVmTrace() {
    return vmTrace;
  }

  /**
   * Gets transaction hash.
   *
   * @return the transaction hash
   */
  @JsonGetter(value = "transactionHash")
  public String getTransactionHash() {
    return transactionHash;
  }

  /**
   * Creates a new Builder instance.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** The type Builder. */
  public static class Builder {
    private String output;
    private String revertReason;
    private StateDiffTrace stateDiff;
    private final List<FlatTrace> traces = new ArrayList<>();
    private VmTrace vmTrace;
    private String transactionHash;

    /** Instantiates a new Builder. */
    private Builder() {}

    /**
     * Build trace replay result.
     *
     * @return the trace replay result
     */
    public TraceReplayResult build() {
      return new TraceReplayResult(
          output, revertReason, stateDiff, traces, transactionHash, vmTrace);
    }

    /**
     * Output.
     *
     * @param output the output
     */
    public void output(final String output) {
      this.output = output;
    }

    /**
     * Revert reason.
     *
     * @param revertReason the revert reason
     */
    public void revertReason(final String revertReason) {
      this.revertReason = revertReason;
    }

    /**
     * State diff.
     *
     * @param stateDiff the state diff
     */
    public void stateDiff(final StateDiffTrace stateDiff) {
      this.stateDiff = stateDiff;
    }

    /**
     * Transaction hash.
     *
     * @param transactionHash the transaction hash
     */
    public void transactionHash(final String transactionHash) {
      this.transactionHash = transactionHash;
    }

    /**
     * Add trace.
     *
     * @param trace the trace
     */
    public void addTrace(final FlatTrace trace) {
      traces.add(trace);
    }

    /**
     * Vm trace.
     *
     * @param vmTrace the vm trace
     */
    public void vmTrace(final VmTrace vmTrace) {
      this.vmTrace = vmTrace;
    }
  }
}
