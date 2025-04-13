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
package org.hyperledger.besu.cli.subcommands

import com.google.common.base.Preconditions
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.subcommands.ValidateConfigSubCommand
import org.hyperledger.besu.cli.util.TomlConfigurationDefaultProvider
import org.hyperledger.besu.cli.util.VersionProvider
import picocli.CommandLine
import java.io.PrintWriter
import java.nio.file.Path

/** The Besu configuration Validate sub command.  */
@CommandLine.Command(
    name = ValidateConfigSubCommand.COMMAND_NAME,
    description = ["This command provides basic Besu config validation (syntax only)."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class ValidateConfigSubCommand
/**
 * Instantiates a new Validate config sub command.
 *
 * @param commandLine the command line
 * @param out the PrintWriter where validation results will be reported.
 */(private val commandLine: CommandLine, private val out: PrintWriter) : Runnable {
    @CommandLine.Option(
        names = ["--config-file"],
        paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
        description = ["Path to Besu config file"]
    )
    private val dataPath: Path = DefaultCommandValues.getDefaultBesuDataPath(this)

    @Suppress("unused")
    @CommandLine.ParentCommand
    private val parentCommand: BesuCommand? = null

    override fun run() {
        Preconditions.checkNotNull(parentCommand)
        try {
            TomlConfigurationDefaultProvider.fromFile(commandLine, dataPath.toFile())
                .loadConfigurationIfNotLoaded()
        } catch (e: Exception) {
            out.println(e)
            return
        }
        out.println(
            "TOML config file is valid on basic inspection. Further dependencies between related options are checked when Besu starts."
        )
    }

    companion object {
        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "validate-config"
    }
}
