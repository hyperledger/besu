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
package org.hyperledger.besu.cli.subcommands.operator

import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand
import org.hyperledger.besu.cli.util.VersionProvider
import picocli.CommandLine
import java.io.PrintWriter

/** Operator related sub-command  */
@CommandLine.Command(
    name = OperatorSubCommand.COMMAND_NAME,
    description = ["Operator related actions such as generating configuration and caches."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [GenerateBlockchainConfig::class, GenerateLogBloomCache::class, BackupState::class, RestoreState::class
    ]
)
class OperatorSubCommand
/**
 * Instantiates a new Operator sub command.
 *
 * @param out the out
 */(private val out: PrintWriter) : Runnable {
    /** The Parent command.  */
    @JvmField
    @Suppress("unused")
    @CommandLine.ParentCommand
    var parentCommand: BesuCommand? = null // Picocli injects reference to parent command

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

    override fun run() {
        spec!!.commandLine().usage(out)
    }

    companion object {
        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "operator"

        /** The constant GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME.  */
        const val GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME: String = "generate-blockchain-config"
    }
}
