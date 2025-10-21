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

  /**
   * Create an OpcodeTracerConfig builder from a previous built config.
   *
   * @param opCodeTracerConfig config to copy the fields from
   * @return a new builder with an immutable configuration
   */
  public static OpCodeTracerConfigBuilder createFrom(final OpCodeTracerConfig opCodeTracerConfig) {
    return new OpCodeTracerConfigBuilder(opCodeTracerConfig);
  }

  /**
   * Create an OpcodeTracerConfig builder from scratch
   *
   * @return a new builder
   */
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

  /**
   * Set storage tracing flag.
   *
   * @param enable flag to enable tracing of storage
   * @return the current builder
   */
  public OpCodeTracerConfigBuilder traceStorage(final boolean enable) {
    traceStorage = enable;
    return this;
  }

  /**
   * Set memory tracing flag.
   *
   * @param enable flag to enable tracing of memory
   * @return the current builder
   */
  public OpCodeTracerConfigBuilder traceMemory(final boolean enable) {
    traceMemory = enable;
    return this;
  }

  /**
   * Set stack tracing flag.
   *
   * @param enable flag to enable tracing of stack
   * @return the current builder
   */
  public OpCodeTracerConfigBuilder traceStack(final boolean enable) {
    traceStack = enable;
    return this;
  }

  /**
   * Set returnData tracing flag.
   *
   * @param enable flag to enable tracing of returnData
   * @return the current builder
   */
  public OpCodeTracerConfigBuilder traceReturnData(final boolean enable) {
    traceReturnData = enable;
    return this;
  }

  /**
   * Set eip3155Strict flag.
   *
   * @param enable flag to enable eip3155 mode tracing
   * @return the current builder
   */
  public OpCodeTracerConfigBuilder eip3155Strict(final boolean enable) {
    eip3155Strict = enable;
    return this;
  }

  /**
   * Build OpCodeTracerConfig configuration.
   *
   * @return the config
   */
  public OpCodeTracerConfig build() {
    return new Config(traceStorage, traceMemory, traceStack, traceReturnData, eip3155Strict);
  }

  /**
   * Interface for the OpCodeTracerConfig. This interface is backed by a single record to avoid
   * rewriting all the boilerplate code for constructors, equals, hashcode, fields, etc... Also this
   * way no external entity can create an OpCodeTracerConfig than not through the builder provided
   * in this class.
   */
  public sealed interface OpCodeTracerConfig permits Config {
    /** static default OpcodeTracerConfig which can be accessed externally */
    OpCodeTracerConfig DEFAULT = new Config(true, false, true, false, false);

    /**
     * Check if tracing of storage is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean traceStorage();

    /**
     * Check if tracing of memory is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean traceMemory();

    /**
     * Check if tracing of stack is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean traceStack();

    /**
     * Check if tracing of returnData is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean traceReturnData();

    /**
     * Check if tracing in eip3155 mode is enabled.
     *
     * @return true if enabled, false otherwise
     */
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
