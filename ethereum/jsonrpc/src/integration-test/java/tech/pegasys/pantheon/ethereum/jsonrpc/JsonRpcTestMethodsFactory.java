package tech.pegasys.pantheon.ethereum.jsonrpc;

import static org.mockito.Mockito.mock;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.db.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterRepository;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;

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
        new FilterManager(
            blockchainQueries, transactionPool, new FilterIdGenerator(), new FilterRepository());
    final EthHashMiningCoordinator miningCoordinator = mock(EthHashMiningCoordinator.class);

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
