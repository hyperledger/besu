package org.hyperledger.besu.ethereum.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

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
public class GoQuorumQip714GateTest {

  private Blockchain blockchain;
  private GoQuorumQip714Gate gate;

  @Before
  public void before() {
    blockchain = mock(Blockchain.class);
  }

  @Test
  public void gateShouldSubscribeAsBlockAddedObserver() {
    gate = new GoQuorumQip714Gate(100, blockchain);

    verify(blockchain).observeBlockAdded(any());
  }

  @Test
  public void whenTargetBlockIsZeroCheckPermissionsReturnTrue() {
    gate = new GoQuorumQip714Gate(0, blockchain);

    assertThat(gate.shouldCheckPermissions()).isTrue();
  }

  @Test
  public void whenBelowTargetBlockCheckPermissionsReturnFalse() {
    gate = new GoQuorumQip714Gate(99, blockchain);

    updateChainHead(55);

    assertThat(gate.shouldCheckPermissions()).isFalse();
  }

  @Test
  public void whenAboveTargetBlockCheckPermissionsReturnTrue() {
    gate = new GoQuorumQip714Gate(99, blockchain);

    updateChainHead(100);

    assertThat(gate.shouldCheckPermissions()).isTrue();
  }

  @Test
  public void latestBlockCheckShouldKeepUpToChainHeight() {
    gate = new GoQuorumQip714Gate(0, blockchain);
    assertThat(gate.getLatestBlock()).isEqualTo(0);

    updateChainHead(1);
    assertThat(gate.getLatestBlock()).isEqualTo(1);

    updateChainHead(3);
    assertThat(gate.getLatestBlock()).isEqualTo(3);

    updateChainHead(2);
    assertThat(gate.getLatestBlock()).isEqualTo(2);
  }

  @Test
  public void getInstanceForbidInstancesWithDifferentQip714BlockNumber() {
    // creating singleton with qip714block = 1
    GoQuorumQip714Gate.getInstance(1L, blockchain);

    // creating new instance with qip714block != 1 should fail
    assertThatThrownBy(() -> GoQuorumQip714Gate.getInstance(2L, blockchain))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Tried to create Quorum QIP-714 gate with different block config from already instantiated gate block config");
  }

  private void updateChainHead(final int height) {
    final Block block = new BlockDataGenerator().block(new BlockOptions().setBlockNumber(height));
    gate.checkChainHeight(
        BlockAddedEvent.createForHeadAdvancement(
            block, Collections.emptyList(), Collections.emptyList()));
  }
}
