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
package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME;

import org.hyperledger.besu.cli.converter.PluginInfoConverter;
import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.List;

import org.slf4j.Logger;
import picocli.CommandLine;

public class PluginsConfigurationOptions implements CLIOptions<PluginConfiguration> {
  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_OPTION_NAME},
      description = "Comma-separated list of plugin names",
      split = ",",
      hidden = true,
      converter = PluginInfoConverter.class,
      arity = "0..*")
  private List<PluginInfo> plugins;

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME},
      defaultValue = "false",
      hidden = true,
      description =
          "If true, only listed plugins are registered; otherwise, all discoverable plugins are.")
  private boolean strictRegistration;

  public void validate(final Logger logger, final CommandLine commandLine) {
    this.checkDependencies(logger, commandLine);
  }

  private void checkDependencies(final Logger logger, final CommandLine commandLine) {
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME,
        !strictRegistration,
        List.of("--plugins"));
  }

  @Override
  public PluginConfiguration toDomainObject() {
    return new PluginConfiguration(plugins, strictRegistration);
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new PluginsConfigurationOptions());
  }

  /**
   * Constructs a {@link PluginConfiguration} instance based on the command line options.
   *
   * @param commandLine The command line instance containing parsed options.
   * @return A new {@link PluginConfiguration} instance.
   */
  public static PluginConfiguration fromCommandLine(final CommandLine commandLine) {
    boolean strictRegistration =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_STRICT_REGISTRATION_OPTION_NAME, Boolean::valueOf);

    List<PluginInfo> plugins =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_OPTION_NAME, new PluginInfoConverter());

    return new PluginConfiguration(plugins, strictRegistration);
  }
}
