/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.NewBlockHashesMessage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;

import java.util.Collections;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BonsaiBlockPropagationManagerTest extends AbstractBlockPropagationManagerTest {

  private static Blockchain fullBlockchain;

  @BeforeClass
  public static void setupSuite() {
    fullBlockchain = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI).importAllBlocks();
  }

  @Before
  public void setup() {
    setup(DataStorageFormat.BONSAI);
  }

  @Override
  public Blockchain getFullBlockchain() {
    return fullBlockchain;
  }

  @Test
  @Override
  public void shouldRepeatGetBlockWhenFirstAttemptFails() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0);
    final RespondingEthPeer secondPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 2);

    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));

    // Broadcast first message
    EthProtocolManagerTestUtil.broadcastMessage(ethProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(RespondingEthPeer.emptyResponder(), peer::hasOutstandingRequests);
    secondPeer.respondWhile(RespondingEthPeer.emptyResponder(), secondPeer::hasOutstandingRequests);
    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
  }
}
