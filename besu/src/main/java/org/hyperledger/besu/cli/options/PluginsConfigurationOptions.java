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

import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME;

import org.hyperledger.besu.cli.converter.PluginInfoConverter;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;

import java.util.List;

import picocli.CommandLine;

/** The Plugins options. */
public class PluginsConfigurationOptions implements CLIOptions<PluginConfiguration> {

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME},
      description = "Enables external plugins (default: ${DEFAULT-VALUE})",
      hidden = true,
      defaultValue = "true",
      arity = "1")
  private Boolean externalPluginsEnabled = true;

  @CommandLine.Option(
      names = {DEFAULT_PLUGINS_OPTION_NAME},
      description = "Comma-separated list of plugin names",
      split = ",",
      hidden = true,
      converter = PluginInfoConverter.class,
      arity = "1")
  private List<PluginInfo> plugins;

  @CommandLine.Option(
      names = {DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME},
      description =
          "Allow Besu startup even if any plugins fail to initialize correctly (default: ${DEFAULT-VALUE})",
      defaultValue = "false",
      arity = "1")
  private final Boolean continueOnPluginError = false;

  /** Default Constructor. */
  public PluginsConfigurationOptions() {}

  @Override
  public PluginConfiguration toDomainObject() {
    return new PluginConfiguration.Builder()
        .externalPluginsEnabled(externalPluginsEnabled)
        .requestedPlugins(plugins)
        .continueOnPluginError(continueOnPluginError)
        .build();
  }

  /**
   * Validate that there are no inconsistencies in the specified options.
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   */
  public void validate(final CommandLine commandLine) {
    String errorMessage =
        String.format(
            "%s and %s option can only be used when %s is true",
            DEFAULT_PLUGINS_OPTION_NAME,
            DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME,
            DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME);
    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        errorMessage,
        externalPluginsEnabled,
        List.of(DEFAULT_PLUGINS_OPTION_NAME, DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME));
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
    List<PluginInfo> plugins =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_OPTION_NAME, new PluginInfoConverter());

    boolean externalPluginsEnabled =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME, Boolean::parseBoolean);

    boolean continueOnPluginError =
        CommandLineUtils.getOptionValueOrDefault(
            commandLine, DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME, Boolean::parseBoolean);

    return new PluginConfiguration.Builder()
        .requestedPlugins(plugins)
        .externalPluginsEnabled(externalPluginsEnabled)
        .continueOnPluginError(continueOnPluginError)
        .build();
  }
}
