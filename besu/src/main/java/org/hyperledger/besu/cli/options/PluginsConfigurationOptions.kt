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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.converter.PluginInfoConverter
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo
import picocli.CommandLine

/** The Plugins options.  */
class PluginsConfigurationOptions
/** Default Constructor.  */
    : CLIOptions<PluginConfiguration?> {
    @CommandLine.Option(
        names = [DefaultCommandValues.DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME],
        description = ["Enables external plugins (default: \${DEFAULT-VALUE})"],
        hidden = true,
        defaultValue = "true",
        arity = "1"
    )
    private var externalPluginsEnabled = true

    @CommandLine.Option(
        names = [DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME],
        description = ["Comma-separated list of plugin names to load"],
        split = ",",
        converter = [PluginInfoConverter::class],
        arity = "1"
    )
    private var plugins: List<PluginInfo>? = null

    @CommandLine.Option(
        names = [DefaultCommandValues.DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME],
        description = ["Allow Besu startup even if any plugins fail to initialize correctly (default: \${DEFAULT-VALUE})"],
        defaultValue = "false",
        arity = "1"
    )
    private var continueOnPluginError = false

    override fun toDomainObject(): PluginConfiguration {
        return PluginConfiguration.Builder()
            .externalPluginsEnabled(externalPluginsEnabled)
            .requestedPlugins(plugins)
            .continueOnPluginError(continueOnPluginError)
            .build()
    }

    /**
     * Validate that there are no inconsistencies in the specified options.
     *
     * @param commandLine the full commandLine to check all the options specified by the user
     */
    fun validate(commandLine: CommandLine?) {
        val errorMessage = String.format(
            "%s and %s option can only be used when %s is true",
            DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME,
            DefaultCommandValues.DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME,
            DefaultCommandValues.DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME
        )
        CommandLineUtils.failIfOptionDoesntMeetRequirement(
            commandLine,
            errorMessage,
            externalPluginsEnabled,
            java.util.List.of(
                DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME,
                DefaultCommandValues.DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME
            )
        )
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, PluginsConfigurationOptions())
    }

    companion object {
        /**
         * Constructs a [PluginConfiguration] instance based on the command line options.
         *
         * @param commandLine The command line instance containing parsed options.
         * @return A new [PluginConfiguration] instance.
         */
        @JvmStatic
        fun fromCommandLine(commandLine: CommandLine): PluginConfiguration {
            val plugins =
                CommandLineUtils.getOptionValueOrDefault(
                    commandLine, DefaultCommandValues.DEFAULT_PLUGINS_OPTION_NAME, PluginInfoConverter()
                )

            val externalPluginsEnabled =
                CommandLineUtils.getOptionValueOrDefault(
                    commandLine, DefaultCommandValues.DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME
                ) { s: String -> s.toBoolean() }

            val continueOnPluginError =
                CommandLineUtils.getOptionValueOrDefault(
                    commandLine, DefaultCommandValues.DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME
                ) { s: String -> s.toBoolean() }

            return PluginConfiguration.Builder()
                .requestedPlugins(plugins)
                .externalPluginsEnabled(externalPluginsEnabled)
                .continueOnPluginError(continueOnPluginError)
                .build()
        }
    }
}
