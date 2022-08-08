/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.cli.options.unstable;

import picocli.CommandLine.Option;

public class RollupOptions {
  @Option(
      hidden = true,
      names = {"--Xengine-rollup-extension-enabled"},
      description = "Enables Rollup extension Engine APIs (default: ${DEFAULT-VALUE})")
  private final Boolean isEngineRollupExtensionEnabled = false;

  public static RollupOptions create() {
    return new RollupOptions();
  }

  public Boolean isEngineRollupExtensionEnabled() {
    return isEngineRollupExtensionEnabled;
  }
}
