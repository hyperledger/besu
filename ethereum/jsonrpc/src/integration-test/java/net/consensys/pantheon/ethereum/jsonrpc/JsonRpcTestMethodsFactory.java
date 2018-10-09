package net.consensys.pantheon.ethereum.jsonrpc;

import static org.mockito.Mockito.mock;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.p2p.api.P2PNetwork;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;

import java.util.HashSet;
import java.util.Map;

/** Provides a facade to construct the JSON-RPC component. */
public class JsonRpcTestMethodsFactory {

  private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";

  private final BlockchainImporter importer;

  public JsonRpcTestMethodsFactory(final BlockchainImporter importer) {
    this.importer = importer;
  }

  public Map<String, JsonRpcMethod> methods(final String chainId) {
    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    final WorldStateArchive stateArchive =
        new WorldStateArchive(new KeyValueStorageWorldStateStorage(keyValueStorage));

    importer.getGenesisConfig().writeStateTo(stateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(
            importer.getGenesisBlock(), keyValueStorage, MainnetBlockHashFunction::createHash);
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
        new FilterManager(blockchainQueries, transactionPool, new FilterIdGenerator());
    final MiningCoordinator miningCoordinator = mock(MiningCoordinator.class);

    return new JsonRpcMethodsFactory()
        .methods(
            CLIENT_VERSION,
            chainId,
            peerDiscovery,
            blockchainQueries,
            synchronizer,
            MainnetProtocolSchedule.create(),
            filterManager,
            transactionPool,
            miningCoordinator,
            new HashSet<>(),
            JsonRpcConfiguration.DEFAULT_JSON_RPC_APIS);
  }
}
