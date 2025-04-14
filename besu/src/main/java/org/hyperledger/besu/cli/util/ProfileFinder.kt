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
import org.hyperledger.besu.cli.config.InternalProfileName
import org.hyperledger.besu.cli.config.InternalProfileName.Companion.valueOfIgnoreCase
import picocli.CommandLine
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors

/**
 * Class for finding profile configurations. This class extends the AbstractConfigurationFinder and
 * provides methods for finding profile configurations based on command line options and environment
 * variables. Each profile corresponds to a TOML configuration file that contains settings for
 * various options. The profile to use can be specified with the '--profile' command line option or
 * the 'BESU_PROFILE' environment variable.
 */
class ProfileFinder
/** Default Constructor.  */
    : AbstractConfigurationFinder<InputStream?>() {
    override val configOptionName: String
        get() = DefaultCommandValues.PROFILE_OPTION_NAME

    override val configEnvName: String?
        get() = Companion.configEnvName

    override fun getFromOption(
        parseResult: CommandLine.ParseResult?, commandLine: CommandLine?
    ): Optional<InputStream> {
        val profileName: String
        try {
            profileName = parseResult?.matchedOption(DefaultCommandValues.PROFILE_OPTION_NAME)?.getter()?.get()!!
        } catch (e: Exception) {
            throw CommandLine.ParameterException(
                commandLine, "Unexpected error in obtaining value of --profile", e
            )
        }
        return getProfile(profileName, commandLine!!)
    }

    override fun getFromEnvironment(
        environment: Map<String?, String?>?, commandLine: CommandLine?
    ): Optional<InputStream> {
        return getProfile(environment!![Companion.configEnvName], commandLine!!)
    }

    companion object {
        val configEnvName: String = "BESU_PROFILE"

        private fun getProfile(
            profileName: String?, commandLine: CommandLine
        ): Optional<InputStream> {
            val internalProfileConfigPath =
                valueOfIgnoreCase(profileName).map { obj: InternalProfileName -> obj.getConfigFile() }
            return if (internalProfileConfigPath.isPresent) {
                Optional.of(getTomlFileFromClasspath(internalProfileConfigPath.get()))
            } else {
                val externalProfileFile = defaultProfilesDir().resolve("$profileName.toml")
                if (Files.exists(externalProfileFile)) {
                    try {
                        Optional.of(Files.newInputStream(externalProfileFile))
                    } catch (e: IOException) {
                        throw CommandLine.ParameterException(
                            commandLine, "Error reading external profile: $profileName"
                        )
                    }
                } else {
                    throw CommandLine.ParameterException(
                        commandLine, "Unable to load external profile: $profileName"
                    )
                }
            }
        }

        private fun getTomlFileFromClasspath(profileConfigFile: String): InputStream {
            val resourceUrl =
                ProfileFinder::class.java.classLoader.getResourceAsStream(profileConfigFile)
            // this is not meant to happen, because for each InternalProfileName there is a corresponding
            // TOML file in resources
            checkNotNull(resourceUrl) { String.format("Internal Profile TOML %s not found", profileConfigFile) }
            return resourceUrl
        }

        val externalProfileNames: Set<String>
            /**
             * Returns the external profile names which are file names without extension in the default
             * profiles directory.
             *
             * @return Set of external profile names
             */
            get() {
                val profilesDir = defaultProfilesDir()
                if (!Files.exists(profilesDir)) {
                    return setOf()
                }

                try {
                    Files.list(profilesDir).use { pathStream ->
                        return pathStream
                            .filter { path: Path? -> Files.isRegularFile(path) }
                            .filter { path: Path -> path.toString().endsWith(".toml") }
                            .map { path: Path ->
                                path.fileName
                                    .toString()
                                    .substring(0, path.fileName.toString().length - 5)
                            }
                            .collect(Collectors.toSet())
                    }
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
            }

        /**
         * Return default profiles directory location
         *
         * @return Path to default profiles directory
         */
        private fun defaultProfilesDir(): Path {
            val profilesDir = System.getProperty("besu.profiles.dir")
            return if (profilesDir == null) {
                Paths.get(System.getProperty("besu.home", "."), "profiles")
            } else {
                Paths.get(profilesDir)
            }
        }
    }
}
