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
package org.hyperledger.besu.cli.subcommands

import com.google.common.base.Preconditions
import jakarta.validation.constraints.NotEmpty
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand
import org.hyperledger.besu.cli.subcommands.PasswordSubCommand.HashSubCommand
import org.hyperledger.besu.cli.util.VersionProvider
import org.springframework.security.crypto.bcrypt.BCrypt
import picocli.CommandLine
import java.io.PrintWriter

/** The Password sub command.  */
@CommandLine.Command(
    name = PasswordSubCommand.COMMAND_NAME,
    description = ["This command provides password related actions."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [HashSubCommand::class]
)
class PasswordSubCommand
/**
 * Instantiates a new Password sub command.
 *
 * @param out The PrintWriter where the usage will be reported.
 */(private val out: PrintWriter) : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val parentCommand: BesuCommand? = null

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null

    override fun run() {
        spec!!.commandLine().usage(out)
    }

    /** The Hash sub command for password.  */
    @CommandLine.Command(
        name = "hash",
        description = ["This command generates the hash of a given password."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class HashSubCommand : Runnable {
        @CommandLine.Option(
            names = ["--password"],
            arity = "1..1",
            required = true,
            description = ["The password input"]
        )
        private val password:  // PicoCLI requires non-final Strings.
                @NotEmpty String? = null

        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: PasswordSubCommand? = null

        override fun run() {
            Preconditions.checkNotNull(parentCommand)

            parentCommand!!.out.println(BCrypt.hashpw(password, BCrypt.gensalt()))
        }
    }

    companion object {
        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "password"
    }
}
