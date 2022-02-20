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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MergeUnfinalizedValidationRuleTest {

  @Mock private ProtocolContext protocolContext;
  @Mock private MergeContext mergeContext;
  @Mock private BlockHeader finalizedHeader;
  final MergeUnfinalizedValidationRule rule = new MergeUnfinalizedValidationRule();

  @Before
  public void setUp() {
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
    when(finalizedHeader.getNumber()).thenReturn(50L);
    when(finalizedHeader.getHash()).thenReturn(Hash.ZERO);
    when(mergeContext.getFinalized()).thenReturn(Optional.of(finalizedHeader));
  }

  @Test
  public void shouldFailBlocksPriorToFinalized() {
    final BlockHeader invalidHeader = mock(BlockHeader.class);
    when(invalidHeader.getHash()).thenReturn(Hash.fromHexStringLenient("0x1337"));
    when(invalidHeader.getNumber()).thenReturn(1L);
    assertThat(rule.validate(invalidHeader, mock(BlockHeader.class), protocolContext)).isFalse();
  }

  @Test
  public void shouldPassBlocksWithSameHashAsFinalized() {
    final BlockHeader duplicateOfFinalized = mock(BlockHeader.class);
    when(duplicateOfFinalized.getHash()).thenReturn(Hash.ZERO);
    when(duplicateOfFinalized.getNumber()).thenReturn(50L);
    assertThat(rule.validate(duplicateOfFinalized, mock(BlockHeader.class), protocolContext))
        .isTrue();
  }
}
