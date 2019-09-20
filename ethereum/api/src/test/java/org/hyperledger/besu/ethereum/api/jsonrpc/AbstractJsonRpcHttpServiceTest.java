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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static com.google.common.base.Preconditions.checkArgument;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterIdGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterRepository;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.EthHashMiningCoordinator;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.testutil.BlockTestUtil.ChainResources;

import java.math.BigInteger;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractJsonRpcHttpServiceTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected BlockchainSetupUtil<Void> blockchainSetupUtil;

  protected static String CLIENT_VERSION = "TestClientVersion/0.1.0";
  protected static final BigInteger NETWORK_ID = BigInteger.valueOf(123);
  protected static final Collection<RpcApi> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3, RpcApis.DEBUG);

  protected final Vertx vertx = Vertx.vertx();
  protected JsonRpcHttpService service;
  protected OkHttpClient client;
  protected String baseUrl;
  protected final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected FilterManager filterManager;

  private void setupBlockchain() {
    blockchainSetupUtil = getBlockchainSetupUtil();
    blockchainSetupUtil.importAllBlocks();
  }

  protected BlockchainSetupUtil<Void> getBlockchainSetupUtil() {
    return BlockchainSetupUtil.forTesting();
  }

  protected BlockchainSetupUtil<Void> createBlockchainSetupUtil(
      final String genesisPath, final String blocksPath) {
    final URL genesisURL = AbstractJsonRpcHttpServiceTest.class.getResource(genesisPath);
    final URL blocksURL = AbstractJsonRpcHttpServiceTest.class.getResource(blocksPath);
    checkArgument(genesisURL != null, "Unable to locate genesis file: " + genesisPath);
    checkArgument(blocksURL != null, "Unable to locate blocks file: " + blocksPath);
    return BlockchainSetupUtil.createForEthashChain(new ChainResources(genesisURL, blocksURL));
  }

  @Before
  public void setup() throws Exception {
    setupBlockchain();
  }

  protected BlockchainSetupUtil<Void> startServiceWithEmptyChain() throws Exception {
    final BlockchainSetupUtil<Void> emptySetupUtil = getBlockchainSetupUtil();
    startService(emptySetupUtil);
    return emptySetupUtil;
  }

  protected Map<String, JsonRpcMethod> getRpcMethods(
      final JsonRpcConfiguration config, final BlockchainSetupUtil<Void> blockchainSetupUtil) {
    final Synchronizer synchronizerMock = mock(Synchronizer.class);
    final P2PNetwork peerDiscoveryMock = mock(P2PNetwork.class);
    final TransactionPool transactionPoolMock = mock(TransactionPool.class);
    final EthHashMiningCoordinator miningCoordinatorMock = mock(EthHashMiningCoordinator.class);
    when(transactionPoolMock.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    // nonce too low tests uses a tx with nonce=16
    when(transactionPoolMock.addLocalTransaction(argThat(tx -> tx.getNonce() == 16)))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
    final PendingTransactions pendingTransactionsMock = mock(PendingTransactions.class);
    when(transactionPoolMock.getPendingTransactions()).thenReturn(pendingTransactionsMock);
    final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);

    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            blockchainSetupUtil.getBlockchain(), blockchainSetupUtil.getWorldArchive());
    final FilterIdGenerator filterIdGenerator = mock(FilterIdGenerator.class);
    final FilterRepository filterRepository = new FilterRepository();
    when(filterIdGenerator.nextId()).thenReturn("0x1");
    filterManager =
        new FilterManager(
            blockchainQueries, transactionPoolMock, filterIdGenerator, filterRepository);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    return new JsonRpcMethodsFactory()
        .methods(
            CLIENT_VERSION,
            NETWORK_ID,
            new StubGenesisConfigOptions(),
            peerDiscoveryMock,
            blockchainQueries,
            synchronizerMock,
            MainnetProtocolSchedule.create(),
            filterManager,
            transactionPoolMock,
            miningCoordinatorMock,
            new NoOpMetricsSystem(),
            supportedCapabilities,
            Optional.empty(),
            Optional.empty(),
            JSON_RPC_APIS,
            privacyParameters,
            config,
            mock(WebSocketConfiguration.class),
            mock(MetricsConfiguration.class));
  }

  protected void startService() throws Exception {
    startService(blockchainSetupUtil);
  }

  private void startService(final BlockchainSetupUtil<Void> blockchainSetupUtil) throws Exception {

    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    final Map<String, JsonRpcMethod> methods = getRpcMethods(config, blockchainSetupUtil);

    config.setPort(0);
    service =
        new JsonRpcHttpService(
            vertx,
            folder.newFolder().toPath(),
            config,
            new NoOpMetricsSystem(),
            Optional.empty(),
            methods,
            HealthService.ALWAYS_HEALTHY,
            HealthService.ALWAYS_HEALTHY);
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url();
  }

  @After
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }
}
