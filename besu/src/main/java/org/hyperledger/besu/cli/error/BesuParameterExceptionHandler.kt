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
package org.hyperledger.besu.cli.error

import picocli.CommandLine
import java.util.function.Supplier

/** The custom parameter exception handler for Besu PicoCLI.  */
class BesuParameterExceptionHandler
/**
 * Instantiates a new Besu parameter exception handler.
 *
 * @param levelSupplier the logging level supplier
 */(private val levelSupplier: Supplier<String>) : CommandLine.IParameterExceptionHandler {
    override fun handleParseException(ex: CommandLine.ParameterException, args: Array<String>): Int {
        val cmd = ex.commandLine
        val err = cmd.err
        val logLevel = levelSupplier.get()
        if (logLevel != null
            && (logLevel == "DEBUG" || logLevel == "TRACE" || logLevel == "ALL")
        ) {
            ex.printStackTrace(err)
        } else {
            err.println(ex.message)
        }

        CommandLine.UnmatchedArgumentException.printSuggestions(ex, err)

        // don't print full help, just the instructions required to get it
        err.println()
        err.println("To display full help:")
        err.println("besu [COMMAND] --help")

        val spec = cmd.commandSpec

        return if (cmd.exitCodeExceptionMapper != null)
            cmd.exitCodeExceptionMapper.getExitCode(ex)
        else
            spec.exitCodeOnInvalidInput()
    }
}
