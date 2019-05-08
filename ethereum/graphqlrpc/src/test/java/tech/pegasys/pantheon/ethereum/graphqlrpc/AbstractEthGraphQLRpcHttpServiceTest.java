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
package tech.pegasys.pantheon.ethereum.graphqlrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.util.RawBlockIterator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractEthGraphQLRpcHttpServiceTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static ProtocolSchedule<Void> PROTOCOL_SCHEDULE;

  static List<Block> BLOCKS;

  private static Block GENESIS_BLOCK;

  private static GenesisState GENESIS_CONFIG;

  private final Vertx vertx = Vertx.vertx();

  private GraphQLRpcHttpService service;

  OkHttpClient client;

  String baseUrl;

  final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private MutableBlockchain blockchain;

  private WorldStateArchive stateArchive;

  private ProtocolContext<Void> context;

  @BeforeClass
  public static void setupConstants() throws Exception {
    PROTOCOL_SCHEDULE = MainnetProtocolSchedule.create();

    final URL blocksUrl =
        EthGraphQLRpcHttpBySpecTest.class
            .getClassLoader()
            .getResource(
                "tech/pegasys/pantheon/ethereum/graphqlrpc/graphQLRpcTestBlockchain.blocks");

    final URL genesisJsonUrl =
        EthGraphQLRpcHttpBySpecTest.class
            .getClassLoader()
            .getResource("tech/pegasys/pantheon/ethereum/graphqlrpc/graphQLRpcTestGenesis.json");

    assertThat(blocksUrl).isNotNull();
    assertThat(genesisJsonUrl).isNotNull();

    BLOCKS = new ArrayList<>();
    try (final RawBlockIterator iterator =
        new RawBlockIterator(
            Paths.get(blocksUrl.toURI()),
            rlp -> BlockHeader.readFrom(rlp, MainnetBlockHashFunction::createHash))) {
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
    final SyncStatus status = new SyncStatus(1, 2, 3);
    when(synchronizerMock.getSyncStatus()).thenReturn(Optional.of(status));

    final EthHashMiningCoordinator miningCoordinatorMock = mock(EthHashMiningCoordinator.class);
    when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(Wei.of(16));

    final TransactionPool transactionPoolMock = mock(TransactionPool.class);

    when(transactionPoolMock.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    final PendingTransactions pendingTransactionsMock = mock(PendingTransactions.class);
    when(transactionPoolMock.getPendingTransactions()).thenReturn(pendingTransactionsMock);

    stateArchive = createInMemoryWorldStateArchive();
    GENESIS_CONFIG.writeStateTo(stateArchive.getMutable());

    blockchain = createInMemoryBlockchain(GENESIS_BLOCK);
    context = new ProtocolContext<>(blockchain, stateArchive, null);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final GraphQLRpcConfiguration config = GraphQLRpcConfiguration.createDefault();

    config.setPort(0);
    final GraphQLDataFetcherContext dataFetcherContext =
        new GraphQLDataFetcherContext(
            blockchain,
            stateArchive,
            PROTOCOL_SCHEDULE,
            transactionPoolMock,
            miningCoordinatorMock,
            synchronizerMock);

    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    service =
        new GraphQLRpcHttpService(
            vertx, folder.newFolder().toPath(), config, graphQL, dataFetcherContext);
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url() + "/graphql/";
  }

  @After
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }

  void importBlock(final int n) {
    final Block block = BLOCKS.get(n);
    final ProtocolSpec<Void> protocolSpec =
        PROTOCOL_SCHEDULE.getByBlockNumber(block.getHeader().getNumber());
    final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
    blockImporter.importBlock(context, block, HeaderValidationMode.FULL);
  }
}
