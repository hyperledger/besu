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
package org.hyperledger.besu.cli

import org.hyperledger.besu.cli.UnstableOptionsSubCommand
import picocli.CommandLine
import java.io.PrintStream
import com.google.common.collect.ImmutableMap

/**
 * This class provides a CLI command to enumerate the unstable options available in Besu.
 *
 *
 * Unstable options are distinguished by
 *
 *
 *  * Not being configured in the main BesuCommand
 *  * Being marked as 'hidden'
 *  * Having their first option name start with `--X`
 *
 *
 * There is no stability or compatibility guarantee for options marked as unstable between releases.
 * They can be added and removed without announcement and their meaning and values can similarly
 * change without announcement or warning.
 */
@CommandLine.Command(
    name = UnstableOptionsSubCommand.COMMAND_NAME,
    aliases = ["-X", "--Xhelp"],
    description = ["This command provides help text for all unstable options."],
    hidden = true,
    helpCommand = true
)
class UnstableOptionsSubCommand private constructor(private val commandLine: CommandLine) : Runnable,
    CommandLine.IHelpCommandInitializable {
    private var ansi: CommandLine.Help.Ansi? = null
    private var out: PrintStream? = null

    override fun run() {
        commandLine.commandSpec.mixins().forEach { (mixinName: String, commandSpec: CommandLine.Model.CommandSpec) ->
            this.printUnstableOptions(
                mixinName,
                commandSpec
            )
        }
    }

    override fun init(
        helpCommandLine: CommandLine,
        ansi: CommandLine.Help.Ansi,
        out: PrintStream,
        err: PrintStream
    ) {
        this.ansi = ansi
        this.out = out
    }

    private fun printUnstableOptions(
        mixinName: String, commandSpec: CommandLine.Model.CommandSpec
    ) {
        // Recreate the options but flip hidden to false.
        val cs = CommandLine.Model.CommandSpec.create()
        commandSpec.options().stream()
            .filter { option: CommandLine.Model.OptionSpec -> option.hidden() && option.names()[0].startsWith("--X") }
            .forEach { option: CommandLine.Model.OptionSpec -> cs.addOption(option.toBuilder().hidden(false).build()) }

        if (cs.options().size > 0) {
            // Print out the help text.
            out!!.printf("Unstable options for %s%n", mixinName)
            out!!.println(CommandLine.Help(cs, ansi).optionList())
        }
    }

    companion object {
        const val COMMAND_NAME: String = "Xhelp"

//        fun createUnstableOptions(
//            commandLine: CommandLine, mixins: Map<String, Any>
//        ) {
//            val listOptionsCommand = UnstableOptionsSubCommand(commandLine)
//            mixins.forEach { (name: String?, mixin: Any?) -> commandLine.addMixin(name, mixin) }
//            commandLine.addSubcommand(COMMAND_NAME, listOptionsCommand)
//        }

        @JvmStatic
        fun createUnstableOptions(commandLine: CommandLine, unstableOptions: ImmutableMap<String, Any>) {
            val listOptionsCommand = UnstableOptionsSubCommand(commandLine)
            unstableOptions.forEach { (name: String?, mixin: Any?) -> commandLine.addMixin(name, mixin) }
            commandLine.addSubcommand(COMMAND_NAME, listOptionsCommand)
        }
    }
}
