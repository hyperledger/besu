/*
 * Copyright Hyperledger Besu.
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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"output", "stateDiff", "trace", "vmTrace"})
public class TraceCallResult {
  private final String output;
  private final StateDiffTrace stateDiff;
  private final List<FlatTrace> traces;
  private final VmTrace vmTrace;

  public TraceCallResult(
      final String output,
      final StateDiffTrace stateDiff,
      final List<FlatTrace> traces,
      final VmTrace vmTrace) {
    this.output = output;
    this.stateDiff = stateDiff;
    this.traces = traces;
    this.vmTrace = vmTrace;
  }

  @JsonGetter(value = "output")
  public String getOutput() {
    return output;
  }

  @JsonGetter(value = "stateDiff")
  public StateDiffTrace getStateDiff() {
    return stateDiff;
  }

  @JsonGetter(value = "trace")
  public List<FlatTrace> getTraces() {
    return traces;
  }

  @JsonGetter(value = "vmTrace")
  public VmTrace getVmTrace() {
    return vmTrace;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String output;
    private StateDiffTrace stateDiff;
    private final List<FlatTrace> traces = new ArrayList<>();
    private VmTrace vmTrace;

    public TraceCallResult build() {
      return new TraceCallResult(output, stateDiff, traces, vmTrace);
    }

    public void output(final String output) {
      this.output = output;
    }

    public void stateDiff(final StateDiffTrace stateDiff) {
      this.stateDiff = stateDiff;
    }

    public void addTrace(final FlatTrace trace) {
      traces.add(trace);
    }

    public void vmTrace(final VmTrace vmTrace) {
      this.vmTrace = vmTrace;
    }
  }
}
