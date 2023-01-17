/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli.subcommands.bonsai;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiConsistencyCheckSubCommandTest {
  @Mock KeyValueStoragePrefixedKeyBlockchainStorage chainStorage;
  @Mock KeyValueStoragePrefixedKeyBlockchainStorage.Updater updater;
  @Mock BonsaiWorldStateKeyValueStorage bonsaiStorage;
  @Mock BonsaiWorldStateArchive archive;

  BonsaiConsistencyCheckSubCommand cmdTest = spy(new BonsaiConsistencyCheckSubCommand());
  BlockHeader genesis = new BlockHeaderTestFixture().buildHeader();
  Blockchain blockchain;

  @Before
  public void setup() {
    doNothing().when(cmdTest).exitWithError(anyString());
    when(chainStorage.getTotalDifficulty(any(Hash.class))).thenReturn(Optional.of(Difficulty.ONE));
    when(chainStorage.updater()).thenReturn(updater);
    when(chainStorage.getChainHead()).thenReturn(Optional.of(genesis.getHash()));
    when(chainStorage.getBlockHeader(genesis.getHash())).thenReturn(Optional.of(genesis));
    when(chainStorage.getBlockHash(0L)).thenReturn(Optional.of(genesis.getHash()));
    when(chainStorage.getBlockBody(genesis.getHash()))
        .thenReturn(
            Optional.of(
                new BlockBody(Collections.emptyList(), Collections.emptyList(), Optional.empty())));
    blockchain =
        spy(DefaultBlockchain.create(chainStorage, new NoOpMetricsSystem(), Long.MAX_VALUE));
    doReturn(archive).when(cmdTest).getWorldStateArchive(chainStorage, bonsaiStorage);
  }

  @Test
  public void consistencyNoOpTest() {
    BlockHeader consistentHeader = new BlockHeaderTestFixture().buildHeader();
    when(chainStorage.getChainHead()).thenReturn(Optional.of(consistentHeader.getHash()));
    when(bonsaiStorage.getWorldStateBlockHash())
        .thenReturn(Optional.of(consistentHeader.getHash()));
    assertThat(cmdTest.maybeRepairChainAndWorldState(chainStorage, bonsaiStorage)).isFalse();
    verify(cmdTest, times(0)).exitWithError(anyString());
  }

  @Test
  public void fixChainAheadOfWorldStateTest() {

    var headerOne =
        new BlockHeaderTestFixture().number(1).parentHash(genesis.getHash()).buildHeader();
    var headerTwo =
        new BlockHeaderTestFixture().number(2).parentHash(headerOne.getHash()).buildHeader();
    var headerThree =
        new BlockHeaderTestFixture().number(3).parentHash(headerTwo.getHash()).buildHeader();
    when(chainStorage.getChainHead()).thenReturn(Optional.of(headerThree.getHash()));
    when(chainStorage.getBlockHeader(headerOne.getHash())).thenReturn(Optional.of(headerOne));
    when(chainStorage.getBlockHeader(headerTwo.getHash())).thenReturn(Optional.of(headerTwo));
    when(chainStorage.getBlockHeader(headerThree.getHash())).thenReturn(Optional.of(headerThree));
    when(bonsaiStorage.getWorldStateBlockHash()).thenReturn(Optional.of(headerOne.getHash()));

    assertThat(cmdTest.maybeRepairChainAndWorldState(chainStorage, bonsaiStorage)).isTrue();
    verify(updater, times(2)).removeBlockHash(anyLong());
    verify(updater, times(2)).removeBlockBody(any(Hash.class));
    verify(updater, times(2)).removeBlockHeader(any(Hash.class));
    verify(updater, times(1)).setChainHead(any(Hash.class));
    verify(updater, times(1)).commit();
  }

  @Test
  public void fixChainBehindWorldStateTest() {
    var headerOne =
        new BlockHeaderTestFixture().number(1).parentHash(genesis.getHash()).buildHeader();
    var headerTwo =
        new BlockHeaderTestFixture().number(2).parentHash(headerOne.getHash()).buildHeader();
    var headerThree =
        new BlockHeaderTestFixture().number(3).parentHash(headerTwo.getHash()).buildHeader();
    when(chainStorage.getChainHead()).thenReturn(Optional.of(headerOne.getHash()));
    when(chainStorage.getBlockHeader(headerOne.getHash())).thenReturn(Optional.of(headerOne));
    when(chainStorage.getBlockHeader(headerTwo.getHash())).thenReturn(Optional.of(headerTwo));
    when(chainStorage.getBlockHeader(headerThree.getHash())).thenReturn(Optional.of(headerThree));
    when(bonsaiStorage.getWorldStateBlockHash()).thenReturn(Optional.of(headerThree.getHash()));
    when(archive.getMutable(headerOne.getStateRoot(), headerOne.getHash()))
        .thenReturn(Optional.of(mock(MutableWorldState.class)));

    assertThat(cmdTest.maybeRepairChainAndWorldState(chainStorage, bonsaiStorage)).isTrue();
    verify(updater, times(0)).removeBlockHash(anyLong());
    verify(updater, times(0)).removeBlockBody(any(Hash.class));
    verify(updater, times(0)).removeBlockHeader(any(Hash.class));
    verify(updater, times(0)).setChainHead(any(Hash.class));
    verify(updater, times(0)).commit();
    verify(cmdTest, times(0)).exitWithError(anyString());
  }

  @Test
  public void exitForChainForkTest() {

    var newGenesis =
        new BlockHeaderTestFixture().coinbase(Address.fromHexString("0xdeadbeef")).buildHeader();
    var headerOne =
        new BlockHeaderTestFixture().number(1).parentHash(newGenesis.getHash()).buildHeader();
    when(chainStorage.getChainHead()).thenReturn(Optional.of(headerOne.getHash()));
    when(chainStorage.getBlockHeader(headerOne.getHash())).thenReturn(Optional.of(headerOne));
    when(chainStorage.getBlockHeader(newGenesis.getHash())).thenReturn(Optional.of(newGenesis));
    when(bonsaiStorage.getWorldStateBlockHash()).thenReturn(Optional.of(genesis.getHash()));

    assertThat(cmdTest.maybeRepairChainAndWorldState(chainStorage, bonsaiStorage)).isFalse();
    verify(cmdTest, times(1)).exitWithError(anyString());
  }

  @Test
  public void exitForWorldBlockNotFoundTest() {

    var headerOne =
        new BlockHeaderTestFixture().number(1).parentHash(genesis.getHash()).buildHeader();
    when(bonsaiStorage.getWorldStateBlockHash()).thenReturn(Optional.of(headerOne.getHash()));

    assertThat(cmdTest.maybeRepairChainAndWorldState(chainStorage, bonsaiStorage)).isFalse();
    verify(cmdTest, times(1)).exitWithError(anyString());
  }
}
