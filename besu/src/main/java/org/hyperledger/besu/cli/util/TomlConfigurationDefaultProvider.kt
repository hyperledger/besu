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
package org.hyperledger.besu.cli.util

import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlArray
import org.apache.tuweni.toml.TomlParseError
import org.apache.tuweni.toml.TomlParseResult
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.util.number.Fraction
import org.hyperledger.besu.util.number.Percentage
import org.hyperledger.besu.util.number.PositiveNumber
import picocli.CommandLine
import java.io.*
import java.math.BigInteger
import java.util.*
import java.util.stream.Collectors

/** The Toml config file default value provider used by PicoCli.  */
class TomlConfigurationDefaultProvider
/**
 * Instantiates a new Toml config file default value provider.
 *
 * @param commandLine the command line
 * @param configurationInputStream the input stream
 */ private constructor(private val commandLine: CommandLine, private val configurationInputStream: InputStream) :
    CommandLine.IDefaultValueProvider {
    private var result: TomlParseResult? = null
    private var isUnknownOptionsChecked = false

    override fun defaultValue(argSpec: CommandLine.Model.ArgSpec): String? {
        loadConfigurationIfNotLoaded()

        // only options can be used in config because a name is needed for the key
        // so we skip default for positional params
        return if (argSpec.isOption) getConfigurationValue((argSpec as CommandLine.Model.OptionSpec)) else null
    }

    private fun getConfigurationValue(optionSpec: CommandLine.Model.OptionSpec): String? {
        // NOTE: This temporary fix is necessary to make certain options be treated as a multi-value.
        // This can be done automatically by picocli if the object implements Collection.
        val isArray = getKeyName(optionSpec).map { dottedKey: String? ->
            result!!.isArray(
                dottedKey
            )
        }.orElse(false)

        return if (optionSpec.type() == Boolean::class.java || optionSpec.type() == Boolean::class.javaPrimitiveType) {
            getBooleanEntryAsString(optionSpec)
        } else if (optionSpec.isMultiValue || isArray) {
            getListEntryAsString(optionSpec)
        } else if (isNumericType(optionSpec.type())) {
            getNumericEntryAsString(optionSpec)
        } else { // else will be treated as String
            getEntryAsString(optionSpec)
        }
    }

    private fun isNumericType(type: Class<*>): Boolean {
        return type == Byte::class.java
                || type == Byte::class.javaPrimitiveType
                || type == Short::class.java
                || type == Short::class.javaPrimitiveType
                || type == Int::class.java
                || type == Int::class.javaPrimitiveType
                || type == Long::class.java
                || type == Long::class.javaPrimitiveType
                || type == Wei::class.java
                || type == BigInteger::class.java
                || type == Double::class.java
                || type == Double::class.javaPrimitiveType
                || type == Float::class.java
                || type == Float::class.javaPrimitiveType
                || type == Percentage::class.java
                || type == Fraction::class.java
                || type == PositiveNumber::class.java
    }

    private fun getEntryAsString(spec: CommandLine.Model.OptionSpec): String? {
        // returns the string value of the config line corresponding to the option in toml file
        // or null if not present in the config
        return getKeyName(spec).map { dottedKey: String? -> result!!.getString(dottedKey) }.orElse(null)
    }

    private fun getKeyName(spec: CommandLine.Model.OptionSpec): Optional<String> {
        // If any of the names of the option are used as key in the toml results
        // then returns the value of first one.
        var keyName =
            Arrays.stream(spec.names()) // remove leading dashes on option name as we can have "--" or "-" options
                .map { name: String -> name.replaceFirst("^-+".toRegex(), "") }
                .filter { dottedKey: String? -> result!!.contains(dottedKey) }
                .findFirst()

        if (keyName.isEmpty) {
            // If the base key name doesn't exist in the file it may be under a TOML table heading
            // e.g. TxPool.tx-pool-max-size
            keyName = getDottedKeyName(spec)
        }

        return keyName
    }

    /*
   For all spec names, look to see if any of the TOML keyPathSet entries contain
   the name. A key path set might look like ["TxPool", "tx-max-pool-size"] where
   "TxPool" is the TOML table heading (which we ignore) and "tx-max-pool-size" is
   the name of the option being requested. For a request for "tx-max-pool-size" this
   function will return "TxPool.tx-max-pool-size" which can then be used directly
   as a query on the TOML result structure.
  */
    private fun getDottedKeyName(spec: CommandLine.Model.OptionSpec): Optional<String> {
        val foundNames: MutableList<String> = ArrayList()

        Arrays.stream(spec.names())
            .forEach { nextSpecName: String ->
                val specName =
                    result!!.keyPathSet().stream()
                        .filter { option: List<String?> ->
                            option.contains(
                                nextSpecName.replaceFirst(
                                    "^-+".toRegex(),
                                    ""
                                )
                            )
                        }
                        .findFirst()
                        .orElse(ArrayList())
                        .stream()
                        .collect(Collectors.joining("."))
                if (specName.length > 0) {
                    foundNames.add(specName)
                }
            }

        return foundNames.stream().findFirst()
    }

    private fun getListEntryAsString(spec: CommandLine.Model.OptionSpec): String? {
        // returns the string representation of the array value of the config line in CLI format
        // corresponding to the option in toml file
        // or null if not present in the config
        return decodeTomlArray(
            getKeyName(spec).map { dottedKey: String? -> result!!.getArray(dottedKey) }
                .map { tomlArray: TomlArray? -> tomlArray!!.toList() }
                .orElse(null))
    }

    private fun decodeTomlArray(tomlArrayElements: List<Any>?): String? {
        if (tomlArrayElements == null) return null
        return tomlArrayElements.stream()
            .map { tomlObject: Any ->
                if (tomlObject is TomlArray) {
                    return@map "[" + decodeTomlArray(tomlObject.toList()) + "]"
                } else {
                    return@map tomlObject.toString()
                }
            }
            .collect(Collectors.joining(","))
    }

    private fun getBooleanEntryAsString(spec: CommandLine.Model.OptionSpec): String? {
        // return the string representation of the boolean value corresponding to the option in toml
        // file
        // or null if not present in the config
        return getKeyName(spec).map { dottedKey: String? -> result!!.getBoolean(dottedKey) }
            .map { obj: Boolean? -> obj.toString() }
            .orElse(null)
    }

    private fun getNumericEntryAsString(spec: CommandLine.Model.OptionSpec): String? {
        // return the string representation of the numeric value corresponding to the option in toml
        // file - this works for integer, double, and float
        // or null if not present in the config

        return getKeyName(spec).map { dottedKey: String? -> result!![dottedKey] }.map { obj: Any? -> obj.toString() }
            .orElse(null)
    }

    private fun checkConfigurationValidity() {
        if (result == null || result!!.isEmpty) {
            throw CommandLine.ParameterException(
                commandLine, "Unable to read from empty TOML configuration file."
            )
        }

        if (!isUnknownOptionsChecked && !commandLine.isUnmatchedArgumentsAllowed) {
            checkUnknownOptions(result!!)
            isUnknownOptionsChecked = true
        }
    }

    /** Load configuration from file.  */
    fun loadConfigurationIfNotLoaded() {
        if (result == null) {
            try {
                val result = Toml.parse(configurationInputStream)

                if (result.hasErrors()) {
                    val errors =
                        result.errors().stream()
                            .map { obj: TomlParseError -> obj.toString() }
                            .collect(Collectors.joining("%n"))

                    throw CommandLine.ParameterException(
                        commandLine, String.format("Invalid TOML configuration: %s", errors)
                    )
                }

                this.result = result
            } catch (e: IOException) {
                throw CommandLine.ParameterException(
                    commandLine, "Unable to read TOML configuration, file not found."
                )
            }
        }
        checkConfigurationValidity()
    }

    private fun checkUnknownOptions(result: TomlParseResult) {
        val commandSpec = commandLine.commandSpec

        // Besu ignores TOML table headings (e.g. [TxPool]) so we use keyPathSet() and take the
        // last element in each one. For a TOML parameter that's not defined inside a table, the lists
        // returned in keyPathSet() will contain a single entry - the config parameter itself. For a
        // TOML
        // entry that is in a table the list will contain N entries, the last one being the config
        // parameter itself.
        val optionsWithoutTables: MutableSet<String> = HashSet()
        result.keyPathSet().stream()
            .forEach { strings: List<String> ->
                optionsWithoutTables.add(strings[strings.size - 1])
            }

        // Once we've stripped TOML table headings from the lists, we can check that the remaining
        // options are valid
        val unknownOptionsList =
            optionsWithoutTables.stream()
                .filter { option: String -> !commandSpec.optionsMap().containsKey("--$option") }
                .collect(Collectors.toSet())

        if (!unknownOptionsList.isEmpty()) {
            val csvUnknownOptions = java.lang.String.join(", ", unknownOptionsList)
            throw CommandLine.ParameterException(
                commandLine,
                String.format(
                    "Unknown option%s in TOML configuration file: %s",
                    if (unknownOptionsList.size > 1) "s" else "", csvUnknownOptions
                )
            )
        }
    }

    companion object {
        /**
         * Creates a new TomlConfigurationDefaultProvider from a file.
         *
         * @param commandLine the command line
         * @param configFile the configuration file
         * @return a new TomlConfigurationDefaultProvider
         * @throws ParameterException if the configuration file is not found
         */
        @JvmStatic
        fun fromFile(
            commandLine: CommandLine, configFile: File
        ): TomlConfigurationDefaultProvider {
            try {
                return TomlConfigurationDefaultProvider(commandLine, FileInputStream(configFile))
            } catch (e: FileNotFoundException) {
                throw CommandLine.ParameterException(
                    commandLine, "Unable to read TOML configuration, file not found."
                )
            }
        }

        /**
         * Creates a new TomlConfigurationDefaultProvider from an input stream.
         *
         * @param commandLine the command line
         * @param inputStream the input stream
         * @return a new TomlConfigurationDefaultProvider
         */
        fun fromInputStream(
            commandLine: CommandLine, inputStream: InputStream
        ): TomlConfigurationDefaultProvider {
            return TomlConfigurationDefaultProvider(commandLine, inputStream)
        }
    }
}
