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

import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.cli.subcommands.TxParseSubCommand
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder
import picocli.CommandLine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Stream

/**
 * txparse sub command implementing txparse spec from
 * https://github.com/holiman/txparse/tree/main/cmd/txparse
 */
@CommandLine.Command(
    name = TxParseSubCommand.COMMAND_NAME,
    description = ["Parse input transactions and return the sender, or an error."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class TxParseSubCommand
/**
 * Instantiates a new TxParse sub command.
 *
 * @param out the PrintWriter where validation results will be reported.
 */(private val out: PrintWriter) : Runnable {
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--corpus-file"],
        arity = "1..1",
        description = ["file to read transaction data lines from, otherwise defaults to stdin"]
    )
    private val corpusFile: String? = null

    fun fileStreamReader(filePath: String): Stream<String> {
        try {
            return Files.lines(Paths.get(filePath)) // skip comments
                .filter { line: String -> !line.startsWith("#") }  // strip out non-alphanumeric characters
                .map { line: String -> line.replace("[^a-zA-Z0-9]".toRegex(), "") }
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    override fun run() {
        val txStream = if (corpusFile != null) {
            fileStreamReader(corpusFile)
        } else {
            BufferedReader(
                InputStreamReader(
                    System.`in`,
                    StandardCharsets.UTF_8
                )
            ).lines()
        }

        txStream.forEach { line: String? ->
            try {
                val bytes = Bytes.fromHexStringLenient(line)
                dump(bytes)
            } catch (ex: Exception) {
                err(ex.message)
            }
        }
    }

    fun dump(tx: Bytes?) {
        try {
            val transaction = TransactionDecoder.decodeOpaqueBytes(tx, EncodingContext.BLOCK_BODY)

            // https://github.com/hyperledger/besu/blob/5fe49c60b30fe2954c7967e8475c3b3e9afecf35/ethereum/core/src/main/java/org/hyperledger/besu/ethereum/mainnet/MainnetTransactionValidator.java#L252
            if (transaction.chainId.isPresent && transaction.chainId.get() != chainId) {
                throw Exception("wrong chain id")
            }

            // https://github.com/hyperledger/besu/blob/5fe49c60b30fe2954c7967e8475c3b3e9afecf35/ethereum/core/src/main/java/org/hyperledger/besu/ethereum/mainnet/MainnetTransactionValidator.java#L270
            if (transaction.s.compareTo(halfCurveOrder) > 0) {
                throw Exception("signature s out of range")
            }
            out.println(transaction.sender)
        } catch (ex: Exception) {
            err(ex.message)
        }
    }

    fun err(message: String?) {
        out.println("err: $message")
    }

    companion object {
        /** The constant COMMAND_NAME.  */
        const val COMMAND_NAME: String = "txparse"

        val halfCurveOrder: BigInteger = SignatureAlgorithmFactory.getInstance().halfCurveOrder
        val chainId: BigInteger = BigInteger("1", 10)
    }
}
