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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;

import org.hyperledger.besu.config.experimental.PrivacyGenesisConfigFile;

import java.io.File;
import java.io.IOException;

import com.google.common.io.Resources;
import picocli.CommandLine;

public class PrivacyGenesisOptions {

  @CommandLine.Option(
      names = {"--Xprivacy-genesis-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      hidden = true,
      description = "Privacy Genesis file. Setting this option makes initialises private state.")
  private final File privacyGenesisFile = null;

  private final CommandLine commandLine;

  public PrivacyGenesisOptions(final CommandLine commandLine) {
    this.commandLine = commandLine;
  }

  public static PrivacyGenesisOptions create(final CommandLine commandLine) {
    return new PrivacyGenesisOptions(commandLine);
  }

  public boolean hasPrivacyGenesisFile() {
    return privacyGenesisFile != null;
  }

  public PrivacyGenesisConfigFile readPrivacyGenesisConfigOptions() {
    try {
      return PrivacyGenesisConfigFile.fromConfig(readPrivacyGenesisConfig());
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(
          this.commandLine, "Unable to load genesis file. " + e.getCause());
    }
  }

  private String readPrivacyGenesisConfig() {
    try {
      return Resources.toString(privacyGenesisFile.toURI().toURL(), UTF_8);
    } catch (final IOException e) {
      throw new CommandLine.ParameterException(
          this.commandLine,
          String.format("Unable to load genesis file %s.", privacyGenesisFile),
          e);
    }
  }
}
