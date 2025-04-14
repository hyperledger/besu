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

import picocli.CommandLine
import java.io.InputStream
import java.util.*

/**
 * Abstract class for finding configuration resources. This class provides a common structure for
 * classes that need to find configuration resources based on command line options and environment
 * variables.
 *
 * @param <T> the type of configuration resource this finder will return
</T> */
abstract class AbstractConfigurationFinder<T>
/** Default Constructor.  */
{
    /**
     * Returns the name of the configuration option.
     *
     * @return the name of the configuration option
     */
    protected abstract val configOptionName: String?

    /**
     * Returns the name of the environment variable for the configuration.
     *
     * @return the name of the environment variable for the configuration
     */
    protected abstract val configEnvName: String?

    /**
     * Finds the configuration resource based on command line options and environment variables.
     *
     * @param environment the environment variables
     * @param parseResult the command line parse result
     * @return an Optional containing the configuration resource, or an empty Optional if no
     * configuration resource was found
     */
    fun findConfiguration(
        environment: Map<String, String>, parseResult: CommandLine.ParseResult
    ): Optional<InputStream> {
        val commandLine = parseResult.commandSpec().commandLine()
        if (isConfigSpecifiedInBothSources(environment, parseResult)) {
            throwExceptionForBothSourcesSpecified(environment, parseResult, commandLine)
        }
        if (parseResult.hasMatchedOption(configOptionName)) {
            return getFromOption(parseResult, commandLine)
        }
        if (environment.containsKey(configEnvName)) {
            return getFromEnvironment(environment, commandLine)
        }
        return Optional.empty()
    }

    /**
     * Gets the configuration resource from the command line option.
     *
     * @param parseResult the command line parse result
     * @param commandLine the command line
     * @return an Optional containing the configuration resource, or an empty Optional if the
     * configuration resource was not specified in the command line option
     */
    protected abstract fun getFromOption(
        parseResult: CommandLine.ParseResult?, commandLine: CommandLine?
    ): Optional<InputStream>

    /**
     * Gets the configuration resource from the environment variable.
     *
     * @param environment the environment variables
     * @param commandLine the command line
     * @return an Optional containing the configuration resource, or an empty Optional if the
     * configuration resource was not specified in the environment variable
     */
    protected abstract fun getFromEnvironment(
        environment: Map<String, String>, commandLine: CommandLine?
    ): Optional<InputStream>

    /**
     * Checks if the configuration resource is specified in both command line options and environment
     * variables.
     *
     * @param environment the environment variables
     * @param parseResult the command line parse result
     * @return true if the configuration resource is specified in both places, false otherwise
     */
    fun isConfigSpecifiedInBothSources(
        environment: Map<String, String>, parseResult: CommandLine.ParseResult
    ): Boolean {
        return parseResult.hasMatchedOption(configOptionName)
                && environment.containsKey(configEnvName)
    }

    /**
     * Throws an exception if the configuration resource is specified in both command line options and
     * environment variables.
     *
     * @param environment the environment variables
     * @param parseResult the command line parse result
     * @param commandLine the command line
     */
    fun throwExceptionForBothSourcesSpecified(
        environment: Map<String, String>,
        parseResult: CommandLine.ParseResult,
        commandLine: CommandLine
    ) {
        throw CommandLine.ParameterException(
            commandLine,
            String.format(
                "Both %s=%s and %s %s specified. Please specify only one.",
                configEnvName,
                configOptionName,
                environment[configEnvName],
                parseResult.matchedOption(configOptionName).stringValues()
            )
        )
    }
}
