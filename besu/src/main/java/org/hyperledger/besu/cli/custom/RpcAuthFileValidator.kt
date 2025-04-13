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
package org.hyperledger.besu.cli.custom

import com.google.common.base.Strings
import org.apache.tuweni.toml.TomlParseResult
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.TomlAuth
import org.hyperledger.besu.ethereum.permissioning.TomlConfigFileParser
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.util.stream.Collectors

/** The Rpc authentication file validator.  */
object RpcAuthFileValidator {
    /**
     * Validate auth file.
     *
     * @param commandLine the command line to use for parameter exceptions
     * @param filename the auth file
     * @param type the RPC type
     * @return the auth filename
     */
    @JvmStatic
    fun validate(
        commandLine: CommandLine, filename: String, type: String
    ): String {
        val authfile = File(filename)
        if (!authfile.exists()) {
            throw CommandLine.ParameterException(
                commandLine,
                ("The specified RPC "
                        + type
                        + " authentication credential file '"
                        + filename
                        + "' does not exist")
            )
        }

        val tomlParseResult: TomlParseResult
        try {
            tomlParseResult = TomlConfigFileParser.loadConfigurationFromFile(filename)
        } catch (e: IOException) {
            throw CommandLine.ParameterException(
                commandLine,
                ("An error occurred while opening the specified RPC "
                        + type
                        + " authentication configuration file.")
            )
        } catch (e: Exception) {
            throw CommandLine.ParameterException(
                commandLine,
                "Invalid RPC " + type + " authentication credentials file: " + e.message
            )
        }

        if (tomlParseResult.hasErrors()) {
            throw CommandLine.ParameterException(
                commandLine,
                "An error occurred while parsing the specified RPC authentication configuration file."
            )
        }

        if (!verifyAllUsersHavePassword(tomlParseResult)) {
            throw CommandLine.ParameterException(commandLine, "RPC user specified without password.")
        }

        if (!verifyAllEntriesHaveValues(tomlParseResult)) {
            throw CommandLine.ParameterException(
                commandLine, "RPC authentication configuration file contains invalid values."
            )
        }

        return filename
    }

    private fun verifyAllUsersHavePassword(tomlParseResult: TomlParseResult): Boolean {
        val configuredUsers = tomlParseResult.getTable("Users")!!.keySet().size

        val usersWithPasswords =
            tomlParseResult.keyPathSet().parallelStream()
                .filter { keySet: List<String?> ->
                    keySet.contains("Users")
                            && keySet.contains("password")
                            && !Strings.isNullOrEmpty(tomlParseResult.getString(keySet))
                }
                .collect(Collectors.toList())
                .size

        return configuredUsers == usersWithPasswords
    }

    private fun verifyAllEntriesHaveValues(tomlParseResult: TomlParseResult): Boolean {
        return tomlParseResult.dottedKeySet().parallelStream()
            .filter { keySet: String -> !keySet.contains("password") }
            .allMatch { dottedKey: String -> verifyEntry(dottedKey, tomlParseResult) }
    }

    private fun verifyEntry(key: String, tomlParseResult: TomlParseResult): Boolean {
        return if (key.endsWith(TomlAuth.PRIVACY_PUBLIC_KEY)) {
            verifyString(key, tomlParseResult)
        } else {
            verifyArray(key, tomlParseResult)
        }
    }

    private fun verifyString(key: String, tomlParseResult: TomlParseResult): Boolean {
        return tomlParseResult.isString(key) && !tomlParseResult.getString(key) { "" }.isEmpty()
    }

    private fun verifyArray(key: String, tomlParseResult: TomlParseResult): Boolean {
        return tomlParseResult.isArray(key) && !tomlParseResult.getArrayOrEmpty(key).isEmpty
    }
}
