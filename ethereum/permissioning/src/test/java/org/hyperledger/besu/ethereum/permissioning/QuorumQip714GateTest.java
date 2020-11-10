package org.hyperledger.besu.ethereum.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
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
public class QuorumQip714GateTest {

  private Blockchain blockchain;
  private QuorumQip714Gate gate;

  @Before
  public void before() {
    blockchain = mock(Blockchain.class);
  }

  @Test
  public void gateShouldSubscribeAsBlockAddedObserver() {
    gate = QuorumQip714Gate.getInstance(100, blockchain);

    verify(blockchain).observeBlockAdded(any());
  }

  @Test
  public void whenTargetBlockIsZeroCheckPermissionsReturnTrue() {
    gate = QuorumQip714Gate.getInstance(0, blockchain);

    assertThat(gate.shouldCheckPermissions()).isTrue();
  }

  @Test
  public void whenBelowTargetBlockCheckPermissionsReturnFalse() {
    gate = QuorumQip714Gate.getInstance(99, blockchain);

    updateChainHead(55);

    assertThat(gate.shouldCheckPermissions()).isFalse();
  }

  @Test
  public void whenAboveTargetBlockCheckPermissionsReturnTrue() {
    gate = QuorumQip714Gate.getInstance(99, blockchain);

    updateChainHead(100);

    assertThat(gate.shouldCheckPermissions()).isTrue();
  }

  @Test
  public void latestBlockCheckShouldKeepUpToChainHeight() {
    gate = QuorumQip714Gate.getInstance(0, blockchain);
    assertThat(gate.getLatestBlock()).isEqualTo(0);

    updateChainHead(1);
    assertThat(gate.getLatestBlock()).isEqualTo(1);

    updateChainHead(3);
    assertThat(gate.getLatestBlock()).isEqualTo(3);

    updateChainHead(2);
    assertThat(gate.getLatestBlock()).isEqualTo(2);
  }

  private void updateChainHead(final int height) {
    final Block block = new BlockDataGenerator().block(new BlockOptions().setBlockNumber(height));
    gate.checkChainHeight(
        BlockAddedEvent.createForHeadAdvancement(
            block, Collections.emptyList(), Collections.emptyList()));
  }
}
