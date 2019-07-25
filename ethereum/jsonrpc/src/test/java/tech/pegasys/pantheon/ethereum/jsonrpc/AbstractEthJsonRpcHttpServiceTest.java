/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.StubGenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.health.HealthService;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterRepository;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.util.RawBlockIterator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.testutil.BlockTestUtil;
import tech.pegasys.pantheon.testutil.TestClock;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractEthJsonRpcHttpServiceTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected static ProtocolSchedule<Void> PROTOCOL_SCHEDULE;

  protected static List<Block> BLOCKS;

  protected static Block GENESIS_BLOCK;

  protected static GenesisState GENESIS_CONFIG;

  protected final Vertx vertx = Vertx.vertx();

  protected JsonRpcHttpService service;

  protected OkHttpClient client;

  protected String baseUrl;

  protected final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  protected final String CLIENT_VERSION = "TestClientVersion/0.1.0";

  protected final int NETWORK_ID = 123;

  protected static final Collection<RpcApi> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);

  protected MutableBlockchain blockchain;

  protected WorldStateArchive stateArchive;

  protected FilterManager filterManager;

  protected ProtocolContext<Void> context;

  @BeforeClass
  public static void setupConstants() throws Exception {
    PROTOCOL_SCHEDULE = MainnetProtocolSchedule.create(TestClock.fixed());

    final URL blocksUrl = BlockTestUtil.getTestBlockchainUrl();

    final URL genesisJsonUrl = BlockTestUtil.getTestGenesisUrl();

    BLOCKS = new ArrayList<>();
    try (final RawBlockIterator iterator =
        new RawBlockIterator(
            Paths.get(blocksUrl.toURI()),
            rlp -> BlockHeader.readFrom(rlp, new MainnetBlockHeaderFunctions()))) {
      while (iterator.hasNext()) {
        BLOCKS.add(iterator.next());
      }
    }

    final String genesisJson = Resources.toString(genesisJsonUrl, Charsets.UTF_8);

    GENESIS_BLOCK = BLOCKS.get(0);
    GENESIS_CONFIG = GenesisState.fromJson(genesisJson, PROTOCOL_SCHEDULE);
  }

  @Before
  public void setupTest() throws Exception {
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
    stateArchive = createInMemoryWorldStateArchive();
    GENESIS_CONFIG.writeStateTo(stateArchive.getMutable());

    blockchain = createInMemoryBlockchain(GENESIS_BLOCK);
    context = new ProtocolContext<>(blockchain, stateArchive, null);

    final BlockchainQueries blockchainQueries = new BlockchainQueries(blockchain, stateArchive);
    final FilterIdGenerator filterIdGenerator = mock(FilterIdGenerator.class);
    final FilterRepository filterRepository = new FilterRepository();
    when(filterIdGenerator.nextId()).thenReturn("0x1");
    filterManager =
        new FilterManager(
            blockchainQueries, transactionPoolMock, filterIdGenerator, filterRepository);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    final Map<String, JsonRpcMethod> methods =
        new JsonRpcMethodsFactory()
            .methods(
                CLIENT_VERSION,
                NETWORK_ID,
                new StubGenesisConfigOptions(),
                peerDiscoveryMock,
                blockchainQueries,
                synchronizerMock,
                MainnetProtocolSchedule.create(TestClock.fixed()),
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

  protected void importBlock(final int n) {
    final Block block = BLOCKS.get(n);
    final ProtocolSpec<Void> protocolSpec =
        PROTOCOL_SCHEDULE.getByBlockNumber(block.getHeader().getNumber());
    final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
    blockImporter.importBlock(context, block, HeaderValidationMode.FULL);
  }
}
