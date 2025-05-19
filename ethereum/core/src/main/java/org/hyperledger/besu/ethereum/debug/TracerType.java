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
package org.hyperledger.besu.ethereum.debug;

/**
 * Enum representing the type of tracer to be used.
 *
 * <p>The default tracer is used when no specific tracer is provided.
 */
public enum TracerType {
  DEFAULT_TRACER(null, DefaultTracerConfig.class),
  CALL_TRACER("callTracer", CallTracerConfig.class),
  FLAT_CALL_TRACER("flatCallTracer", CallTracerConfig.class);

  private final String tracerName;
  private final Class<? extends TracerConfig> configClass;

  TracerType(final String tracerName, final Class<? extends TracerConfig> configClass) {
    this.tracerName = tracerName;
    this.configClass = configClass;
  }

  public String getTracerName() {
    return tracerName;
  }

  public Class<? extends TracerConfig> getConfigClass() {
    return configClass;
  }

  public static TracerType fromString(final String tracerName) {
    if (tracerName == null) {
      return DEFAULT_TRACER;
    }

    for (TracerType tracerType : values()) {
      if (tracerName.equals(tracerType.getTracerName())) {
        return tracerType;
      }
    }
    throw new IllegalArgumentException("Unknown tracer type: " + tracerName);
  }
}
