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

package org.hyperledger.besu.consensus.merge.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConstantOmmersHashValidationTest {

  @Mock private ProtocolContext protocolContext;
  @Mock private MutableBlockchain blockchain;
  @Mock private MergeContext mergeContext;

  @Before
  public void setUp() {
    when(blockchain.getTotalDifficultyByHash(any())).thenReturn(Optional.of(Difficulty.ONE));
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(Difficulty.ONE);
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
  }

  @Test
  public void checksAndConfirmsOmmerHashValue() {
    final ConstantOmmersHashRule rule = new ConstantOmmersHashRule();
    final BlockHeader parentHeader = mock(BlockHeader.class);

    final BlockHeader invalidHeader = mock(BlockHeader.class);
    when(invalidHeader.getOmmersHash())
        .thenReturn(
            Hash.fromHexString(
                "0xb0b0faceb0b0faceb0b0faceb0b0faceb0b0faceb0b0faceb0b0faceb0b0face"));
    assertThat(rule.validate(invalidHeader, parentHeader, protocolContext)).isFalse();

    final BlockHeader validHeader = mock(BlockHeader.class);
    when(validHeader.getOmmersHash())
        .thenReturn(
            Hash.fromHexString(
                "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"));

    assertThat(rule.validate(validHeader, parentHeader, protocolContext)).isTrue();
  }
}
