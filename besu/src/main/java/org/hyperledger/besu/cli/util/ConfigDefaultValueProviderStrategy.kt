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

import com.google.common.annotations.VisibleForTesting
import com.google.common.io.Files
import picocli.CommandLine
import java.io.File
import java.io.InputStream
import java.util.*


/** Custom Config option search and run handler.  */
class ConfigDefaultValueProviderStrategy
/**
 * Instantiates a new Config option search and run handler.
 *
 * @param resultHandler the result handler
 * @param environment the environment variables map
 */(private val resultHandler: CommandLine.IExecutionStrategy, private val environment: Map<String, String>) :
    CommandLine.IExecutionStrategy {
    @Throws(CommandLine.ExecutionException::class, CommandLine.ParameterException::class)
    override fun execute(parseResult: CommandLine.ParseResult): Int {
        val commandLine = parseResult.commandSpec().commandLine()
        commandLine.setDefaultValueProvider(
            createDefaultValueProvider(
                commandLine,
                ConfigFileFinder().findConfiguration(environment, parseResult),
                ProfileFinder().findConfiguration(environment, parseResult)
            )
        )
        commandLine.setExecutionStrategy(resultHandler)
        return commandLine.execute(*parseResult.originalArgs().toTypedArray<String>())
    }

    /**
     * Create default value provider default value provider.
     *
     * @param commandLine the command line
     * @param configFile the config file
     * @param profile the profile file
     * @return the default value provider
     */
    @VisibleForTesting
    fun createDefaultValueProvider(
        commandLine: CommandLine?,
        configFile: Optional<InputStream>,
        profile: Optional<out InputStream?>
    ): CommandLine.IDefaultValueProvider {
        val providers: MutableList<CommandLine.IDefaultValueProvider> = ArrayList()
        providers.add(EnvironmentVariableDefaultProvider(environment))

        configFile.ifPresent { config: InputStream? ->
            if (config != null) {
                val buffer = ByteArray(config.available())
                config.read(buffer)

                val targetFile = File("/tmp/targetFile.tmp")
                Files.write(buffer, targetFile)

//                providers.add(TomlConfigurationDefaultProvider.fromFile(commandLine!!, config))
                providers.add(TomlConfigurationDefaultProvider.fromFile(commandLine!!, targetFile))
            }
        }

        profile.ifPresent { p: InputStream? ->
            providers.add(
                TomlConfigurationDefaultProvider.fromInputStream(
                    commandLine!!,
                    p!!
                )
            )
        }
        return CascadingDefaultProvider(providers)
    }
}
