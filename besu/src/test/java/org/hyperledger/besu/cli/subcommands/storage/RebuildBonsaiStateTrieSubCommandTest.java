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
package org.hyperledger.besu.cli.subcommands.storage;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.cli.subcommands.storage.RebuildBonsaiStateTrieSubCommand.BlockHashAndStateRoot;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.BlockTestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This test only exercises bonsai worldstate since forest is essentially a no-op for moving the
 * worldstate.
 */
public class RebuildBonsaiStateTrieSubCommandTest {

  Blockchain blockchain;
  WorldStateArchive archive;
  ProtocolContext protocolContext;
  ProtocolSchedule protocolSchedule;
  BlockchainSetupUtil blockchainSetupUtil;
  RebuildBonsaiStateTrieSubCommand command = new RebuildBonsaiStateTrieSubCommand();

  @BeforeEach
  public void setup() throws Exception {
    setupBonsaiBlockchain();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    archive = blockchainSetupUtil.getWorldArchive();
  }

  void setupBonsaiBlockchain() {
    blockchainSetupUtil =
        BlockchainSetupUtil.createForEthashChain(
            // Mainnet is a more robust test resource, but it takes upwards of 1 minute to generate
            // BlockTestUtil.getMainnetResources(),
            BlockTestUtil.getSnapTestChainResources(), DataStorageFormat.BONSAI);
    blockchainSetupUtil.importAllBlocks(HeaderValidationMode.NONE, HeaderValidationMode.NONE);
  }

  @Test
  public void assertStateRootMatchesAfterRebuild() {
    var worldstate = (BonsaiWorldState) archive.getMutable();
    var headRootHash = worldstate.rootHash();

    // drop the trie:
    worldstate.getWorldStateStorage().clearTrie();

    // rebuild the trie:
    var newHash = command.rebuildTrie(worldstate.getWorldStateStorage());

    assertThat(newHash).isEqualTo(headRootHash);
  }

  @Test
  public void assertBlockHashAndStateRootParsing() {

    assertThat(BlockHashAndStateRoot.create("0xdeadbeef:0xdeadbeef")).isNull();

    var mockVal = BlockHashAndStateRoot.create(Hash.EMPTY + ":" + Hash.EMPTY_TRIE_HASH);
    assertThat(mockVal).isNotNull();
    assertThat(mockVal.blockHash()).isEqualTo(Hash.EMPTY);
    assertThat(mockVal.stateRoot()).isEqualTo(Hash.EMPTY_TRIE_HASH);
  }
}
