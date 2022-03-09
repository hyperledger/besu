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
package org.hyperledger.besu.ethereum.api.graphql;

import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.DefaultSyncStatus;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.util.RawBlockIterator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

  private static ProtocolSchedule PROTOCOL_SCHEDULE;

  static List<Block> BLOCKS;

  private static Block GENESIS_BLOCK;

  private static GenesisState GENESIS_CONFIG;

  private final Vertx vertx = Vertx.vertx();

  private GraphQLHttpService service;

  OkHttpClient client;

  String baseUrl;

  final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");

  private ProtocolContext context;

  @BeforeClass
  public static void setupConstants() throws Exception {
    PROTOCOL_SCHEDULE = ProtocolScheduleFixture.MAINNET;

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
    final SyncStatus status = new DefaultSyncStatus(1, 2, 3, Optional.of(4L), Optional.of(5L));
    Mockito.when(synchronizerMock.getSyncStatus()).thenReturn(Optional.of(status));

    final PoWMiningCoordinator miningCoordinatorMock = Mockito.mock(PoWMiningCoordinator.class);
    Mockito.when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(Wei.of(16));

    final TransactionPool transactionPoolMock = Mockito.mock(TransactionPool.class);

    Mockito.when(transactionPoolMock.addLocalTransaction(ArgumentMatchers.any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    // nonce too low tests uses a tx with nonce=16
    Mockito.when(
            transactionPoolMock.addLocalTransaction(
                ArgumentMatchers.argThat(tx -> tx.getNonce() == 16)))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
    final GasPricePendingTransactionsSorter pendingTransactionsMock =
        Mockito.mock(GasPricePendingTransactionsSorter.class);
    Mockito.when(transactionPoolMock.getPendingTransactions()).thenReturn(pendingTransactionsMock);
    Mockito.when(pendingTransactionsMock.getTransactionInfo())
        .thenReturn(
            Collections.singleton(
                new AbstractPendingTransactionsSorter.TransactionInfo(
                    Transaction.builder()
                        .type(TransactionType.FRONTIER)
                        .nonce(42)
                        .gasLimit(654321)
                        .build(),
                    true,
                    Instant.ofEpochSecond(Integer.MAX_VALUE))));

    final WorldStateArchive stateArchive = createInMemoryWorldStateArchive();
    GENESIS_CONFIG.writeStateTo(stateArchive.getMutable());

    final MutableBlockchain blockchain =
        InMemoryKeyValueStorageProvider.createInMemoryBlockchain(GENESIS_BLOCK);
    context = new ProtocolContext(blockchain, stateArchive, null);
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            context.getBlockchain(),
            context.getWorldStateArchive(),
            Optional.empty(),
            Optional.empty(),
            ImmutableApiConfiguration.builder().gasPriceMin(0).build());

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();

    config.setPort(0);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    service =
        new GraphQLHttpService(
            vertx,
            folder.newFolder().toPath(),
            config,
            graphQL,
            Map.of(
                GraphQLContextType.BLOCKCHAIN_QUERIES,
                blockchainQueries,
                GraphQLContextType.PROTOCOL_SCHEDULE,
                PROTOCOL_SCHEDULE,
                GraphQLContextType.TRANSACTION_POOL,
                transactionPoolMock,
                GraphQLContextType.MINING_COORDINATOR,
                miningCoordinatorMock,
                GraphQLContextType.SYNCHRONIZER,
                synchronizerMock),
            Mockito.mock(EthScheduler.class));
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
    final ProtocolSpec protocolSpec =
        PROTOCOL_SCHEDULE.getByBlockNumber(block.getHeader().getNumber());
    final BlockImporter blockImporter = protocolSpec.getBlockImporter();
    blockImporter.importBlock(context, block, HeaderValidationMode.FULL);
  }
}
