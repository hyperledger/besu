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

import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration
import org.hyperledger.besu.nat.NatMethod
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.nio.file.*

/** The interface Default command values.  */
interface DefaultCommandValues {
    companion object {
        /**
         * Gets default besu data path.
         *
         * @param command the command
         * @return the default besu data path
         */
        @JvmStatic
        fun getDefaultBesuDataPath(command: Any): Path {
            // this property is retrieved from Gradle tasks or Besu running shell script.
            val besuHomeProperty = System.getProperty(BESU_HOME_PROPERTY_NAME)
            val besuHome: Path

            // If prop is found, then use it
            if (besuHomeProperty != null) {
                try {
                    besuHome = Paths.get(besuHomeProperty)
                } catch (e: InvalidPathException) {
                    throw CommandLine.ParameterException(
                        CommandLine(command),
                        String.format(
                            "Unable to define default data directory from %s property.",
                            BESU_HOME_PROPERTY_NAME
                        ),
                        e
                    )
                }
            } else {
                // otherwise use a default path.
                // That may only be used when NOT run from distribution script and Gradle as they all define
                // the property.
                try {
                    val path = File(DEFAULT_DATA_DIR_PATH).canonicalPath
                    besuHome = Paths.get(path)
                } catch (e: IOException) {
                    throw CommandLine.ParameterException(
                        CommandLine(command), "Unable to create default data directory."
                    )
                }
            }

            // Try to create it, then verify if the provided path is not already existing and is not a
            // directory. Otherwise, if it doesn't exist or exists but is already a directory,
            // Runner will use it to store data.
            try {
                Files.createDirectories(besuHome)
            } catch (e: FileAlreadyExistsException) {
                // Only thrown if it exists but is not a directory
                throw CommandLine.ParameterException(
                    CommandLine(command),
                    String.format("%s: already exists and is not a directory.", besuHome.toAbsolutePath()),
                    e
                )
            } catch (e: Exception) {
                throw CommandLine.ParameterException(
                    CommandLine(command),
                    String.format("Error creating directory %s.", besuHome.toAbsolutePath()),
                    e
                )
            }
            return besuHome
        }

        /** The constant CONFIG_FILE_OPTION_NAME.  */
        const val CONFIG_FILE_OPTION_NAME: String = "--config-file"

        /** The constant MANDATORY_PATH_FORMAT_HELP.  */
        const val MANDATORY_PATH_FORMAT_HELP: String = "<PATH>"

        /** The constant MANDATORY_FILE_FORMAT_HELP.  */
        const val MANDATORY_FILE_FORMAT_HELP: String = "<FILE>"

        /** The constant MANDATORY_DIRECTORY_FORMAT_HELP.  */
        const val MANDATORY_DIRECTORY_FORMAT_HELP: String = "<DIRECTORY>"

        /** The constant BESU_HOME_PROPERTY_NAME.  */
        const val BESU_HOME_PROPERTY_NAME: String = "besu.home"

        /** The constant DEFAULT_DATA_DIR_PATH.  */
        const val DEFAULT_DATA_DIR_PATH: String = "./build/data"

        /** The constant MANDATORY_INTEGER_FORMAT_HELP.  */
        const val MANDATORY_INTEGER_FORMAT_HELP: String = "<INTEGER>"

        /** The constant MANDATORY_DOUBLE_FORMAT_HELP.  */
        const val MANDATORY_DOUBLE_FORMAT_HELP: String = "<DOUBLE>"

        /** The constant MANDATORY_LONG_FORMAT_HELP.  */
        const val MANDATORY_LONG_FORMAT_HELP: String = "<LONG>"

        /** The constant MANDATORY_MODE_FORMAT_HELP.  */
        const val MANDATORY_MODE_FORMAT_HELP: String = "<MODE>"

        /** The constant MANDATORY_NETWORK_FORMAT_HELP.  */
        const val MANDATORY_NETWORK_FORMAT_HELP: String = "<NETWORK>"

        /** The constant PROFILE_OPTION_NAME.  */
        const val PROFILE_OPTION_NAME: String = "--profile"

        /** The constant PROFILE_FORMAT_HELP.  */
        const val PROFILE_FORMAT_HELP: String = "<PROFILE>"

        /** The constant MANDATORY_NODE_ID_FORMAT_HELP.  */
        const val MANDATORY_NODE_ID_FORMAT_HELP: String = "<NODEID>"

        /** The constant PERMISSIONING_CONFIG_LOCATION.  */
        const val PERMISSIONING_CONFIG_LOCATION: String = "permissions_config.toml"

        /** The constant MANDATORY_HOST_FORMAT_HELP.  */
        const val MANDATORY_HOST_FORMAT_HELP: String = "<HOST>"

        /** The constant MANDATORY_PORT_FORMAT_HELP.  */
        const val MANDATORY_PORT_FORMAT_HELP: String = "<PORT>"

        /** The constant DEFAULT_NAT_METHOD.  */
//        @JvmField
        val DEFAULT_NAT_METHOD: NatMethod = NatMethod.AUTO

        /** The constant DEFAULT_JWT_ALGORITHM.  */
//        @JvmField
        val DEFAULT_JWT_ALGORITHM: JwtAlgorithm = JwtAlgorithm.RS256

        /** The constant SYNC_MIN_PEER_COUNT.  */
        const val SYNC_MIN_PEER_COUNT: Int = 5

        /** The constant DEFAULT_MAX_PEERS.  */
        const val DEFAULT_MAX_PEERS: Int = 25

        /** The constant DEFAULT_HTTP_MAX_CONNECTIONS.  */
        const val DEFAULT_HTTP_MAX_CONNECTIONS: Int = 80

        /** The constant DEFAULT_HTTP_MAX_BATCH_SIZE.  */
        const val DEFAULT_HTTP_MAX_BATCH_SIZE: Int = 1024

        /** The constant DEFAULT_MAX_REQUEST_CONTENT_LENGTH.  */
        const val DEFAULT_MAX_REQUEST_CONTENT_LENGTH: Long = (5 * 1024 * 1024 // 5MB
                ).toLong()

        /** The constant DEFAULT_WS_MAX_CONNECTIONS.  */
        const val DEFAULT_WS_MAX_CONNECTIONS: Int = 80

        /** The constant DEFAULT_WS_MAX_FRAME_SIZE.  */
        const val DEFAULT_WS_MAX_FRAME_SIZE: Int = 1024 * 1024

        /** The constant DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED.  */
        const val DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED: Float =
            RlpxConfiguration.DEFAULT_FRACTION_REMOTE_CONNECTIONS_ALLOWED

        /** The constant DEFAULT_KEY_VALUE_STORAGE_NAME.  */
        const val DEFAULT_KEY_VALUE_STORAGE_NAME: String = "rocksdb"

        /** The constant DEFAULT_SECURITY_MODULE.  */
        const val DEFAULT_SECURITY_MODULE: String = "localfile"

        /** The constant DEFAULT_KEYSTORE_TYPE.  */
        const val DEFAULT_KEYSTORE_TYPE: String = "JKS"

        /** The Default tls protocols.  */
//        @JvmField
        val DEFAULT_TLS_PROTOCOLS: List<String> = listOf("TLSv1.3", "TLSv1.2")

        /** The constant DEFAULT_PLUGINS_OPTION_NAME.  */
        const val DEFAULT_PLUGINS_OPTION_NAME: String = "--plugins"

        /** The constant DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME.  */
        const val DEFAULT_CONTINUE_ON_PLUGIN_ERROR_OPTION_NAME: String = "--plugin-continue-on-error"

        /** The constant DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME.  */
        const val DEFAULT_PLUGINS_EXTERNAL_ENABLED_OPTION_NAME: String = "--Xplugins-external-enabled"
    }
}
