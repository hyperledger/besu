/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.tracing;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTrace;

import java.util.ArrayList;
import java.util.List;

public class FlatTrace implements Trace {
  private Action action;
  private Result result;
  private int subtraces;
  private List<Integer> traceAddress;
  private String type;

  private FlatTrace(
      final Action.Builder actionBuilder,
      final Result.Builder resultBuilder,
      final int subtraces,
      final List<Integer> traceAddress,
      final String type) {
    this(
        actionBuilder != null ? actionBuilder.build() : null,
        resultBuilder != null ? resultBuilder.build() : null,
        subtraces,
        traceAddress,
        type);
  }

  private FlatTrace(
      final Action action,
      final Result result,
      final int subtraces,
      final List<Integer> traceAddress,
      final String type) {
    this.action = action;
    this.result = result;
    this.subtraces = subtraces;
    this.traceAddress = traceAddress;
    this.type = type;
  }

  public static Builder freshBuilder(final TransactionTrace transactionTrace) {
    return FlatTrace.builder()
        .resultBuilder(Result.builder())
        .actionBuilder(Action.Builder.from(transactionTrace));
  }

  public Action getAction() {
    return action;
  }

  public Result getResult() {
    return result;
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

    private Builder builder;
    private boolean returned;
    private boolean isSubtrace = false;

    public Context(final Builder builder) {
      this(builder, false);
    }

    Context(final Builder builder, final boolean returned) {
      this.builder = builder;
      this.returned = returned;
    }

    public Builder getBuilder() {
      return builder;
    }

    public boolean isReturned() {
      return returned;
    }

    public boolean isSubtrace() {
      return isSubtrace;
    }

    public Context subTrace() {
      this.isSubtrace = true;
      return this;
    }

    public void markAsReturned() {
      this.returned = true;
    }
  }

  public static final class Builder {

    private Action.Builder actionBuilder;
    private Result.Builder resultBuilder;
    private int subtraces;
    private List<Integer> traceAddress = new ArrayList<>();
    private String type = "call";

    private Builder() {}

    public Builder resultBuilder(final Result.Builder resultBuilder) {
      this.resultBuilder = resultBuilder;
      return this;
    }

    public Builder actionBuilder(final Action.Builder actionBuilder) {
      this.actionBuilder = actionBuilder;
      return this;
    }

    public Builder subtraces(final int subtraces) {
      this.subtraces = subtraces;
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

    public Builder incSubTraces() {
      return incSubTraces(1);
    }

    public Builder incSubTraces(final int n) {
      this.subtraces += n;
      return this;
    }

    public FlatTrace build() {
      return new FlatTrace(actionBuilder, resultBuilder, subtraces, traceAddress, type);
    }

    public Result.Builder getResultBuilder() {
      return resultBuilder;
    }

    public Action.Builder getActionBuilder() {
      return actionBuilder;
    }
  }
}
