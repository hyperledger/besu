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
package tech.pegasys.pantheon.ethereum.api.graphql;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.util.RawBlockIterator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.testutil.BlockTestUtil;

import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public abstract class AbstractEthGraphQLHttpServiceTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static ProtocolSchedule<Void> PROTOCOL_SCHEDULE;

  static List<Block> BLOCKS;

  private static Block GENESIS_BLOCK;

  private static GenesisState GENESIS_CONFIG;

  private final Vertx vertx = Vertx.vertx();

  private GraphQLHttpService service;

  OkHttpClient client;

  String baseUrl;

  final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");

  private MutableBlockchain blockchain;

  private WorldStateArchive stateArchive;

  private ProtocolContext<Void> context;

  @BeforeClass
  public static void setupConstants() throws Exception {
    PROTOCOL_SCHEDULE = MainnetProtocolSchedule.create();

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
    final Synchronizer synchronizerMock = Mockito.mock(Synchronizer.class);
    final SyncStatus status = new SyncStatus(1, 2, 3);
    Mockito.when(synchronizerMock.getSyncStatus()).thenReturn(Optional.of(status));

    final EthHashMiningCoordinator miningCoordinatorMock =
        Mockito.mock(EthHashMiningCoordinator.class);
    Mockito.when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(Wei.of(16));

    final TransactionPool transactionPoolMock = Mockito.mock(TransactionPool.class);

    Mockito.when(transactionPoolMock.addLocalTransaction(ArgumentMatchers.any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    // nonce too low tests uses a tx with nonce=16
    Mockito.when(
            transactionPoolMock.addLocalTransaction(
                ArgumentMatchers.argThat(tx -> tx.getNonce() == 16)))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
    final PendingTransactions pendingTransactionsMock = Mockito.mock(PendingTransactions.class);
    Mockito.when(transactionPoolMock.getPendingTransactions()).thenReturn(pendingTransactionsMock);
    Mockito.when(pendingTransactionsMock.getTransactionInfo())
        .thenReturn(
            Collections.singleton(
                new PendingTransactions.TransactionInfo(
                    Transaction.builder().nonce(42).gasLimit(654321).build(),
                    true,
                    Instant.ofEpochSecond(Integer.MAX_VALUE))));

    stateArchive = InMemoryStorageProvider.createInMemoryWorldStateArchive();
    GENESIS_CONFIG.writeStateTo(stateArchive.getMutable());

    blockchain = InMemoryStorageProvider.createInMemoryBlockchain(GENESIS_BLOCK);
    context = new ProtocolContext<>(blockchain, stateArchive, null);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();

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
        new GraphQLHttpService(
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
