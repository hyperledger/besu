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
package org.hyperledger.besu.cli.options

import com.google.common.base.Strings
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.DefaultCommandValues.Companion.DEFAULT_JWT_ALGORITHM
import org.hyperledger.besu.cli.custom.RpcAuthFileValidator.validate
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration
import org.slf4j.Logger
import picocli.CommandLine
import java.io.File
import java.util.function.Predicate
import java.util.stream.Collectors

/** This class represents the WebSocket options for the RPC.  */
class RpcWebsocketOptions
/** Default Constructor.  */
{
    internal class KeystorePasswordOptions {
        @CommandLine.Option(
            names = ["--rpc-ws-ssl-keystore-password"],
            paramLabel = "<PASSWORD>",
            description = ["Password for the WebSocket RPC keystore file"]
        )
        internal val rpcWsKeyStorePassword: String? = null

        @CommandLine.Option(
            names = ["--rpc-ws-ssl-keystore-password-file"],
            paramLabel = "<FILE>",
            description = ["File containing the password for WebSocket keystore."]
        )
        val rpcWsKeystorePasswordFile: String? = null
    }

    internal class TruststorePasswordOptions {
        @CommandLine.Option(
            names = ["--rpc-ws-ssl-truststore-password"],
            paramLabel = "<PASSWORD>",
            description = ["Password for the WebSocket RPC truststore file"]
        )
        val rpcWsTrustStorePassword: String? = null

        @CommandLine.Option(
            names = ["--rpc-ws-ssl-truststore-password-file"],
            paramLabel = "<FILE>",
            description = ["File containing the password for WebSocket truststore."]
        )
        val rpcWsTruststorePasswordFile: String? = null
    }

    @CommandLine.Option(
        names = ["--rpc-ws-authentication-jwt-algorithm"],
        description = [("Encryption algorithm used for Websockets JWT public key. Possible values are \${COMPLETION-CANDIDATES}"
                + " (default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private val rpcWebsocketsAuthenticationAlgorithm = DEFAULT_JWT_ALGORITHM

    /**
     * Checks if the WebSocket service is enabled.
     *
     * @return Boolean indicating if the WebSocket service is enabled
     */
    @JvmField
    @CommandLine.Option(
        names = ["--rpc-ws-enabled"],
        description = ["Set to start the JSON-RPC WebSocket service (default: \${DEFAULT-VALUE})"]
    )
    val isRpcWsEnabled: Boolean = false

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--rpc-ws-host"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["Host for JSON-RPC WebSocket service to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private val rpcWsHost: String? = null

    /**
     * Returns the port for the WebSocket service.
     *
     * @return Port number
     */
    @JvmField
    @CommandLine.Option(
        names = ["--rpc-ws-port"],
        paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
        description = ["Port for JSON-RPC WebSocket service to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    val rpcWsPort: Int = WebSocketConfiguration.DEFAULT_WEBSOCKET_PORT

    @CommandLine.Option(
        names = ["--rpc-ws-max-frame-size"],
        description = ["Maximum size in bytes for JSON-RPC WebSocket frames (default: \${DEFAULT-VALUE}). If this limit is exceeded, the websocket will be disconnected."],
        arity = "1"
    )
    private val rpcWsMaxFrameSize = DefaultCommandValues.DEFAULT_WS_MAX_FRAME_SIZE

    @CommandLine.Option(
        names = ["--rpc-ws-max-active-connections"],
        description = ["Maximum number of WebSocket connections allowed for JSON-RPC (default: \${DEFAULT-VALUE}). Once this limit is reached, incoming connections will be rejected."],
        arity = "1"
    )
    private val rpcWsMaxConnections = DefaultCommandValues.DEFAULT_WS_MAX_CONNECTIONS

    /**
     * Returns the list of APIs for the WebSocket.
     *
     * @return List of APIs
     */
    @JvmField
    @CommandLine.Option(
        names = ["--rpc-ws-api", "--rpc-ws-apis"],
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description = ["Comma separated list of APIs to enable on JSON-RPC WebSocket service (default: \${DEFAULT-VALUE})"]
    )
    val rpcWsApis: List<String> = RpcApis.DEFAULT_RPC_APIS

    @CommandLine.Option(
        names = ["--rpc-ws-api-methods-no-auth", "--rpc-ws-api-method-no-auth"],
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description = ["Comma separated list of RPC methods to exclude from RPC authentication services, RPC WebSocket authentication must be enabled"]
    )
    private val rpcWsApiMethodsNoAuth: List<String> = ArrayList()

    @CommandLine.Option(
        names = ["--rpc-ws-authentication-enabled"],
        description = ["Require authentication for the JSON-RPC WebSocket service (default: \${DEFAULT-VALUE})"]
    )
    private val isRpcWsAuthenticationEnabled = false

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--rpc-ws-authentication-credentials-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Storage file for JSON-RPC WebSocket authentication credentials (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private val rpcWsAuthenticationCredentialsFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-ws-authentication-jwt-public-key-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["JWT public key file for JSON-RPC WebSocket authentication"],
        arity = "1"
    )
    private val rpcWsAuthenticationPublicKeyFile: File? = null

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-enabled"],
        description = ["Enable SSL/TLS for the WebSocket RPC service"]
    )
    private val isRpcWsSslEnabled = false

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-keystore-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the keystore file for the WebSocket RPC service"]
    )
    private val rpcWsKeyStoreFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-key-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the PEM key file for the WebSocket RPC service"]
    )
    private val rpcWsKeyFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-cert-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the PEM cert file for the WebSocket RPC service"]
    )
    private val rpcWsCertFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-keystore-type"],
        paramLabel = "<TYPE>",
        description = ["Type of the WebSocket RPC keystore (JKS, PKCS12, PEM)"]
    )
    private val rpcWsKeyStoreType: String? = null

    // For client authentication (mTLS)
    @CommandLine.Option(
        names = ["--rpc-ws-ssl-client-auth-enabled"],
        description = ["Enable client authentication for the WebSocket RPC service"]
    )
    private val isRpcWsClientAuthEnabled = false

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-truststore-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the truststore file for the WebSocket RPC service"]
    )
    private val rpcWsTrustStoreFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-trustcert-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the PEM trustcert file for the WebSocket RPC service"]
    )
    private val rpcWsTrustCertFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-ws-ssl-truststore-type"],
        paramLabel = "<TYPE>",
        description = ["Type of the truststore (JKS, PKCS12, PEM)"]
    )
    private val rpcWsTrustStoreType: String? = null

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private val keystorePasswordOptions: KeystorePasswordOptions? = null

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private val truststorePasswordOptions: TruststorePasswordOptions? = null

    /**
     * Validates the WebSocket options.
     *
     * @param logger Logger instance
     * @param commandLine CommandLine instance
     * @param configuredApis Predicate for configured APIs
     */
    fun validate(
        logger: Logger, commandLine: CommandLine, configuredApis: Predicate<String>?
    ) {
        checkOptionDependencies(logger, commandLine)

        if (!rpcWsApis.stream().allMatch(configuredApis)) {
            val invalidWsApis: MutableList<String> = ArrayList(rpcWsApis)
            invalidWsApis.removeAll(RpcApis.VALID_APIS)
            throw CommandLine.ParameterException(
                commandLine,
                "Invalid value for option '--rpc-ws-api': invalid entries found $invalidWsApis"
            )
        }

        val validWsApiMethods =
            rpcWsApiMethodsNoAuth.stream()
                .allMatch { rpcMethodName: String? -> RpcMethod.rpcMethodExists(rpcMethodName) }

        if (!validWsApiMethods) {
            throw CommandLine.ParameterException(
                commandLine,
                "Invalid value for option '--rpc-ws-api-methods-no-auth', options must be valid RPC methods"
            )
        }

        if (isRpcWsAuthenticationEnabled
            && rpcWsAuthenticationCredentialsFile(commandLine) == null && rpcWsAuthenticationPublicKeyFile == null
        ) {
            throw CommandLine.ParameterException(
                commandLine,
                "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file or authentication public key file"
            )
        }
    }

    /**
     * Checks the dependencies of the WebSocket options.
     *
     * @param logger Logger instance
     * @param commandLine CommandLine instance
     */
    private fun checkOptionDependencies(logger: Logger, commandLine: CommandLine) {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-ws-enabled",
            !isRpcWsEnabled,
            listOf(
                "--rpc-ws-api",
                "--rpc-ws-apis",
                "--rpc-ws-api-method-no-auth",
                "--rpc-ws-api-methods-no-auth",
                "--rpc-ws-host",
                "--rpc-ws-port",
                "--rpc-ws-max-frame-size",
                "--rpc-ws-max-active-connections",
                "--rpc-ws-authentication-enabled",
                "--rpc-ws-authentication-credentials-file",
                "--rpc-ws-authentication-public-key-file",
                "--rpc-ws-authentication-jwt-algorithm",
                "--rpc-ws-ssl-enabled"
            )
        )

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-ws-ssl-enabled",
            !isRpcWsSslEnabled,
            listOf(
                "--rpc-ws-ssl-keystore-file",
                "--rpc-ws-ssl-keystore-type",
                "--rpc-ws-ssl-client-auth-enabled"
            )
        )

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-ws-ssl-client-auth-enabled",
            !isRpcWsClientAuthEnabled,
            listOf(
                "--rpc-ws-ssl-truststore-file",
                "--rpc-ws-ssl-truststore-type",
                "--rpc-ws-ssl-trustcert-file"
            )
        )

        if (isRpcWsSslEnabled) {
            if ("PEM".equals(rpcWsKeyStoreType, ignoreCase = true)) {
                CommandLineUtils.checkOptionDependencies(
                    logger,
                    commandLine,
                    "--rpc-ws-ssl-key-file",
                    rpcWsKeyFile == null,
                    listOf("--rpc-ws-ssl-cert-file")
                )
                CommandLineUtils.checkOptionDependencies(
                    logger,
                    commandLine,
                    "--rpc-ws-ssl-cert-file",
                    rpcWsCertFile == null,
                    listOf("--rpc-ws-ssl-key-file")
                )
            } else {
                CommandLineUtils.checkOptionDependencies(
                    logger,
                    commandLine,
                    "--rpc-ws-ssl-keystore-file",
                    rpcWsKeyStoreFile == null,
                    listOf("--rpc-ws-ssl-keystore-password", "--rpc-ws-ssl-keystore-password-file")
                )
            }
        }

        if (isRpcWsClientAuthEnabled && !"PEM".equals(rpcWsTrustStoreType, ignoreCase = true)) {
            CommandLineUtils.checkOptionDependencies(
                logger,
                commandLine,
                "--rpc-ws-ssl-truststore-file",
                rpcWsTrustStoreFile == null,
                listOf("--rpc-ws-ssl-truststore-password", "--rpc-ws-ssl-truststore-password-file")
            )
        }

        if (isRpcWsAuthenticationEnabled) {
            CommandLineUtils.checkOptionDependencies(
                logger,
                commandLine,
                "--rpc-ws-authentication-public-key-file",
                rpcWsAuthenticationPublicKeyFile == null,
                listOf("--rpc-ws-authentication-jwt-algorithm")
            )
        }
    }

    /**
     * Creates a WebSocket configuration based on the WebSocket options.
     *
     * @param hostsAllowlist List of allowed hosts
     * @param defaultHostAddress Default host address
     * @param wsTimoutSec WebSocket timeout in seconds
     * @return WebSocketConfiguration instance
     */
    fun webSocketConfiguration(
        hostsAllowlist: List<String?>?, defaultHostAddress: String?, wsTimoutSec: Long
    ): WebSocketConfiguration {
        val webSocketConfiguration = WebSocketConfiguration.createDefault()
        webSocketConfiguration.isEnabled = isRpcWsEnabled
        webSocketConfiguration.host =
            if (Strings.isNullOrEmpty(rpcWsHost)) defaultHostAddress else rpcWsHost
        webSocketConfiguration.port = rpcWsPort
        webSocketConfiguration.maxFrameSize = rpcWsMaxFrameSize
        webSocketConfiguration.maxActiveConnections = rpcWsMaxConnections
        webSocketConfiguration.setRpcApis(rpcWsApis)
        webSocketConfiguration.setRpcApisNoAuth(
            rpcWsApiMethodsNoAuth.stream().distinct().collect(Collectors.toList())
        )
        webSocketConfiguration.isAuthenticationEnabled = isRpcWsAuthenticationEnabled
        webSocketConfiguration.authenticationCredentialsFile = rpcWsAuthenticationCredentialsFile
        webSocketConfiguration.setHostsAllowlist(hostsAllowlist)
        webSocketConfiguration.authenticationPublicKeyFile = rpcWsAuthenticationPublicKeyFile
        webSocketConfiguration.authenticationAlgorithm = rpcWebsocketsAuthenticationAlgorithm
        webSocketConfiguration.timeoutSec = wsTimoutSec
        webSocketConfiguration.isSslEnabled = isRpcWsSslEnabled
        webSocketConfiguration.setKeyStorePath(rpcWsKeyStoreFile)
        webSocketConfiguration.setKeyStoreType(rpcWsKeyStoreType)
        webSocketConfiguration.isClientAuthEnabled = isRpcWsClientAuthEnabled
        webSocketConfiguration.setTrustStorePath(rpcWsTrustStoreFile)
        webSocketConfiguration.setTrustStoreType(rpcWsTrustStoreType)
        webSocketConfiguration.setKeyPath(rpcWsKeyFile)
        webSocketConfiguration.setCertPath(rpcWsCertFile)
        webSocketConfiguration.setTrustCertPath(rpcWsTrustCertFile)

        if (keystorePasswordOptions != null) {
            webSocketConfiguration.setKeyStorePassword(keystorePasswordOptions.rpcWsKeyStorePassword)
            webSocketConfiguration.setKeyStorePasswordFile(
                keystorePasswordOptions.rpcWsKeystorePasswordFile
            )
        }

        if (truststorePasswordOptions != null) {
            webSocketConfiguration.setTrustStorePassword(
                truststorePasswordOptions.rpcWsTrustStorePassword
            )
            webSocketConfiguration.setTrustStorePasswordFile(
                truststorePasswordOptions.rpcWsTruststorePasswordFile
            )
        }

        return webSocketConfiguration
    }

    /**
     * Validates the authentication credentials file for the WebSocket.
     *
     * @param commandLine CommandLine instance
     * @return Filename of the authentication credentials file
     */
    private fun rpcWsAuthenticationCredentialsFile(commandLine: CommandLine): String? {
        val filename = rpcWsAuthenticationCredentialsFile

        if (filename != null) {
            validate(commandLine, filename, "WS")
        }
        return filename
    }
}
