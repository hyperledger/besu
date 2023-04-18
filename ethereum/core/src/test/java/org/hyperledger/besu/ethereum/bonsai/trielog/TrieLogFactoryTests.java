package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedWorldStorageManager;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrieLogFactoryTests {

  Blockchain blockchain;
  BonsaiWorldStateKeyValueStorage worldStateStorage;
  BonsaiWorldStateProvider archive;
  TrieLogManager trieLogManager;
  BlockchainSetupUtil setup = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);

  @Before
  public void setup() {
    var noopMetrics = new NoOpMetricsSystem();
    blockchain = setup.getBlockchain();

    worldStateStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem());

    archive =
        new BonsaiWorldStateProvider(
            worldStateStorage,
            blockchain,
            Optional.of(512L),
            new CachedMerkleTrieLoader(noopMetrics),
            noopMetrics);

    trieLogManager =
        new CachedWorldStorageManager(
            archive, blockchain, worldStateStorage, new NoOpMetricsSystem(), 512);

    setup.getGenesisState().writeStateTo(archive.getMutable());
  }

  @Test
  public void testGenerateTrieLogFixture() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .parentHash(setup.getGenesisState().getBlock().getHash())
            .coinbase(Address.ZERO)
            .buildHeader();

    TrieLogLayer fixture =
        ((AbstractTrieLogManager) trieLogManager)
            .prepareTrieLog(
                header, (BonsaiWorldStateUpdateAccumulator) archive.getMutable().updater());
    byte[] rlp = new TrieLogFactoryImpl().serialize(fixture);
    if (rlp.length > 0) {
      System.out.println("rlp = " + Arrays.toString(rlp));
    }
  }
}
