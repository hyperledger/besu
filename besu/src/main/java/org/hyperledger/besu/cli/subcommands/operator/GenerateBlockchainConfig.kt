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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.base.Preconditions
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.common.io.Resources
import jakarta.validation.constraints.NotBlank
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.config.JsonGenesisConfigOptions
import org.hyperledger.besu.config.JsonUtil
import org.hyperledger.besu.consensus.ibft.IbftExtraDataCodec
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec
import org.hyperledger.besu.crypto.*
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.core.Util
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import java.util.stream.IntStream

@CommandLine.Command(
    name = "generate-blockchain-config",
    description = ["Generate node keypairs and genesis file with RLP encoded extra data."],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
internal class GenerateBlockchainConfig : Runnable {
    private val SIGNATURE_ALGORITHM: Supplier<SignatureAlgorithm> =
        Suppliers.memoize { SignatureAlgorithmFactory.getInstance() }

    @CommandLine.Option(
        required = true,
        names = ["--config-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Configuration file."],
        arity = "1..1"
    )
    private val configurationFile: @NotBlank File? = null

    @CommandLine.Option(
        required = true,
        names = ["--to"],
        paramLabel = DefaultCommandValues.MANDATORY_DIRECTORY_FORMAT_HELP,
        description = ["Directory to write output files to."],
        arity = "1..1"
    )
    private val outputDirectory: @NotBlank File? = null

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--genesis-file-name"],
        paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
        description = ["Name of the genesis file. (default: \${DEFAULT-VALUE})"],
        arity = "1..1"
    )
    private val genesisFileName = "genesis.json"

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--private-key-file-name"],
        paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
        description = ["Name of the private key file. (default: \${DEFAULT-VALUE})"],
        arity = "1..1"
    )
    private val privateKeyFileName = "key.priv"

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--public-key-file-name"],
        paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
        description = ["Name of the public key file. (default: \${DEFAULT-VALUE})"],
        arity = "1..1"
    )
    private val publicKeyFileName = "key.pub"

    @CommandLine.ParentCommand
    private val parentCommand: OperatorSubCommand? = null // Picocli injects reference to parent command

    private var operatorConfig: ObjectNode? = null
    private var genesisConfig: ObjectNode? = null
    private var blockchainConfig: ObjectNode? = null
    private var nodesConfig: ObjectNode? = null
    private var generateNodesKeys = false
    private val addressesForGenesisExtraData: MutableList<Address> = ArrayList()
    private var keysDirectory: Path? = null

    override fun run() {
        checkPreconditions()
        generateBlockchainConfig()
    }

    private fun checkPreconditions() {
        Preconditions.checkNotNull(parentCommand)
        Preconditions.checkNotNull(parentCommand!!.parentCommand)
        require(
            !isAnyDuplicate(
                genesisFileName,
                publicKeyFileName,
                privateKeyFileName
            )
        ) { "Output file paths must be unique." }
    }

    /** Generates output directory with all required configuration files.  */
    private fun generateBlockchainConfig() {
        try {
            handleOutputDirectory()
            parseConfig()
            processEcCurve()
            if (generateNodesKeys) {
                generateNodesKeys()
            } else {
                importPublicKeysFromConfig()
            }
            processExtraData()
            writeGenesisFile(outputDirectory!!, genesisFileName, genesisConfig)
        } catch (e: IOException) {
            LOG.error("An error occurred while trying to generate network configuration.", e)
        }
    }

    /** Imports public keys from input configuration.  */
    private fun importPublicKeysFromConfig() {
        LOG.info("Importing public keys from configuration.")
        JsonUtil.getArrayNode(nodesConfig, "keys")
            .ifPresent { keys: ArrayNode ->
                keys.forEach(
                    Consumer { publicKeyJson: JsonNode ->
                        this.importPublicKey(
                            publicKeyJson
                        )
                    })
            }
    }

    /**
     * Imports a single public key.
     *
     * @param publicKeyJson The public key.
     */
    private fun importPublicKey(publicKeyJson: JsonNode) {
        require(publicKeyJson.nodeType == JsonNodeType.STRING) { "Invalid key json of type: " + publicKeyJson.nodeType }
        val publicKeyText = publicKeyJson.asText()

        try {
            val publicKey =
                SIGNATURE_ALGORITHM.get().createPublicKey(Bytes.fromHexString(publicKeyText))

            require(SIGNATURE_ALGORITHM.get().isValidPublicKey(publicKey)) {
                (publicKeyText
                        + " is not a valid public key for elliptic curve "
                        + SIGNATURE_ALGORITHM.get().curveName)
            }

            writeKeypair(publicKey, null)
            LOG.info("Public key imported from configuration.({})", publicKey.toString())
        } catch (e: IOException) {
            LOG.error("An error occurred while trying to import node public key.", e)
        }
    }

    /** Generates nodes keypairs.  */
    private fun generateNodesKeys() {
        val nodesCount = JsonUtil.getInt(nodesConfig, "count", 0)
        LOG.info("Generating {} nodes keys.", nodesCount)
        IntStream.range(0, nodesCount).forEach { node: Int ->
            this.generateNodeKeypair(
                node
            )
        }
    }

    /**
     * Generate a keypair for a node.
     *
     * @param node The number of the node.
     */
    private fun generateNodeKeypair(node: Int) {
        try {
            LOG.info("Generating keypair for node {}.", node)
            val keyPair = SIGNATURE_ALGORITHM.get().generateKeyPair()
            writeKeypair(keyPair.publicKey, keyPair.privateKey)
        } catch (e: IOException) {
            LOG.error("An error occurred while trying to generate node keypair.", e)
        }
    }

    /**
     * Writes public and private keys in separate files. Both are written in the same directory named
     * with the address derived from the public key.
     *
     * @param publicKey The public key.
     * @param privateKey The private key. No file is created if privateKey is NULL.
     * @throws IOException If the file cannot be written or accessed.
     */
    @Throws(IOException::class)
    private fun writeKeypair(publicKey: SECPPublicKey, privateKey: SECPPrivateKey?) {
        val nodeAddress = Util.publicKeyToAddress(publicKey)
        addressesForGenesisExtraData.add(nodeAddress)
        val nodeDirectoryPath = keysDirectory!!.resolve(nodeAddress.toString())
        Files.createDirectory(nodeDirectoryPath)
        createFileAndWrite(nodeDirectoryPath, publicKeyFileName, publicKey.toString())
        if (privateKey != null) {
            createFileAndWrite(nodeDirectoryPath, privateKeyFileName, privateKey.toString())
        }
    }

    /** Computes RLP encoded exta data from pre filled list of addresses.  */
    private fun processExtraData() {
        val configNode =
            JsonUtil.getObjectNode(genesisConfig, "config")
                .orElseThrow { IllegalArgumentException("Missing config section in config file") }

        val genesisConfigOptions =
            JsonGenesisConfigOptions.fromJsonObject(configNode)
        if (genesisConfigOptions.isIbft2) {
            LOG.info("Generating IBFT extra data.")
            val extraData =
                IbftExtraDataCodec.encodeFromAddresses(addressesForGenesisExtraData).toString()
            genesisConfig!!.put("extraData", extraData)
        } else if (genesisConfigOptions.isQbft) {
            LOG.info("Generating QBFT extra data.")
            val extraData =
                QbftExtraDataCodec.encodeFromAddresses(addressesForGenesisExtraData).toString()
            genesisConfig!!.put("extraData", extraData)
        }
    }

    @Throws(IOException::class)
    private fun createFileAndWrite(directory: Path, fileName: String, content: String) {
        val filePath = directory.resolve(fileName)
        Files.write(filePath, content.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW)
    }

    /**
     * Parses the root configuration file and related sub elements.
     *
     * @throws IOException If the file cannot be read or accessed.
     */
    @Throws(IOException::class)
    private fun parseConfig() {
        val configString =
            Resources.toString(configurationFile!!.toPath().toUri().toURL(), StandardCharsets.UTF_8)
        val root = JsonUtil.objectNodeFromString(configString)
        operatorConfig = root
        genesisConfig =
            JsonUtil.getObjectNode(operatorConfig, "genesis").orElse(JsonUtil.createEmptyObjectNode())
        blockchainConfig =
            JsonUtil.getObjectNode(operatorConfig, "blockchain")
                .orElse(JsonUtil.createEmptyObjectNode())
        nodesConfig =
            JsonUtil.getObjectNode(blockchainConfig, "nodes").orElse(JsonUtil.createEmptyObjectNode())
        generateNodesKeys = JsonUtil.getBoolean(nodesConfig, "generate", false)
    }

    /** Sets the selected signature algorithm instance in SignatureAlgorithmFactory.  */
    private fun processEcCurve() {
        val options = GenesisConfig.fromConfig(genesisConfig).configOptions
        val ecCurve = options.ecCurve

        if (ecCurve.isEmpty) {
            SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.createDefault())
            return
        }

        try {
            SignatureAlgorithmFactory.setInstance(SignatureAlgorithmType.create(ecCurve.get()))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid parameter for ecCurve in genesis config: " + e.message
            )
        }
    }

    /**
     * Checks if the output directory exists.
     *
     * @throws IOException If the cannot be accessed or created.
     */
    @Throws(IOException::class)
    private fun handleOutputDirectory() {
        Preconditions.checkNotNull(outputDirectory)
        val outputDirectoryPath = outputDirectory!!.toPath()
        require(
            !(outputDirectory.exists()
                    && outputDirectory.isDirectory
                    && outputDirectory.list() != null && outputDirectory.list().size > 0)
        ) { "Output directory already exists." }
        if (!outputDirectory.exists()) {
            Files.createDirectory(outputDirectoryPath)
        }
        keysDirectory = outputDirectoryPath.resolve("keys")
        Files.createDirectory(keysDirectory)
    }

    /**
     * Write the content of the genesis to the output file.
     *
     * @param directory The directory to write the file to.
     * @param fileName The name of the output file.
     * @param genesis The genesis content.
     * @throws IOException If the genesis file cannot be written or accessed.
     */
    @Throws(IOException::class)
    private fun writeGenesisFile(
        directory: File, fileName: String, genesis: ObjectNode?
    ) {
        LOG.info("Writing genesis file.")
        Files.write(
            directory.toPath().resolve(fileName),
            JsonUtil.getJson(genesis).toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE_NEW
        )
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GenerateBlockchainConfig::class.java)

        private fun isAnyDuplicate(vararg values: String): Boolean {
            val set: MutableSet<String> = HashSet()
            for (value in values) {
                if (!set.add(value)) {
                    return true
                }
            }
            return false
        }
    }
}
