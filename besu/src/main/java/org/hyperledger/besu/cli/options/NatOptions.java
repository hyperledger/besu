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
package org.hyperledger.besu.cli.options;

import picocli.CommandLine;

/** The Nat Cli options. */
public class NatOptions {

  @CommandLine.Option(
      hidden = true,
      names = {"--Xnat-method-fallback-enabled"},
      description =
          "Enable fallback to NONE for the nat manager in case of failure. If False BESU will exit on failure. (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean natMethodFallbackEnabled = true;

  /** Default constructor. */
  NatOptions() {}

  /**
   * Create nat options.
   *
   * @return the nat options
   */
  public static NatOptions create() {
    return new NatOptions();
  }

  /**
   * Whether nat method fallback is enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public Boolean getNatMethodFallbackEnabled() {
    return natMethodFallbackEnabled;
  }
}
