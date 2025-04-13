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
package org.hyperledger.besu.cli.error

import picocli.CommandLine

/** Custom Execution Exception Handler used by PicoCLI framework.  */
class BesuExecutionExceptionHandler
/** Default constructor.  */
    : CommandLine.IExecutionExceptionHandler {
    override fun handleExecutionException(
        ex: Exception,
        commandLine: CommandLine,
        parseResult: CommandLine.ParseResult
    ): Int {
        val spec = commandLine.commandSpec
        commandLine.err.println(ex)
        return if (commandLine.exitCodeExceptionMapper != null)
            commandLine.exitCodeExceptionMapper.getExitCode(ex)
        else
            spec.exitCodeOnExecutionException()
    }
}
