/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.tracing;

import java.util.Objects;

/** Configuration for the default struct/opcode tracer. */
public final class OpCodeTracerConfigBuilder {
  private boolean traceStorage;
  private boolean traceMemory;
  private boolean traceStack;
  private boolean traceReturnData;
  private boolean eip3155Strict;

  public static OpCodeTracerConfigBuilder createFrom(final OpCodeTracerConfig opCodeTracerConfig) {
    return new OpCodeTracerConfigBuilder(opCodeTracerConfig);
  }

  public static OpCodeTracerConfigBuilder create() {
    return new OpCodeTracerConfigBuilder();
  }

  private OpCodeTracerConfigBuilder() {}

  private OpCodeTracerConfigBuilder(final OpCodeTracerConfig opCodeTracerConfig) {
    Objects.requireNonNull(opCodeTracerConfig, "opCodeTracerConfig should not be null");
    this.traceStorage = opCodeTracerConfig.traceStorage();
    this.traceMemory = opCodeTracerConfig.traceMemory();
    this.traceStack = opCodeTracerConfig.traceStack();
    this.traceReturnData = opCodeTracerConfig.traceReturnData();
    this.eip3155Strict = opCodeTracerConfig.eip3155Strict();
  }

  public OpCodeTracerConfigBuilder traceStorage(final boolean enable) {
    traceStorage = enable;
    return this;
  }

  public OpCodeTracerConfigBuilder traceMemory(final boolean enable) {
    traceMemory = enable;
    return this;
  }

  public OpCodeTracerConfigBuilder traceStack(final boolean enable) {
    traceStack = enable;
    return this;
  }

  public OpCodeTracerConfigBuilder traceReturnData(final boolean enable) {
    traceReturnData = enable;
    return this;
  }

  public OpCodeTracerConfigBuilder eip3155Strict(final boolean enable) {
    eip3155Strict = enable;
    return this;
  }

  public OpCodeTracerConfig build() {
    return new Config(traceStorage, traceMemory, traceStack, traceReturnData, eip3155Strict);
  }

  public sealed interface OpCodeTracerConfig permits Config {
    OpCodeTracerConfig DEFAULT = new Config(true, false, true, false, false);

    boolean traceStorage();

    boolean traceMemory();

    boolean traceStack();

    boolean traceReturnData();

    boolean eip3155Strict();
  }

  private record Config(
      boolean traceStorage,
      boolean traceMemory,
      boolean traceStack,
      boolean traceReturnData,
      boolean eip3155Strict)
      implements OpCodeTracerConfig {}
}
