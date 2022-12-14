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
package org.hyperledger.besu.cli.options.unstable;

import net.consensys.quorum.mainnet.launcher.options.Options;
import picocli.CommandLine;

/** The Launcher CLI options. */
public class LauncherOptions implements Options {

  private static final String LAUNCHER_OPTION_NAME = "--Xlauncher";
  private static final String LAUNCHER_OPTION_NAME_FORCE = "--Xlauncher-force";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      hidden = true,
      names = {LAUNCHER_OPTION_NAME},
      description =
          "Activate the launcher if no configuration file is present.  (default: ${DEFAULT-VALUE})",
      arity = "0..1")
  private Boolean isLauncherMode = Boolean.FALSE;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      hidden = true,
      names = {LAUNCHER_OPTION_NAME_FORCE},
      description =
          "Force to activate the launcher even if a configuration file is present.  (default: ${DEFAULT-VALUE})",
      arity = "0..1")
  private Boolean isLauncherModeForced = Boolean.FALSE;

  /**
   * Create launcher options.
   *
   * @return the launcher options
   */
  public static LauncherOptions create() {
    return new LauncherOptions();
  }

  /**
   * Is launcher mode enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public boolean isLauncherMode() {
    return isLauncherMode;
  }

  /**
   * Is launcher mode forced enabled.
   *
   * @return true if enabled, false otherwise.
   */
  public boolean isLauncherModeForced() {
    return isLauncherModeForced;
  }
}
