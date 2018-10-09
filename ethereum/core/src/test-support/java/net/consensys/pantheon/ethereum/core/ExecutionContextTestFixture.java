package net.consensys.pantheon.ethereum.core;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;

public class ExecutionContextTestFixture {

  private final Block genesis = GenesisConfig.mainnet().getBlock();
  private final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
  private final MutableBlockchain blockchain =
      new DefaultMutableBlockchain(genesis, keyValueStorage, MainnetBlockHashFunction::createHash);
  private final WorldStateArchive stateArchive =
      new WorldStateArchive(new KeyValueStorageWorldStateStorage(keyValueStorage));

  ProtocolSchedule<Void> protocolSchedule =
      MainnetProtocolSchedule.create(2, 3, 10, 11, 12, -1, 42);
  ProtocolContext<Void> protocolContext = new ProtocolContext<>(blockchain, stateArchive, null);

  public ExecutionContextTestFixture() {
    GenesisConfig.mainnet()
        .writeStateTo(
            new DefaultMutableWorldState(new KeyValueStorageWorldStateStorage(keyValueStorage)));
  }

  public Block getGenesis() {
    return genesis;
  }

  public KeyValueStorage getKeyValueStorage() {
    return keyValueStorage;
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getStateArchive() {
    return stateArchive;
  }

  public ProtocolSchedule<Void> getProtocolSchedule() {
    return protocolSchedule;
  }

  public ProtocolContext<Void> getProtocolContext() {
    return protocolContext;
  }
}
