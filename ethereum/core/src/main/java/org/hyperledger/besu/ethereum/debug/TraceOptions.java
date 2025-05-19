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

public record TraceOptions<T extends TracerConfig>(TracerType tracerType, T config) {
  @SuppressWarnings("MethodInputParametersMustBeFinal")
  public TraceOptions {
    if (!tracerType.getConfigClass().isInstance(config)) {
      throw new IllegalArgumentException("Invalid config type for tracer type: " + tracerType);
    }
  }

  public static final TraceOptions<DefaultTracerConfig> DEFAULT =
      new TraceOptions<>(TracerType.DEFAULT_TRACER, new DefaultTracerConfig(true, false, true));
}
