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
package org.hyperledger.besu.cli.subcommands.storage

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import org.hyperledger.besu.cli.subcommands.storage.RevertMetadataSubCommand.v2ToV1
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.IOException
import java.util.*

/** The revert metadata to v1 subcommand.  */
@CommandLine.Command(
    name = "revert-metadata",
    description = ["Revert database metadata to previous format"],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    subcommands = [v2ToV1::class]
)
class RevertMetadataSubCommand
/** Default Constructor.  */
    : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val parentCommand: StorageSubCommand? = null

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null

    override fun run() {
        spec!!.commandLine().usage(System.out)
    }

    @CommandLine.Command(
        name = "v2-to-v1",
        description = ["Revert a database metadata v2 format to v1 format"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class
    )
    internal class v2ToV1 : Runnable {
        @Suppress("unused")
        @CommandLine.Spec
        private val spec: CommandLine.Model.CommandSpec? = null

        @Suppress("unused")
        @CommandLine.ParentCommand
        private val parentCommand: RevertMetadataSubCommand? = null

        override fun run() {
            val dataDir = parentCommand!!.parentCommand!!.besuCommand!!.dataDir()

            val dbMetadata = dataDir.resolve(METADATA_FILENAME).toFile()
            if (!dbMetadata.exists()) {
                val errMsg = String.format(
                    "Could not find database metadata file %s, check your data dir %s",
                    dbMetadata, dataDir
                )
                LOG.error(errMsg)
                throw IllegalArgumentException(errMsg)
            }
            try {
                val root = MAPPER.readTree(dbMetadata)
                if (!root.has("v2")) {
                    val errMsg = String.format("Database metadata file %s is not in v2 format", dbMetadata)
                    LOG.error(errMsg)
                    throw IllegalArgumentException(errMsg)
                }

                val v2Obj = root["v2"]
                if (!v2Obj.has("format")) {
                    val errMsg = String.format(
                        "Database metadata file %s is malformed, \"format\" field not found", dbMetadata
                    )
                    LOG.error(errMsg)
                    throw IllegalArgumentException(errMsg)
                }

                val formatField = v2Obj["format"].asText()
                val maybePrivacyVersion =
                    if (v2Obj.has("privacyVersion"))
                        OptionalInt.of(v2Obj["privacyVersion"].asInt())
                    else
                        OptionalInt.empty()

                val dataStorageFormat = DataStorageFormat.valueOf(formatField)
                val v1Version =
                    when (dataStorageFormat) {
                        DataStorageFormat.FOREST -> 1
                        DataStorageFormat.BONSAI -> 2
                    }

                @JsonSerialize
                data class V1(val version: Int, val privacyVersion: OptionalInt)

                MAPPER.writeValue(dbMetadata, V1(v1Version, maybePrivacyVersion))
                LOG.info("Successfully reverted database metadata from v2 to v1 in {}", dbMetadata)
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RevertMetadataSubCommand::class.java)
        private const val METADATA_FILENAME = "DATABASE_METADATA.json"
        private val MAPPER: ObjectMapper = ObjectMapper()
            .registerModule(Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .enable(SerializationFeature.INDENT_OUTPUT)
    }
}
