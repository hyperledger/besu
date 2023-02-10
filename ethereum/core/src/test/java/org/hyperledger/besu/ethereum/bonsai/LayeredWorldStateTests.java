/*
 * Copyright Hyperledger Besu contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.SnapshotMutableWorldState;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LayeredWorldStateTests {

  @Mock BonsaiWorldStateArchive archive;
  @Mock Blockchain blockchain;

  @Test
  public void layeredWorldStateUsesCorrectPersistedWorldStateOnCopy() {
    // when copying a layered worldstate we return mutable copy,
    // ensure it is for the correct/corresponding worldstate:

    Hash state1Hash = Hash.hash(Bytes.of("first_state".getBytes(StandardCharsets.UTF_8)));
    Hash block1Hash = Hash.hash(Bytes.of("first_block".getBytes(StandardCharsets.UTF_8)));
    var mockStorage = mock(BonsaiWorldStateKeyValueStorage.class);
    when(mockStorage.getWorldStateBlockHash()).thenReturn(Optional.of(block1Hash));
    when(mockStorage.getWorldStateRootHash()).thenReturn(Optional.of(state1Hash));
    SnapshotMutableWorldState mockState =
        when(mock(SnapshotMutableWorldState.class).getWorldStateStorage())
            .thenReturn(mockStorage)
            .getMock();

    TrieLogLayer mockLayer =
        when(mock(TrieLogLayer.class).getBlockHash()).thenReturn(Hash.ZERO).getMock();
    BonsaiLayeredWorldState mockLayerWs =
        new BonsaiLayeredWorldState(
            blockchain,
            archive,
            Optional.of(mock(BonsaiLayeredWorldState.class)),
            1L,
            state1Hash,
            mockLayer);

    // mimic persisted state being at a different state:
    when(archive.getMutableSnapshot(mockLayer.getBlockHash())).thenReturn(Optional.of(mockState));

    try (var copyOfLayer1 = mockLayerWs.copy()) {
      assertThat(copyOfLayer1.rootHash()).isEqualTo(state1Hash);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test
  public void saveTrieLogShouldUseCorrectPersistedWorldStateOnCopy() {
    // when we save a snapshot based worldstate, ensure
    // we used the passed in worldstate and roothash for calculating the trielog diff
    Hash testStateRoot = Hash.fromHexStringLenient("0xdeadbeef");
    BlockHeader testHeader = new BlockHeaderTestFixture().stateRoot(testStateRoot).buildHeader();

    BonsaiWorldStateKeyValueStorage testStorage =
        mock(BonsaiWorldStateKeyValueStorage.class, Answers.RETURNS_DEEP_STUBS);

    BonsaiSnapshotWorldState testState = mock(BonsaiSnapshotWorldState.class);
    when(testState.getWorldStateStorage()).thenReturn(testStorage);
    when(testState.rootHash()).thenReturn(testStateRoot);
    when(testState.blockHash()).thenReturn(testHeader.getBlockHash());

    BonsaiWorldStateUpdater testUpdater = new BonsaiWorldStateUpdater(testState);
    // mock kvstorage to mimic head being in a different state than testState
    LayeredTrieLogManager manager =
        spy(
            new LayeredTrieLogManager(
                blockchain, mock(BonsaiWorldStateKeyValueStorage.class), 10L, new HashMap<>()));

    // assert we are using the target worldstate storage:
    final AtomicBoolean calledPrepareTrieLog = new AtomicBoolean(false);
    doAnswer(
            prepareCallSpec -> {
              Hash blockHash = prepareCallSpec.getArgument(0, BlockHeader.class).getHash();
              Hash rootHash = prepareCallSpec.getArgument(1, Hash.class);
              BonsaiPersistedWorldState ws =
                  prepareCallSpec.getArgument(4, BonsaiPersistedWorldState.class);
              assertThat(ws.rootHash()).isEqualTo(rootHash);
              assertThat(ws.blockHash()).isEqualTo(blockHash);
              calledPrepareTrieLog.set(true);
              return mock(TrieLogLayer.class);
            })
        .when(manager)
        .prepareTrieLog(
            any(BlockHeader.class),
            any(Hash.class),
            any(BonsaiWorldStateUpdater.class),
            any(BonsaiWorldStateArchive.class),
            any(BonsaiPersistedWorldState.class));

    manager.saveTrieLog(archive, testUpdater, testStateRoot, testHeader, testState);
    assertThat(calledPrepareTrieLog.get()).isTrue();
  }
}
