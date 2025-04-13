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
package org.hyperledger.besu.cli.subcommands.rlp

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.google.common.base.Preconditions
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand.DecodeSubCommand
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand.EncodeSubCommand
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.consensus.common.bft.BftExtraData
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

/** The RLP sub command.  */
@CommandLine.Command(
    name = RLPSubCommand.COMMAND_NAME,
    description = ["This command provides RLP data related actions."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [EncodeSubCommand::class, DecodeSubCommand::class]
)
class RLPSubCommand
/**
 * Instantiates a new Rlp sub command.
 *
 * @param out the PrintWriter where the output of subcommand will be reported
 * @param in the InputStream which will be used to read the input for this subcommand.
 */(private val out: PrintWriter, private val `in`: InputStream) : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val parentCommand: BesuCommand? = null

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null

    override fun run() {
        spec!!.commandLine().usage(out)
    }

    /**
     * RLP encode sub-command
     *
     *
     * Encode a JSON data into an RLP hex string.
     */
    @CommandLine.Command(
        name = "encode",
        description = ["This command encodes a JSON typed data into an RLP hex string."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class EncodeSubCommand : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: RLPSubCommand? = null // Picocli injects reference to parent command

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        @CommandLine.Option(
            names = ["--type"],
            description = ["Type of the RLP data to encode, possible values are \${COMPLETION-CANDIDATES}. (default: \${DEFAULT-VALUE})"],
            arity = "1..1"
        )
        private val type = RLPType.IBFT_EXTRA_DATA

        @CommandLine.Option(
            names = ["--from"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File containing JSON object to encode"],
            arity = "1..1"
        )
        private val jsonSourceFile: File? = null

        @CommandLine.Option(
            names = ["--to"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File to write encoded RLP string to."],
            arity = "1..1"
        )
        private val rlpTargetFile: File? = null

        override fun run() {
            Preconditions.checkNotNull(parentCommand)
            readInput()
        }

        /**
         * Reads the stdin or from a file if one is specified by [.jsonSourceFile] then goes to
         * [.encode] this data
         */
        private fun readInput() {
            // if we have an output file defined, print to it
            // otherwise print to defined output, usually standard output.
            val jsonData = StringBuilder()

            if (jsonSourceFile != null) {
                try {
                    val reader = Files.newBufferedReader(jsonSourceFile.toPath(), StandardCharsets.UTF_8)

                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) jsonData.append(line)
                } catch (e: IOException) {
                    throw CommandLine.ExecutionException(spec!!.commandLine(), "Unable to read JSON file.")
                }
            } else {
                // get JSON data from standard input
                Scanner(parentCommand!!.`in`, StandardCharsets.UTF_8.name()).use { scanner ->
                    while (scanner.hasNextLine()) {
                        jsonData.append(
                            java.lang.String.join(
                                "",
                                *scanner.nextLine().split("\\s".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()))
                    }
                }
            }

            // next step is to encode the value
            encode(jsonData.toString())
        }

        /**
         * Encodes the JSON input into an RLP data based on the [.type] then goes to [ ][.writeOutput] this data to file or stdout
         *
         * @param jsonInput the JSON string data to encode
         */
        private fun encode(jsonInput: String) {
            if (jsonInput == null || jsonInput.isEmpty()) {
                throw CommandLine.ParameterException(
                    spec!!.commandLine(), "An error occurred while trying to read the JSON data."
                )
            } else {
                try {
                    // encode and write the value
                    writeOutput(type.adapter.encode(jsonInput)!!)
                } catch (e: MismatchedInputException) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        "Unable to map the JSON data with selected type. Please check JSON input format. "
                                + e
                    )
                } catch (e: IOException) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        "Unable to load the JSON data. Please check JSON input format. $e"
                    )
                }
            }
        }

        /**
         * write the encoded result to stdout or a file if the option is specified
         *
         * @param rlpEncodedOutput the RLP output to write to file or stdout
         */
        private fun writeOutput(rlpEncodedOutput: Bytes) {
            if (rlpTargetFile != null) {
                val targetPath = rlpTargetFile.toPath()

                try {
                    Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8).use { fileWriter ->
                        fileWriter.write(rlpEncodedOutput.toString())
                    }
                } catch (e: IOException) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        "An error occurred while trying to write the RLP string. " + e.message
                    )
                }
            } else {
                parentCommand!!.out.println(rlpEncodedOutput)
            }
        }
    }

    /**
     * RLP decode sub-command
     *
     *
     * Decode a RLP hex string into a validator list.
     */
    @CommandLine.Command(
        name = "decode",
        description = ["This command decodes a JSON typed RLP hex string into validator list."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class DecodeSubCommand : Runnable {
        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: RLPSubCommand? = null // Picocli injects reference to parent command

        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        @CommandLine.Option(
            names = ["--type"],
            description = ["Type of the RLP data to Decode, possible values are \${COMPLETION-CANDIDATES}. (default: \${DEFAULT-VALUE})"],
            arity = "1..1"
        )
        private val type = RLPType.IBFT_EXTRA_DATA

        @CommandLine.Option(
            names = ["--from"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File containing JSON object to decode"],
            arity = "1..1"
        )
        private val jsonSourceFile: File? = null

        @CommandLine.Option(
            names = ["--to"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File to write decoded RLP string to."],
            arity = "1..1"
        )
        private val rlpTargetFile: File? = null

        override fun run() {
            Preconditions.checkNotNull(parentCommand)
            readInput()
        }

        /**
         * Reads the stdin or from a file if one is specified by [.jsonSourceFile] then goes to
         * [.decode] this data
         */
        private fun readInput() {
            // if we have an output file defined, print to it
            // otherwise print to defined output, usually standard output.
            val inputData: String

            if (jsonSourceFile != null) {
                try {
                    val reader = Files.newBufferedReader(jsonSourceFile.toPath(), StandardCharsets.UTF_8)

                    // Read only the first line if there are many lines
                    inputData = reader.readLine()
                } catch (e: IOException) {
                    throw CommandLine.ExecutionException(spec!!.commandLine(), "Unable to read input file.")
                }
            } else {
                // get data from standard input
                try {
                    Scanner(parentCommand!!.`in`, StandardCharsets.UTF_8.name()).use { scanner ->
                        inputData = scanner.nextLine()
                    }
                } catch (e: NoSuchElementException) {
                    throw CommandLine.ParameterException(spec!!.commandLine(), "Unable to read input data.$e")
                }
            }

            decode(inputData)
        }

        /**
         * Decodes the string input into an validator data based on the [.type] then goes to
         * [.writeOutput] this data to file or stdout
         *
         * @param inputData the string data to decode
         */
        private fun decode(inputData: String) {
            if (inputData == null || inputData.isEmpty()) {
                throw CommandLine.ParameterException(
                    spec!!.commandLine(), "An error occurred while trying to read the input data."
                )
            } else {
                try {
                    // decode and write the value
                    writeOutput(type.adapter.decode(inputData)!!)
                } catch (e: MismatchedInputException) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        "Unable to map the input data with selected type. Please check input format. $e"
                    )
                } catch (e: IOException) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(), "Unable to load the input data. Please check input format. $e"
                    )
                }
            }
        }

        /**
         * write the decoded result to stdout or a file if the option is specified
         *
         * @param bftExtraDataOutput the BFT extra data output to write to file or stdout
         */
        private fun writeOutput(bftExtraDataOutput: BftExtraData) {
            if (rlpTargetFile != null) {
                val targetPath = rlpTargetFile.toPath()

                try {
                    Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8).use { fileWriter ->
                        fileWriter.write(bftExtraDataOutput.validators.toString())
                    }
                } catch (e: IOException) {
                    throw CommandLine.ParameterException(
                        spec!!.commandLine(),
                        "An error occurred while trying to write the validator list. " + e.message
                    )
                }
            } else {
                parentCommand!!.out.println(bftExtraDataOutput.validators.toString())
            }
        }
    }

    companion object {
        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "rlp"
    }
}
