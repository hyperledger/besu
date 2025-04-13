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
import org.hyperledger.besu.cli.BesuCommand
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.options.NodePrivateKeyFileOption
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand
import org.hyperledger.besu.cli.subcommands.PublicKeySubCommand.AddressSubCommand
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.crypto.KeyPair
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory
import org.hyperledger.besu.crypto.SignatureAlgorithmType
import org.hyperledger.besu.ethereum.core.Util
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.Function

/** Node's public key related sub-command  */
@CommandLine.Command(
    name = PublicKeySubCommand.COMMAND_NAME,
    description = ["This command provides node public key related actions."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [PublicKeySubCommand.ExportSubCommand::class, AddressSubCommand::class]
)
class PublicKeySubCommand
/**
 * Instantiates a new Public key sub command.
 *
 * @param out the out
 */(private val out: PrintWriter) : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val parentCommand: BesuCommand? = null // Picocli injects reference to parent command

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null // Picocli injects reference to command spec

    override fun run() {
        spec!!.commandLine().usage(out)
    }

    /**
     * Public key export sub-command
     *
     *
     * Export of the public key is writing the key to the standard output by default. An option
     * enables to write it in a file. Indeed, a direct output of the value to standard out is not
     * always recommended as reading can be made difficult as the value can be mixed with other
     * information like logs that are in KeyPairUtil that is inevitable.
     */
    @CommandLine.Command(
        name = "export",
        description = ["This command outputs the node public key. Default output is standard output."],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class ExportSubCommand : KeyPairSubcommand(), Runnable {
        @CommandLine.Option(
            names = ["--to"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File to write public key to instead of standard output"],
            arity = "1..1"
        )
        private val publicKeyExportFile: File? = null

        override fun run() {
            configureEcCurve(ecCurve, parentCommand!!.spec!!.commandLine())
            run(
                publicKeyExportFile,
                Function { keyPair: KeyPair? -> keyPair?.publicKey.toString() })
        }
    }

    /**
     * Account address export sub-command
     *
     *
     * Export of the account address is writing the address to the standard output by default. An
     * option enables to write it in a file. Indeed, a direct output of the value to standard out is
     * not always recommended as reading can be made difficult as the value can be mixed with other
     * information like logs that are in KeyPairUtil that is inevitable.
     */
    @CommandLine.Command(
        name = "export-address",
        description = [("This command outputs the node's account address. "
                + "Default output is standard output.")],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class AddressSubCommand : KeyPairSubcommand(), Runnable {
        @CommandLine.Option(
            names = ["--to"],
            paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
            description = ["File to write address to instead of standard output"],
            arity = "1..1"
        )
        private val addressExportFile: File? = null

        override fun run() {
            configureEcCurve(ecCurve, parentCommand!!.spec!!.commandLine())
            run(
                addressExportFile,
                Function { keyPair: KeyPair? -> Util.publicKeyToAddress(keyPair!!.publicKey).toString() })
        }
    }

    open class KeyPairSubcommand {
        /** The Parent command.  */
        @Suppress("unused")
        @CommandLine.ParentCommand
        protected var parentCommand: PublicKeySubCommand? = null // Picocli injects reference to parent command

        @CommandLine.Mixin
        private val nodePrivateKeyFileOption: NodePrivateKeyFileOption? = null

        /** The Ec curve.  */
        @CommandLine.Option(
            names = ["--ec-curve"],
            paramLabel = "<NAME>",
            description = [("Elliptic curve to use when creating a new key (default: "
                    + SignatureAlgorithmType.DEFAULT_EC_CURVE_NAME
                    + ")")],
            arity = "0..1"
        )
        protected var ecCurve: String? = null

        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        /**
         * Run.
         *
         * @param exportFile the export file
         * @param outputFunction the output function
         */
        protected fun run(
            exportFile: File?, outputFunction: Function<KeyPair?, String?>
        ) {
            Preconditions.checkNotNull(parentCommand)
            val besuCommand = parentCommand!!.parentCommand
            Preconditions.checkNotNull(besuCommand)

            val nodePrivateKeyFile = nodePrivateKeyFileOption!!.nodePrivateKeyFile
            if (nodePrivateKeyFile != null && !nodePrivateKeyFile.exists()) {
                throw CommandLine.ParameterException(
                    spec!!.commandLine(), "Private key file doesn't exist"
                )
            }

            val keyPair: KeyPair
            try {
                keyPair = besuCommand!!.loadKeyPair(nodePrivateKeyFileOption.nodePrivateKeyFile)
            } catch (e: IllegalArgumentException) {
                throw CommandLine.ParameterException(
                    spec!!.commandLine(), "Private key cannot be loaded from file", e
                )
            }
            val output = outputFunction.apply(keyPair)
            if (exportFile != null) {
                val path = exportFile.toPath()

                try {
                    Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { fileWriter ->
                        fileWriter.write(output)
                    }
                } catch (e: IOException) {
                    LOG.error("An error occurred while trying to write to output file", e)
                }
            } else {
                parentCommand!!.out.println(output)
            }
        }

        companion object {
            /**
             * Configure ec curve.
             *
             * @param ecCurve the ec curve
             * @param commandLine the command line
             */
            @JvmStatic
            protected fun configureEcCurve(ecCurve: String?, commandLine: CommandLine) {
                if (ecCurve != null) {
                    try {
                        SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create(ecCurve))
                    } catch (e: IllegalArgumentException) {
                        throw CommandLine.ParameterException(commandLine, e.message, e)
                    }
                }
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(PublicKeySubCommand::class.java)

        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "public-key"
    }
}
