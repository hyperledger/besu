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

import java.util.Map;

/**
 * Configuration options for tracing.
 *
 * <p>Contains the tracer type and configuration options for the tracers. Default Tracer Config is
 * top-level configuration option and is typically used by default tracer. Non-default tracers
 * typically use tracer Config object.
 */
public record TraceOptions(
    String tracerType, DefaultTracerConfig defaultTracerConfig, Map<String, Object> tracerConfig) {
  private static final DefaultTracerConfig DEFAULT_TRACER_CONFIG =
      new DefaultTracerConfig(true, false, true);

  /**
   * Default tracer configuration. Used by trace_ and debug_ methods when no tracer type is
   * specified.
   */
  public static final TraceOptions DEFAULT = new TraceOptions("", DEFAULT_TRACER_CONFIG, Map.of());

  /**
   * Constructor for TraceOptions.
   *
   * @param tracerType the type of tracer to use
   * @param defaultTracerConfig the default tracer's configuration
   * @param tracerConfig the tracer configuration options for non-default tracers
   */
  public TraceOptions(
      final String tracerType,
      final DefaultTracerConfig defaultTracerConfig,
      final Map<String, Object> tracerConfig) {
    this.tracerType = tracerType == null ? "" : tracerType.trim();
    this.tracerConfig = tracerConfig == null ? Map.of() : tracerConfig;
    this.defaultTracerConfig =
        defaultTracerConfig == null ? DEFAULT_TRACER_CONFIG : defaultTracerConfig;
  }
}
