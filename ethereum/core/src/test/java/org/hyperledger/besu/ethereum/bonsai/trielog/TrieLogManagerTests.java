package org.hyperledger.besu.ethereum.bonsai.trielog;

import static org.junit.Assert.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedWorldStorageManager;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(MockitoJUnitRunner.class)
public class TrieLogManagerTests {

  BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) BonsaiWorldStateUpdateAccumulator bonsaiUpdater;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) BonsaiWorldState bonsaiWorldState;
  @Mock BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage;
  @Mock BonsaiWorldStateProvider archive;
  @Mock Blockchain blockchain;


  TrieLogManager trieLogManager;

  @Before
  public void setup() {
    trieLogManager = new CachedWorldStorageManager(archive, blockchain, bonsaiWorldStateKeyValueStorage, 512);
  }
  @Test
  public void testSaveTrieLogEvent() {
    AtomicBoolean eventFired = new AtomicBoolean(false);
    trieLogManager.subscribe(layer -> {
      assertThat(layer).isNotNull();
      eventFired.set(true);
    });
    trieLogManager.saveTrieLog(bonsaiUpdater, Hash.ZERO, blockHeader, bonsaiWorldState);

    assertThat(eventFired.get()).isTrue();
  }

}
