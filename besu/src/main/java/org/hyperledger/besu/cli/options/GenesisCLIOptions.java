/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.config.GenesisCLIConfiguration;

import java.util.List;

import picocli.CommandLine;

/** Command line options for configuring the genesis file. */
public class GenesisCLIOptions implements CLIOptions<GenesisCLIConfiguration> {
  private static final String GETH_GENESIS_FILE_SUPPORT = "--Xgeth-genesis-file-support";

  private GenesisCLIOptions() {
    // empty constructor
  }

  @CommandLine.Option(
      hidden = true,
      names = {GETH_GENESIS_FILE_SUPPORT},
      description =
          "Enable the support of using Geth style genesis files (default: ${DEFAULT-VALUE})")
  private final Boolean gethGenesisFileSupport = Boolean.FALSE;

  /**
   * Create GenesisCLIOptions instance
   *
   * @return new GenesisCLIOptions object
   */
  public static GenesisCLIOptions create() {
    return new GenesisCLIOptions();
  }

  @Override
  public GenesisCLIConfiguration toDomainObject() {
    return new GenesisCLIConfiguration(gethGenesisFileSupport);
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new GenesisCLIOptions());
  }
}
