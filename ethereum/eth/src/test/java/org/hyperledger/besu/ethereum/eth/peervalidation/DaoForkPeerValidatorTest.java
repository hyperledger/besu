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
package org.hyperledger.besu.ethereum.eth.peervalidation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class DaoForkPeerValidatorTest extends AbstractPeerBlockValidatorTest {

  @Override
  AbstractPeerBlockValidator getSampleValidator(final long blockNumber) {
    return new DaoForkPeerValidator(
        MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), blockNumber, 0);
  }

  @Test
  public void validatePeer_responsivePeerOnRightSideOfFork() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long daoBlockNumber = 500;
    final Block daoBlock =
        gen.block(
            BlockOptions.create()
                .setBlockNumber(daoBlockNumber)
                .setExtraData(MainnetBlockHeaderValidator.DAO_EXTRA_DATA));

    final PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, 0);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    assertThat(result).isNotDone();

    // Send response for dao block
    final AtomicBoolean daoBlockRequested = respondToBlockRequest(peer, daoBlock);

    assertThat(daoBlockRequested).isTrue();
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  public void validatePeer_responsivePeerOnWrongSideOfFork() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long daoBlockNumber = 500;
    final Block daoBlock =
        gen.block(
            BlockOptions.create().setBlockNumber(daoBlockNumber).setExtraData(BytesValue.EMPTY));

    final PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, 0);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    assertThat(result).isNotDone();

    // Send response for dao block
    final AtomicBoolean daoBlockRequested = respondToBlockRequest(peer, daoBlock);

    assertThat(daoBlockRequested).isTrue();
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }
}
