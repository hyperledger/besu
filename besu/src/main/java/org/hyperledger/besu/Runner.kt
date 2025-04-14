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
package org.hyperledger.besu

import com.google.common.annotations.VisibleForTesting
import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.api.graphql.GraphQLHttpService
import org.hyperledger.besu.ethereum.api.jsonrpc.EngineJsonRpcService
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcHttpService
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcService
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketService
import org.hyperledger.besu.ethereum.api.query.cache.AutoTransactionLogBloomCachingService
import org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolEvictionService
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork
import org.hyperledger.besu.ethereum.stratum.StratumServer
import org.hyperledger.besu.ethstats.EthStatsService
import org.hyperledger.besu.metrics.MetricsService
import org.hyperledger.besu.nat.NatService
import org.hyperledger.besu.plugin.data.EnodeURL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.*

/** The Runner controls various Besu services lifecycle.  */
class Runner internal constructor(
    private val vertx: Vertx,
    private val networkRunner: NetworkRunner,
    private val natService: NatService,
    private val jsonRpc: Optional<JsonRpcHttpService>,
    private val engineJsonRpc: Optional<EngineJsonRpcService>,
    private val graphQLHttp: Optional<GraphQLHttpService>,
    private val webSocketRpc: Optional<WebSocketService>,
    private val ipcJsonRpc: Optional<JsonRpcIpcService>,
    /**
     * Get the RPC methods that can be called in-process
     *
     * @return RPC methods by name
     */
  @JvmField val inProcessRpcMethods: Map<String, JsonRpcMethod>?,
    private val stratumServer: Optional<StratumServer>,
    private val metrics: Optional<MetricsService>,
    private val ethStatsService: Optional<EthStatsService>,
    private val besuController: BesuController,
    private val dataDir: Path,
    private val pidPath: Optional<Path>,
    transactionLogBloomCacher: Optional<TransactionLogBloomCacher>,
    blockchain: Blockchain?
) : AutoCloseable {
    private val vertxShutdownLatch = CountDownLatch(1)
    private val shutdown = CountDownLatch(1)

    private val transactionPoolEvictionService =
        TransactionPoolEvictionService(vertx, besuController.transactionPool)

    private val autoTransactionLogBloomCachingService: Optional<AutoTransactionLogBloomCachingService> =
        transactionLogBloomCacher.map { cacher: TransactionLogBloomCacher? ->
            AutoTransactionLogBloomCachingService(
                blockchain,
                cacher
            )
        }

    /** Start external services.  */
    fun startExternalServices() {
        LOG.info("Starting external services ... ")
        metrics.ifPresent { service: MetricsService -> waitForServiceToStart("metrics", service.start()) }

        jsonRpc.ifPresent { service: JsonRpcHttpService -> waitForServiceToStart("jsonRpc", service.start()) }
        engineJsonRpc.ifPresent { service: EngineJsonRpcService ->
            waitForServiceToStart(
                "engineJsonRpc",
                service.start()
            )
        }
        graphQLHttp.ifPresent { service: GraphQLHttpService -> waitForServiceToStart("graphQLHttp", service.start()) }
        webSocketRpc.ifPresent { service: WebSocketService ->
            waitForServiceToStart(
                "websocketRpc",
                service.start()
            )
        }
        ipcJsonRpc.ifPresent { service: JsonRpcIpcService ->
            waitForServiceToStart(
                "ipcJsonRpc", service.start().toCompletionStage().toCompletableFuture()
            )
        }
        stratumServer.ifPresent { server: StratumServer ->
            waitForServiceToStart(
                "stratum", server.start().toCompletionStage().toCompletableFuture()
            )
        }
        autoTransactionLogBloomCachingService.ifPresent { obj: AutoTransactionLogBloomCachingService -> obj.start() }
    }

    private fun startExternalServicePostMainLoop() {
        ethStatsService.ifPresent { obj: EthStatsService -> obj.start() }
    }

    /** Start ethereum main loop.  */
    fun startEthereumMainLoop() {
        try {
            LOG.info("Starting Ethereum main loop ... ")
            natService.start()
            networkRunner.start()
            besuController.miningCoordinator.subscribe()
            if (networkRunner.network.isP2pEnabled) {
                besuController.synchronizer.start()
            }
            besuController.miningCoordinator.start()
            transactionPoolEvictionService.start()

            LOG.info("Ethereum main loop is up.")
            // we write these values to disk to be able to access them during the acceptance tests
            writeBesuPortsToFile()
            writeBesuNetworksToFile()
            writePidFile()

            // start external service that depends on information from main loop
            startExternalServicePostMainLoop()
        } catch (ex: Exception) {
            LOG.error("unable to start main loop", ex)
            throw IllegalStateException("Startup failed", ex)
        }
    }

    /** Stop services.  */
    fun stop() {
        transactionPoolEvictionService.stop()
        jsonRpc.ifPresent { service: JsonRpcHttpService -> waitForServiceToStop("jsonRpc", service.stop()) }
        engineJsonRpc.ifPresent { service: EngineJsonRpcService ->
            waitForServiceToStop(
                "engineJsonRpc",
                service.stop()
            )
        }
        graphQLHttp.ifPresent { service: GraphQLHttpService -> waitForServiceToStop("graphQLHttp", service.stop()) }
        webSocketRpc.ifPresent { service: WebSocketService ->
            waitForServiceToStop(
                "websocketRpc",
                service.stop()
            )
        }
        ipcJsonRpc.ifPresent { service: JsonRpcIpcService ->
            waitForServiceToStop(
                "ipcJsonRpc", service.stop().toCompletionStage().toCompletableFuture()
            )
        }
        waitForServiceToStop("Transaction Pool", besuController.transactionPool.setDisabled())
        metrics.ifPresent { service: MetricsService -> waitForServiceToStop("metrics", service.stop()) }
        ethStatsService.ifPresent { obj: EthStatsService -> obj.stop() }
        besuController.miningCoordinator.stop()
        waitForServiceToStop("Mining Coordinator") { besuController.miningCoordinator.awaitStop() }
        stratumServer.ifPresent { server: StratumServer ->
            waitForServiceToStop(
                "Stratum"
            ) { server.stop() }
        }
        if (networkRunner.network.isP2pEnabled) {
            besuController.synchronizer.stop()
            waitForServiceToStop("Synchronizer") { besuController.synchronizer.awaitStop() }
        }

        networkRunner.stop()
        waitForServiceToStop("Network") { networkRunner.awaitStop() }
        autoTransactionLogBloomCachingService.ifPresent { obj: AutoTransactionLogBloomCachingService -> obj.stop() }
        natService.stop()
        besuController.close()
        vertx.close { res: AsyncResult<Void?>? -> vertxShutdownLatch.countDown() }
        waitForServiceToStop("Vertx") { vertxShutdownLatch.await() }
        shutdown.countDown()
    }

    /** Await stop.  */
    fun awaitStop() {
        try {
            shutdown.await()
        } catch (e: InterruptedException) {
            LOG.debug("Interrupted, exiting", e)
            Thread.currentThread().interrupt()
        }
    }

    override fun close() {
        stop()
        awaitStop()
    }

    private fun waitForServiceToStop(
        serviceName: String, stopFuture: CompletableFuture<*>
    ) {
        try {
            stopFuture[30, TimeUnit.SECONDS]
        } catch (e: InterruptedException) {
            LOG.debug("Interrupted while waiting for service to complete", e)
            Thread.currentThread().interrupt()
        } catch (e: ExecutionException) {
            LOG.error("Service $serviceName failed to shutdown", e)
        } catch (e: TimeoutException) {
            LOG.error("Service {} did not shut down cleanly", serviceName)
        }
    }

    private fun waitForServiceToStop(serviceName: String, shutdown: SynchronousShutdown) {
        try {
            shutdown.await()
        } catch (e: InterruptedException) {
            LOG.debug("Interrupted while waiting for service {} to stop {}", serviceName, e)
            Thread.currentThread().interrupt()
        }
    }

    private fun waitForServiceToStart(
        serviceName: String, startFuture: CompletableFuture<*>
    ) {
        do {
            try {
                startFuture[60, TimeUnit.SECONDS]
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Interrupted while waiting for service to start", e)
            } catch (e: ExecutionException) {
                throw IllegalStateException("Service $serviceName failed to start", e)
            } catch (e: TimeoutException) {
                LOG.warn("Service {} is taking an unusually long time to start", serviceName)
            }
        } while (!startFuture.isDone)
    }

    private fun writeBesuPortsToFile() {
        val properties = Properties()
        if (networkRunner.network.isP2pEnabled) {
            networkRunner
                .network
                .localEnode
                .ifPresent { enode: EnodeURL ->
                    enode
                        .discoveryPort
                        .ifPresent { discoveryPort: Int ->
                            properties.setProperty(
                                "discovery",
                                discoveryPort.toString()
                            )
                        }
                    enode
                        .listeningPort
                        .ifPresent { listeningPort: Int -> properties.setProperty("p2p", listeningPort.toString()) }
                }
        }

        var port = jsonRpcPort
        if (port.isPresent) {
            properties.setProperty("json-rpc", port.get().toString())
        }
        port = graphQLHttpPort
        if (port.isPresent) {
            properties.setProperty("graphql-http", port.get().toString())
        }
        port = webSocketPort
        if (port.isPresent) {
            properties.setProperty("ws-rpc", port.get().toString())
        }
        port = metricsPort
        if (port.isPresent) {
            properties.setProperty("metrics", port.get().toString())
        }
        port = engineJsonRpcPort
        if (port.isPresent) {
            properties.setProperty("engine-json-rpc", port.get().toString())
        }
        // create besu.ports file
        createBesuFile(
            properties, "ports", "This file contains the ports used by the running instance of Besu"
        )
    }

    private fun writeBesuNetworksToFile() {
        val properties = Properties()
        if (networkRunner.network.isP2pEnabled) {
            networkRunner
                .network
                .localEnode
                .ifPresent { enode: EnodeURL ->
                    val globalIp = natService.queryExternalIPAddress(enode.ipAsString)
                    properties.setProperty("global-ip", globalIp)
                    val localIp = natService.queryLocalIPAddress(enode.ipAsString)
                    properties.setProperty("local-ip", localIp)
                }
        }
        // create besu.networks file
        createBesuFile(
            properties,
            "networks",
            "This file contains the IP Addresses (global and local) used by the running instance of Besu"
        )
    }

    private fun writePidFile() {
        pidPath.ifPresent { path: Path ->
            var pid = ""
            try {
                pid = ProcessHandle.current().pid().toString()
            } catch (t: Throwable) {
                LOG.error("Error retrieving PID", t)
            }
            try {
                Files.write(
                    path,
                    pid.toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                path.toFile().deleteOnExit()
            } catch (e: IOException) {
                LOG.error("Error writing PID file", e)
            }
        }
    }

    val jsonRpcPort: Optional<Int>
        /**
         * Gets json rpc port.
         *
         * @return the json rpc port
         */
        get() = jsonRpc.map { service: JsonRpcHttpService -> service.socketAddress().port }

    val engineJsonRpcPort: Optional<Int>
        /**
         * Gets engine json rpc port.
         *
         * @return the engine json rpc port
         */
        get() = engineJsonRpc.map { service: EngineJsonRpcService -> service.socketAddress().port }

    val graphQLHttpPort: Optional<Int>
        /**
         * Gets GraphQl http port.
         *
         * @return the graph ql http port
         */
        get() = graphQLHttp.map { service: GraphQLHttpService -> service.socketAddress().port }

    val webSocketPort: Optional<Int>
        /**
         * Gets web socket port.
         *
         * @return the web socket port
         */
        get() = webSocketRpc.map { service: WebSocketService -> service.socketAddress().port }

    val metricsPort: Optional<Int>
        /**
         * Gets metrics port.
         *
         * @return the metrics port
         */
        get() {
            return if (metrics.isPresent) {
                metrics.get().port
            } else {
                Optional.empty()
            }
        }

    @get:VisibleForTesting
    val localEnode: Optional<EnodeURL>
        /**
         * Gets local enode.
         *
         * @return the local enode
         */
        get() = networkRunner.network.localEnode

    val p2PNetwork: P2PNetwork
        /**
         * get P2PNetwork service.
         *
         * @return p2p network service.
         */
        get() = networkRunner.network

    private fun interface SynchronousShutdown {
        /**
         * Await for shutdown.
         *
         * @throws InterruptedException the interrupted exception
         */
        @Throws(InterruptedException::class)
        fun await()
    }

    private fun createBesuFile(
        properties: Properties, fileName: String, fileHeader: String
    ) {
        val file = File(dataDir.toFile(), String.format("besu.%s", fileName))
        file.deleteOnExit()
        try {
            FileOutputStream(file).use { fileOutputStream ->
                properties.store(
                    fileOutputStream,
                    String.format("%s. This file will be deleted after the node is shutdown.", fileHeader)
                )
            }
        } catch (e: Exception) {
            LOG.warn(String.format("Error writing %s file", fileName), e)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Runner::class.java)
    }
}
