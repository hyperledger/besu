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
package org.hyperledger.besu.ethereum.debug;

import org.hyperledger.besu.evm.tracing.OpCodeTracerConfigBuilder.OpCodeTracerConfig;

import java.util.Map;

/**
 * Configuration options for tracing.
 *
 * <p>Contains the tracer type and configuration options for the tracers. Default Tracer Config is
 * top-level configuration option and is typically used by default tracer. Non-default tracers
 * typically use tracer Config object.
 */
public record TraceOptions(
    TracerType tracerType,
    OpCodeTracerConfig opCodeTracerConfig,
    Map<String, Object> tracerConfig) {
  /**
   * Default tracer configuration. Used by trace_ and debug_ methods when no tracer type is
   * specified.
   */
  public static final TraceOptions DEFAULT =
      new TraceOptions(TracerType.OPCODE_TRACER, OpCodeTracerConfig.DEFAULT, Map.of());

  /**
   * Constructor for TraceOptions. The default tracer (opcode) options are specified by
   * OpcodeTracerConfig. All other tracer type options are specified by tracerConfig.
   *
   * @param tracerType the type of tracer to use. Defaults to OPCODE_TRACER if null.
   * @param opCodeTracerConfig the default (opcode) tracer's configuration. Defaults to
   *     OPCODE_TRACER_CONFIG if null.
   * @param tracerConfig the tracer configuration options for non-default tracers. Empty map if
   *     null.
   */
  public TraceOptions(
      final TracerType tracerType,
      final OpCodeTracerConfig opCodeTracerConfig,
      final Map<String, Object> tracerConfig) {
    this.tracerType = tracerType == null ? TracerType.OPCODE_TRACER : tracerType;
    this.opCodeTracerConfig =
        opCodeTracerConfig == null ? OpCodeTracerConfig.DEFAULT : opCodeTracerConfig;
    this.tracerConfig = tracerConfig == null ? Map.of() : tracerConfig;
  }
}
