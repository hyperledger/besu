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

import static org.mockito.Mockito.mock;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterRepository;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.permissioning.AccountWhitelistController;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.HashSet;
import java.util.Map;

/** Provides a facade to construct the JSON-RPC component. */
public class JsonRpcTestMethodsFactory {

  private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";

  private final BlockchainImporter importer;

  public JsonRpcTestMethodsFactory(final BlockchainImporter importer) {
    this.importer = importer;
  }

  public Map<String, JsonRpcMethod> methods() {
    final WorldStateArchive stateArchive = createInMemoryWorldStateArchive();

    importer.getGenesisState().writeStateTo(stateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final MutableBlockchain blockchain = createInMemoryBlockchain(importer.getGenesisBlock());
    final ProtocolContext<Void> context = new ProtocolContext<>(blockchain, stateArchive, null);

    for (final Block block : importer.getBlocks()) {
      final ProtocolSchedule<Void> protocolSchedule = importer.getProtocolSchedule();
      final ProtocolSpec<Void> protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
      blockImporter.importBlock(context, block, HeaderValidationMode.FULL);
    }

    final BlockchainQueries blockchainQueries = new BlockchainQueries(blockchain, stateArchive);

    final Synchronizer synchronizer = mock(Synchronizer.class);
    final P2PNetwork peerDiscovery = mock(P2PNetwork.class);
    final TransactionPool transactionPool = mock(TransactionPool.class);
    final FilterManager filterManager =
        new FilterManager(
            blockchainQueries, transactionPool, new FilterIdGenerator(), new FilterRepository());
    final EthHashMiningCoordinator miningCoordinator = mock(EthHashMiningCoordinator.class);
    final MetricsSystem metricsSystem = new NoOpMetricsSystem();
    final AccountWhitelistController accountWhitelistController =
        mock(AccountWhitelistController.class);
    final PrivateTransactionHandler privateTransactionHandler =
        mock(PrivateTransactionHandler.class);

    return new JsonRpcMethodsFactory()
        .methods(
            CLIENT_VERSION,
            peerDiscovery,
            blockchainQueries,
            synchronizer,
            MainnetProtocolSchedule.create(),
            filterManager,
            transactionPool,
            miningCoordinator,
            metricsSystem,
            new HashSet<>(),
            accountWhitelistController,
            RpcApis.DEFAULT_JSON_RPC_APIS,
            privateTransactionHandler);
  }
}
