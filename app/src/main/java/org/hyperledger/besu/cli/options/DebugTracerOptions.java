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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceTransactionStepFactory;

import picocli.CommandLine;

/** Debug Tracer CLI Options */
public class DebugTracerOptions {
  /** Default Constructor */
  public DebugTracerOptions() {}

  /**
   * Create a new instance of DebugTracerOptions
   *
   * @return a new instance of DebugTracerOptions
   */
  public static DebugTracerOptions create() {
    return new DebugTracerOptions();
  }

  /**
   * Enables additional debug tracers such as callTracer and flatCallTracer.
   *
   * @param enableExtraDebugTracers When true, enables the extra debug tracers
   */
  @CommandLine.Option(
      names = {"--Xenable-extra-debug-tracers"},
      description =
          "Enable extra debug tracers such as callTracer, flatCallTracer (Experimental. Default: ${DEFAULT-VALUE})",
      defaultValue = "false",
      hidden = true)
  public void setEnableExtraDebugTracers(final boolean enableExtraDebugTracers) {
    DebugTraceTransactionStepFactory.enableExtraTracers = enableExtraDebugTracers;
  }
}
