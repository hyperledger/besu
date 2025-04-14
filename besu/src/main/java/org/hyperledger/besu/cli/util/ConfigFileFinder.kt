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
package org.hyperledger.besu.cli.util

import org.hyperledger.besu.cli.DefaultCommandValues
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

/**
 * Class for finding configuration files. This class extends the AbstractConfigurationFinder and
 * provides methods for finding configuration files based on command line options and environment
 * variables.
 */
class ConfigFileFinder
/** Default constructor.  */
    : AbstractConfigurationFinder<File?>() {
    override val configOptionName: String
        /**
         * Returns the name of the configuration option.
         *
         * @return the name of the configuration option
         */
        get() = DefaultCommandValues.CONFIG_FILE_OPTION_NAME

    override val configEnvName: String?
        get() = Companion.configEnvName

    /**
     * Gets the configuration file from the command line option.
     *
     * @param parseResult the command line parse result
     * @param commandLine the command line
     * @return an Optional containing the configuration file, or an empty Optional if the
     * configuration file was not specified in the command line option
     */
    override fun getFromOption(
        parseResult: CommandLine.ParseResult?, commandLine: CommandLine?
    ): Optional<InputStream> {
        val configFileOption =
            parseResult!!.matchedOption(DefaultCommandValues.CONFIG_FILE_OPTION_NAME)
        try {
            val file = configFileOption.getter().get<File>()
            if (!file.exists()) {
                throw CommandLine.ParameterException(
                    commandLine,
                    String.format("Unable to read TOML configuration, file not found: %s", file)
                )
            }
            return Optional.of(FileInputStream(file))
        } catch (e: Exception) {
            throw CommandLine.ParameterException(commandLine, e.message, e)
        }
    }

    /**
     * Gets the configuration file from the environment variable.
     *
     * @param environment the environment variables
     * @param commandLine the command line
     * @return an Optional containing the configuration file, or an empty Optional if the
     * configuration file was not specified in the environment variable
     */
    override fun getFromEnvironment(
        environment: Map<String?, String?>?, commandLine: CommandLine?
    ): Optional<InputStream> {
        val toml = File(environment!![Companion.configEnvName]!!)
        if (!toml.exists()) {
            throw CommandLine.ParameterException(
                commandLine,
                String.format(
                    "TOML file %s specified in environment variable %s not found",
                    Companion.configEnvName, environment[Companion.configEnvName]
                )
            )
        }
        return Optional.of(FileInputStream(toml))
    }

    companion object {
        val configEnvName: String = "BESU_CONFIG_FILE"
    }
}
