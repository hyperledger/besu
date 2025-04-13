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
import org.hyperledger.besu.cli.DefaultCommandValues.Companion.DEFAULT_TLS_PROTOCOLS
import org.hyperledger.besu.cli.custom.CorsAllowedOriginsProperty
import org.hyperledger.besu.cli.custom.RpcAuthFileValidator.validate
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod
import org.hyperledger.besu.ethereum.api.tls.FileBasedPasswordProvider
import org.hyperledger.besu.ethereum.api.tls.TlsClientAuthConfiguration
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration
import org.slf4j.Logger
import picocli.CommandLine
import java.io.File
import java.nio.file.Path
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors
import javax.net.ssl.SSLContext

/**
 * Handles configuration options for the JSON-RPC HTTP service, including validation and creation of
 * a JSON-RPC configuration.
 */
// TODO: implement CLIOption<JsonRpcConfiguration>
class JsonRpcHttpOptions
/** Default constructor  */
{
    /**
     * Checks if RPC over HTTP is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @JvmField
    @CommandLine.Option(
        names = ["--rpc-http-enabled"],
        description = ["Set to start the JSON-RPC HTTP service (default: \${DEFAULT-VALUE})"]
    )
    val isRpcHttpEnabled: Boolean = false

    /**
     * Returns the host for RPC over HTTP.
     *
     * @return The port number
     */
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--rpc-http-host"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["Host for JSON-RPC HTTP to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    val rpcHttpHost: String = JsonRpcConfiguration.DEFAULT_JSON_RPC_HOST

    /**
     * Returns the port for RPC over HTTP.
     *
     * @return The port number
     */
    @JvmField
    @CommandLine.Option(
        names = ["--rpc-http-port"],
        paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
        description = ["Port for JSON-RPC HTTP to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    val rpcHttpPort: Int = JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT

    @CommandLine.Option(
        names = ["--rpc-http-max-active-connections"],
        description = ["Maximum number of HTTP connections allowed for JSON-RPC (default: \${DEFAULT-VALUE}). Once this limit is reached, incoming connections will be rejected."],
        arity = "1"
    )
    private val rpcHttpMaxConnections = DefaultCommandValues.DEFAULT_HTTP_MAX_CONNECTIONS

    // A list of origins URLs that are accepted by the JsonRpcHttpServer (CORS)
    @CommandLine.Option(
        names = ["--rpc-http-cors-origins"],
        description = ["Comma separated origin domain URLs for CORS validation (default: none)"]
    )
    private val rpcHttpCorsAllowedOrigins = CorsAllowedOriginsProperty()

    /**
     * Returns the list of APIs enabled for RPC over HTTP.
     *
     * @return A list of APIs
     */
    @JvmField
    @CommandLine.Option(
        names = ["--rpc-http-api", "--rpc-http-apis"],
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description = ["Comma separated list of APIs to enable on JSON-RPC HTTP service (default: \${DEFAULT-VALUE})"]
    )
    val rpcHttpApis: List<String> = RpcApis.DEFAULT_RPC_APIS

    @CommandLine.Option(
        names = ["--rpc-http-api-method-no-auth", "--rpc-http-api-methods-no-auth"],
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description = ["Comma separated list of API methods to exclude from RPC authentication services, RPC HTTP authentication must be enabled"]
    )
    private val rpcHttpApiMethodsNoAuth: List<String> = ArrayList()

    @CommandLine.Option(
        names = ["--rpc-http-authentication-enabled"],
        description = ["Require authentication for the JSON-RPC HTTP service (default: \${DEFAULT-VALUE})"]
    )
    private val isRpcHttpAuthenticationEnabled = false

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--rpc-http-authentication-credentials-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Storage file for JSON-RPC HTTP authentication credentials (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    private val rpcHttpAuthenticationCredentialsFile: String? = null

    @CommandLine.Option(
        names = ["--rpc-http-authentication-jwt-public-key-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["JWT public key file for JSON-RPC HTTP authentication"],
        arity = "1"
    )
    private val rpcHttpAuthenticationPublicKeyFile: File? = null

    @CommandLine.Option(
        names = ["--rpc-http-authentication-jwt-algorithm"],
        description = [("Encryption algorithm used for HTTP JWT public key. Possible values are \${COMPLETION-CANDIDATES}"
                + " (default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private val rpcHttpAuthenticationAlgorithm = DEFAULT_JWT_ALGORITHM

    @CommandLine.Option(
        names = ["--rpc-http-tls-enabled"],
        description = ["Enable TLS for the JSON-RPC HTTP service (default: \${DEFAULT-VALUE})"]
    )
    private val isRpcHttpTlsEnabled = false

    @CommandLine.Option(
        names = ["--rpc-http-tls-keystore-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Keystore (PKCS#12) containing key/certificate for the JSON-RPC HTTP service. Required if TLS is enabled."]
    )
    private val rpcHttpTlsKeyStoreFile: Path? = null

    @CommandLine.Option(
        names = ["--rpc-http-tls-keystore-password-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["File containing password to unlock keystore for the JSON-RPC HTTP service. Required if TLS is enabled."]
    )
    private val rpcHttpTlsKeyStorePasswordFile: Path? = null

    @CommandLine.Option(
        names = ["--rpc-http-tls-client-auth-enabled"],
        description = ["Enable TLS client authentication for the JSON-RPC HTTP service (default: \${DEFAULT-VALUE})"]
    )
    private val isRpcHttpTlsClientAuthEnabled = false

    @CommandLine.Option(
        names = ["--rpc-http-tls-known-clients-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to file containing clients certificate common name and fingerprint for client authentication"]
    )
    private val rpcHttpTlsKnownClientsFile: Path? = null

    @CommandLine.Option(
        names = ["--rpc-http-tls-ca-clients-enabled"],
        description = ["Enable to accept clients certificate signed by a valid CA for client authentication (default: \${DEFAULT-VALUE})"]
    )
    private val isRpcHttpTlsCAClientsEnabled = false

    @CommandLine.Option(
        names = ["--rpc-http-tls-truststore-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the truststore file for the JSON-RPC HTTP service."],
        arity = "1"
    )
    private val rpcHttpTlsTruststoreFile: Path? = null

    @CommandLine.Option(
        names = ["--rpc-http-tls-truststore-password-file"],
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = ["Path to the file containing the password for the truststore."],
        arity = "1"
    )
    private val rpcHttpTlsTruststorePasswordFile: Path? = null

    @CommandLine.Option(
        names = ["--rpc-http-tls-protocol", "--rpc-http-tls-protocols"],
        description = ["Comma separated list of TLS protocols to support (default: \${DEFAULT-VALUE})"],
        split = ",",
        arity = "1..*"
    )
    private val rpcHttpTlsProtocols: MutableList<String> = ArrayList(DEFAULT_TLS_PROTOCOLS)

    @CommandLine.Option(
        names = ["--rpc-http-tls-cipher-suite", "--rpc-http-tls-cipher-suites"],
        description = ["Comma separated list of TLS cipher suites to support"],
        split = ",",
        arity = "1..*"
    )
    private val rpcHttpTlsCipherSuites: MutableList<String> = ArrayList()

    @CommandLine.Option(
        names = ["--rpc-http-max-batch-size"],
        paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
        description = ["Specifies the maximum number of requests in a single RPC batch request via RPC. -1 specifies no limit  (default: \${DEFAULT-VALUE})"]
    )
    private val rpcHttpMaxBatchSize = DefaultCommandValues.DEFAULT_HTTP_MAX_BATCH_SIZE

    @CommandLine.Option(
        names = ["--rpc-http-max-request-content-length"],
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = ["Specifies the maximum request content length. (default: \${DEFAULT-VALUE})"]
    )
    private val rpcHttpMaxRequestContentLength = DefaultCommandValues.DEFAULT_MAX_REQUEST_CONTENT_LENGTH

    @CommandLine.Option(
        names = ["--json-pretty-print-enabled"],
        description = ["Enable JSON pretty print format (default: \${DEFAULT-VALUE})"]
    )
    private val prettyJsonEnabled = JsonRpcConfiguration.DEFAULT_PRETTY_JSON_ENABLED

    /**
     * Validates the Rpc Http options.
     *
     * @param logger Logger instance
     * @param commandLine CommandLine instance
     * @param configuredApis Predicate for configured APIs
     */
    fun validate(
        logger: Logger?, commandLine: CommandLine, configuredApis: Predicate<String>?
    ) {
        if (!rpcHttpApis.stream().allMatch(configuredApis)) {
            val invalidHttpApis: MutableList<String> = ArrayList(rpcHttpApis)
            invalidHttpApis.removeAll(RpcApis.VALID_APIS)
            throw CommandLine.ParameterException(
                commandLine,
                "Invalid value for option '--rpc-http-api': invalid entries found $invalidHttpApis"
            )
        }

        val validHttpApiMethods =
            rpcHttpApiMethodsNoAuth.stream()
                .allMatch { rpcMethodName: String? -> RpcMethod.rpcMethodExists(rpcMethodName) }

        if (!validHttpApiMethods) {
            throw CommandLine.ParameterException(
                commandLine,
                "Invalid value for option '--rpc-http-api-methods-no-auth', options must be valid RPC methods"
            )
        }

        if (isRpcHttpAuthenticationEnabled) {
            CommandLineUtils.checkOptionDependencies(
                logger,
                commandLine,
                "--rpc-http-authentication-public-key-file",
                rpcHttpAuthenticationPublicKeyFile == null,
                listOf("--rpc-http-authentication-jwt-algorithm")
            )
        }

        if (isRpcHttpAuthenticationEnabled
            && rpcHttpAuthenticationCredentialsFile(commandLine) == null && rpcHttpAuthenticationPublicKeyFile == null
        ) {
            throw CommandLine.ParameterException(
                commandLine,
                "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file or authentication public key file"
            )
        }

        checkDependencies(logger, commandLine)

        if (isRpcTlsConfigurationRequired) {
            validateTls(commandLine)
        }
    }

    /**
     * Creates a JsonRpcConfiguration based on the provided options.
     *
     * @return configuration populated from options or defaults
     */
    fun jsonRpcConfiguration(): JsonRpcConfiguration {
        val jsonRpcConfiguration = JsonRpcConfiguration.createDefault()
        jsonRpcConfiguration.isEnabled = isRpcHttpEnabled
        jsonRpcConfiguration.port = rpcHttpPort
        jsonRpcConfiguration.maxActiveConnections = rpcHttpMaxConnections
        jsonRpcConfiguration.setCorsAllowedDomains(rpcHttpCorsAllowedOrigins)
        jsonRpcConfiguration.setRpcApis(rpcHttpApis.stream().distinct().collect(Collectors.toList()))
        jsonRpcConfiguration.setNoAuthRpcApis(
            rpcHttpApiMethodsNoAuth.stream().distinct().collect(Collectors.toList())
        )
        jsonRpcConfiguration.isAuthenticationEnabled = isRpcHttpAuthenticationEnabled
        jsonRpcConfiguration.authenticationCredentialsFile = rpcHttpAuthenticationCredentialsFile
        jsonRpcConfiguration.authenticationPublicKeyFile = rpcHttpAuthenticationPublicKeyFile
        jsonRpcConfiguration.authenticationAlgorithm = rpcHttpAuthenticationAlgorithm
        jsonRpcConfiguration.tlsConfiguration = rpcHttpTlsConfiguration()
        jsonRpcConfiguration.maxBatchSize = rpcHttpMaxBatchSize
        jsonRpcConfiguration.maxRequestContentLength = rpcHttpMaxRequestContentLength
        jsonRpcConfiguration.isPrettyJsonEnabled = prettyJsonEnabled
        return jsonRpcConfiguration
    }

    /**
     * Creates a JsonRpcConfiguration based on the provided options.
     *
     * @param hostsAllowlist List of hosts allowed
     * @param defaultHostAddress Default host address
     * @param timoutSec timeout in seconds
     * @return A JsonRpcConfiguration instance
     */
    fun jsonRpcConfiguration(
        hostsAllowlist: List<String?>?, defaultHostAddress: String?, timoutSec: Long
    ): JsonRpcConfiguration {
        val jsonRpcConfiguration = this.jsonRpcConfiguration()

        jsonRpcConfiguration.host =
            if (Strings.isNullOrEmpty(rpcHttpHost)) defaultHostAddress else rpcHttpHost
        jsonRpcConfiguration.setHostsAllowlist(hostsAllowlist)
        jsonRpcConfiguration.httpTimeoutSec = timoutSec
        return jsonRpcConfiguration
    }

    /**
     * Checks dependencies between options.
     *
     * @param logger Logger instance
     * @param commandLine CommandLine instance
     */
    fun checkDependencies(logger: Logger?, commandLine: CommandLine) {
        checkRpcTlsClientAuthOptionsDependencies(logger, commandLine)
        checkRpcTlsOptionsDependencies(logger, commandLine)
        checkRpcHttpOptionsDependencies(logger, commandLine)
    }

    private fun checkRpcTlsClientAuthOptionsDependencies(
        logger: Logger?, commandLine: CommandLine
    ) {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-http-tls-client-auth-enabled",
            !isRpcHttpTlsClientAuthEnabled,
            mutableListOf(
                "--rpc-http-tls-known-clients-file",
                "--rpc-http-tls-ca-clients-enabled",
                "--rpc-http-tls-truststore-file",
                "--rpc-http-tls-truststore-password-file"
            )
        )

        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-http-tls-truststore-file",
            rpcHttpTlsTruststoreFile == null,
            mutableListOf("--rpc-http-tls-truststore-password-file")
        )
    }

    private fun checkRpcTlsOptionsDependencies(logger: Logger?, commandLine: CommandLine) {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-http-tls-enabled",
            !isRpcHttpTlsEnabled,
            mutableListOf(
                "--rpc-http-tls-keystore-file",
                "--rpc-http-tls-keystore-password-file",
                "--rpc-http-tls-client-auth-enabled",
                "--rpc-http-tls-known-clients-file",
                "--rpc-http-tls-ca-clients-enabled",
                "--rpc-http-tls-protocols",
                "--rpc-http-tls-cipher-suite",
                "--rpc-http-tls-cipher-suites"
            )
        )
    }

    private fun checkRpcHttpOptionsDependencies(logger: Logger?, commandLine: CommandLine) {
        CommandLineUtils.checkOptionDependencies(
            logger,
            commandLine,
            "--rpc-http-enabled",
            !isRpcHttpEnabled,
            mutableListOf(
                "--rpc-http-api",
                "--rpc-http-apis",
                "--rpc-http-api-method-no-auth",
                "--rpc-http-api-methods-no-auth",
                "--rpc-http-cors-origins",
                "--rpc-http-host",
                "--rpc-http-port",
                "--rpc-http-max-active-connections",
                "--rpc-http-authentication-enabled",
                "--rpc-http-authentication-credentials-file",
                "--rpc-http-authentication-public-key-file",
                "--rpc-http-tls-enabled",
                "--rpc-http-tls-keystore-file",
                "--rpc-http-tls-keystore-password-file",
                "--rpc-http-tls-client-auth-enabled",
                "--rpc-http-tls-known-clients-file",
                "--rpc-http-tls-ca-clients-enabled",
                "--rpc-http-authentication-jwt-algorithm",
                "--rpc-http-tls-protocols",
                "--rpc-http-tls-cipher-suite",
                "--rpc-http-tls-cipher-suites"
            )
        )
    }

    private fun validateTls(commandLine: CommandLine) {
        if (rpcHttpTlsKeyStoreFile == null) {
            throw CommandLine.ParameterException(
                commandLine, "Keystore file is required when TLS is enabled for JSON-RPC HTTP endpoint"
            )
        }

        if (rpcHttpTlsKeyStorePasswordFile == null) {
            throw CommandLine.ParameterException(
                commandLine,
                "File containing password to unlock keystore is required when TLS is enabled for JSON-RPC HTTP endpoint"
            )
        }

        if (isRpcHttpTlsClientAuthEnabled) {
            if (!isRpcHttpTlsCAClientsEnabled && rpcHttpTlsKnownClientsFile == null && rpcHttpTlsTruststoreFile == null) {
                throw CommandLine.ParameterException(
                    commandLine,
                    ("Configuration error: TLS client authentication is enabled, but none of the following options are provided: "
                            + "1. Specify a known-clients file (--rpc-http-tls-known-clients-file) and/or  Enable CA clients (--rpc-http-tls-ca-clients-enabled). "
                            + "2. Specify a truststore file and its password file (--rpc-http-tls-truststore-file and --rpc-http-tls-truststore-password-file). "
                            + "Only one of these options must be configured")
                )
            }

            if (rpcHttpTlsTruststoreFile != null && rpcHttpTlsTruststorePasswordFile == null) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "Configuration error: A truststore file is specified for JSON RPC HTTP endpoint, but the corresponding truststore password file (--rpc-http-tls-truststore-password-file) is missing"
                )
            }

            if ((isRpcHttpTlsCAClientsEnabled || rpcHttpTlsKnownClientsFile != null)
                && rpcHttpTlsTruststoreFile != null
            ) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "Configuration error: Truststore file (--rpc-http-tls-truststore-file) cannot be used together with CA clients (--rpc-http-tls-ca-clients-enabled) or a known-clients (--rpc-http-tls-known-clients-file) option. "
                            + "These options are mutually exclusive. Choose either truststore-based authentication or known-clients/CA clients configuration."
                )
            }
        }

        rpcHttpTlsProtocols.retainAll(jDKEnabledProtocols)
        if (rpcHttpTlsProtocols.isEmpty()) {
            throw CommandLine.ParameterException(
                commandLine,
                ("No valid TLS protocols specified (the following protocols are enabled: "
                        + jDKEnabledProtocols
                        + ")")
            )
        }

        for (cipherSuite in rpcHttpTlsCipherSuites) {
            if (!jDKEnabledCipherSuites.contains(cipherSuite)) {
                throw CommandLine.ParameterException(
                    commandLine, "Invalid TLS cipher suite specified $cipherSuite"
                )
            }
        }
    }

    private fun rpcHttpTlsConfiguration(): Optional<TlsConfiguration> {
        if (!isRpcTlsConfigurationRequired) {
            return Optional.empty()
        }

        rpcHttpTlsCipherSuites.retainAll(jDKEnabledCipherSuites)

        return Optional.of(
            TlsConfiguration.Builder.aTlsConfiguration()
                .withKeyStorePath(rpcHttpTlsKeyStoreFile)
                .withKeyStorePasswordSupplier(
                    FileBasedPasswordProvider(rpcHttpTlsKeyStorePasswordFile)
                )
                .withClientAuthConfiguration(rpcHttpTlsClientAuthConfiguration())
                .withSecureTransportProtocols(rpcHttpTlsProtocols)
                .withCipherSuites(rpcHttpTlsCipherSuites)
                .build()
        )
    }

    private val isRpcTlsConfigurationRequired: Boolean
        get() = isRpcHttpEnabled && isRpcHttpTlsEnabled

    private fun rpcHttpTlsClientAuthConfiguration(): TlsClientAuthConfiguration? {
        if (isRpcHttpTlsClientAuthEnabled) {
            val tlsClientAuthConfigurationBuilder =
                TlsClientAuthConfiguration.Builder.aTlsClientAuthConfiguration()
                    .withKnownClientsFile(rpcHttpTlsKnownClientsFile)
                    .withCaClientsEnabled(isRpcHttpTlsCAClientsEnabled)
                    .withTruststorePath(rpcHttpTlsTruststoreFile)

            if (rpcHttpTlsTruststorePasswordFile != null) {
                tlsClientAuthConfigurationBuilder.withTruststorePasswordSupplier(
                    FileBasedPasswordProvider(rpcHttpTlsTruststorePasswordFile)
                )
            }
            return tlsClientAuthConfigurationBuilder.build()
        }

        return null
    }

    private fun rpcHttpAuthenticationCredentialsFile(commandLine: CommandLine): String? {
        val filename = rpcHttpAuthenticationCredentialsFile

        if (filename != null) {
            validate(commandLine, filename, "HTTP")
        }
        return filename
    }

    companion object {
        private val jDKEnabledCipherSuites: List<String>
            get() {
                try {
                    val context = SSLContext.getInstance("TLS")
                    context.init(null, null, null)
                    val engine = context.createSSLEngine()
                    return Arrays.asList(*engine.enabledCipherSuites)
                } catch (e: KeyManagementException) {
                    throw RuntimeException(e)
                } catch (e: NoSuchAlgorithmException) {
                    throw RuntimeException(e)
                }
            }

        private val jDKEnabledProtocols: List<String>
            get() {
                try {
                    val context = SSLContext.getInstance("TLS")
                    context.init(null, null, null)
                    val engine = context.createSSLEngine()
                    return Arrays.asList(*engine.enabledProtocols)
                } catch (e: KeyManagementException) {
                    throw RuntimeException(e)
                } catch (e: NoSuchAlgorithmException) {
                    throw RuntimeException(e)
                }
            }
    }
}
