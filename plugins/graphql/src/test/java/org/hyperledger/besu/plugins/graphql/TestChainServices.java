/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.pluginadapter.BlockchainQueryServiceImpl;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.chain.pluginadapter.BlockchainServiceImpl;
import org.hyperledger.besu.ethereum.chain.pluginadapter.RlpConverterServiceImpl;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.DefaultSyncStatus;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PropagatedBlockSource;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.pluginadapter.TransactionPoolServiceImpl;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.pluginadapter.TransactionSimulationServiceImpl;
import org.hyperledger.besu.ethereum.worldstate.pluginadapter.WorldStateServiceImpl;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.WorldStateService;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.mockito.ArgumentMatchers;

/**
 * The real plugin service implementations of Besu over the in-memory hive test chain, with the same
 * transaction pool mock and sync status the GraphQL spec tests have always used.
 */
public final class TestChainServices {

  private final DataStorageFormat dataStorageFormat;
  private final BlockchainSetupUtil blockchainSetupUtil;
  private final TransactionSimulator transactionSimulator;
  private final BlockchainServiceImpl blockchainService;
  private final BlockchainQueryServiceImpl blockchainQueryService;
  private final WorldStateServiceImpl worldStateService;
  private final TransactionSimulationServiceImpl transactionSimulationService;
  private final TransactionPoolServiceImpl transactionPoolService;
  private final RlpConverterServiceImpl rlpConverterService;
  private final SynchronizationService synchronizationService;

  private TestChainServices(final DataStorageFormat dataStorageFormat) {
    this.dataStorageFormat = dataStorageFormat;
    blockchainSetupUtil = BlockchainSetupUtil.forHiveTesting(dataStorageFormat);
    blockchainSetupUtil.importAllBlocks();

    final MutableBlockchain blockchain = blockchainSetupUtil.getBlockchain();
    final ProtocolSchedule protocolSchedule = blockchainSetupUtil.getProtocolSchedule();

    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            protocolSchedule,
            blockchain,
            blockchainSetupUtil.getWorldArchive(),
            Optional.empty(),
            Optional.empty(),
            ImmutableApiConfiguration.builder().build(),
            MiningConfiguration.newDefault().setMinTransactionGasPrice(Wei.ZERO));

    transactionSimulator =
        new TransactionSimulator(
            blockchain,
            blockchainSetupUtil.getWorldArchive(),
            protocolSchedule,
            ImmutableMiningConfiguration.newDefault(),
            0L);

    blockchainService = new BlockchainServiceImpl();
    blockchainService.init(
        blockchain, protocolSchedule, mock(PropagatedBlockSource.class), new BadBlockManager());
    blockchainQueryService = new BlockchainQueryServiceImpl(blockchainQueries);
    worldStateService =
        new WorldStateServiceImpl(blockchainSetupUtil.getWorldArchive(), blockchain);
    transactionSimulationService = new TransactionSimulationServiceImpl();
    transactionSimulationService.init(blockchain, transactionSimulator);
    transactionPoolService = new TransactionPoolServiceImpl(transactionPoolMock());
    rlpConverterService = new RlpConverterServiceImpl(protocolSchedule);

    synchronizationService = mock(SynchronizationService.class);
    final SyncStatus status = new DefaultSyncStatus(1, 2, 3, Optional.of(4L), Optional.of(5L));
    when(synchronizationService.getSyncStatus()).thenReturn(Optional.of(status));
  }

  /**
   * Imports the hive test chain into a new in-memory database of the given format.
   *
   * @param dataStorageFormat the storage format of the world state
   * @return the services over the chain
   */
  public static TestChainServices forHiveTesting(final DataStorageFormat dataStorageFormat) {
    return new TestChainServices(dataStorageFormat);
  }

  private static TransactionPool transactionPoolMock() {
    final TransactionPool transactionPoolMock = mock(TransactionPool.class);
    when(transactionPoolMock.addTransactionViaApi(ArgumentMatchers.any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    // nonce too low tests uses a tx with nonce=16
    when(transactionPoolMock.addTransactionViaApi(
            ArgumentMatchers.argThat(tx -> tx.getNonce() == 16)))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
    when(transactionPoolMock.getPendingTransactions())
        .thenReturn(
            Collections.singleton(
                new PendingTransaction.Local(
                    Transaction.builder()
                        .type(TransactionType.FRONTIER)
                        .nonce(42)
                        .gasLimit(654321)
                        .gasPrice(Wei.ONE)
                        .build())));
    return transactionPoolMock;
  }

  /**
   * Returns the storage format of the world state.
   *
   * @return the storage format
   */
  public DataStorageFormat dataStorageFormat() {
    return dataStorageFormat;
  }

  /**
   * Returns the chain the services read from.
   *
   * @return the chain setup
   */
  public BlockchainSetupUtil blockchainSetupUtil() {
    return blockchainSetupUtil;
  }

  /**
   * Returns Besu's transaction simulator the simulation service delegates to.
   *
   * @return the transaction simulator
   */
  public TransactionSimulator transactionSimulator() {
    return transactionSimulator;
  }

  /**
   * Returns the GraphQL context the plugin builds from these services.
   *
   * @return the GraphQL context
   */
  public Map<GraphQLContextType, Object> graphQLContext() {
    return Map.of(
        GraphQLContextType.BLOCKCHAIN_SERVICE,
        blockchainService,
        GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE,
        blockchainQueryService,
        GraphQLContextType.WORLD_STATE_SERVICE,
        worldStateService,
        GraphQLContextType.TRANSACTION_SIMULATION_SERVICE,
        transactionSimulationService,
        GraphQLContextType.TRANSACTION_POOL_SERVICE,
        transactionPoolService,
        GraphQLContextType.RLP_CONVERTER_SERVICE,
        rlpConverterService,
        GraphQLContextType.SYNCHRONIZATION_SERVICE,
        synchronizationService,
        GraphQLContextType.BONSAI_WORLD_STATE,
        dataStorageFormat.isBonsaiFormat());
  }

  /**
   * Adds the services the plugin reads its data from to a service manager, as Besu does before it
   * starts the plugins.
   *
   * @param serviceManager the service manager
   */
  public void addTo(final ServiceManager.SimpleServiceManager serviceManager) {
    serviceManager.addService(BlockchainService.class, blockchainService);
    serviceManager.addService(BlockchainQueryService.class, blockchainQueryService);
    serviceManager.addService(WorldStateService.class, worldStateService);
    serviceManager.addService(TransactionSimulationService.class, transactionSimulationService);
    serviceManager.addService(TransactionPoolService.class, transactionPoolService);
    serviceManager.addService(RlpConverterService.class, rlpConverterService);
    serviceManager.addService(SynchronizationService.class, synchronizationService);
  }
}
