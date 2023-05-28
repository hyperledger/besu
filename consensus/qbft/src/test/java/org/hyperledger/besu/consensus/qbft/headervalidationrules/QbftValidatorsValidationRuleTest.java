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
package org.hyperledger.besu.consensus.qbft.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraData;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Test;

public class QbftValidatorsValidationRuleTest {
  private final BftExtraData bftExtraData = mock(BftExtraData.class);
  private final BlockHeader blockHeader = mock(BlockHeader.class);

  @Test
  public void validationPassesIfValidatorsAndVoteAreEmpty() {
    final QbftValidatorsValidationRule qbftValidatorsValidationRule =
        new QbftValidatorsValidationRule(true);
    final ProtocolContext context =
        new ProtocolContext(
            null,
            null,
            setupContextWithBftExtraData(QbftContext.class, Collections.emptyList(), bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(Collections.emptyList());
    when(bftExtraData.getVote()).thenReturn(Optional.empty());
    assertThat(qbftValidatorsValidationRule.validate(blockHeader, null, context)).isTrue();
  }

  @Test
  public void validationIsDelegatedWhenConstructorFlagIsFalse() {
    final QbftValidatorsValidationRule qbftValidatorsValidationRule =
        new QbftValidatorsValidationRule(false);
    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final ProtocolContext context =
        new ProtocolContext(
            null, null, setupContextWithBftExtraData(QbftContext.class, validators, bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(validators);
    assertThat(qbftValidatorsValidationRule.validate(blockHeader, null, context)).isTrue();
  }

  @Test
  public void validationFailsIfValidatorsAreNotEmpty() {
    final QbftValidatorsValidationRule qbftValidatorsValidationRule =
        new QbftValidatorsValidationRule(true);
    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final ProtocolContext context =
        new ProtocolContext(
            null, null, setupContextWithBftExtraData(QbftContext.class, validators, bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(validators);
    assertThat(qbftValidatorsValidationRule.validate(blockHeader, null, context)).isFalse();
  }

  @Test
  public void validationFailsIfVoteIsPresent() {
    final QbftValidatorsValidationRule qbftValidatorsValidationRule =
        new QbftValidatorsValidationRule(true);
    final ProtocolContext context =
        new ProtocolContext(
            null,
            null,
            setupContextWithBftExtraData(QbftContext.class, Collections.emptyList(), bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(Collections.emptyList());
    when(bftExtraData.getVote()).thenReturn(Optional.of(mock(Vote.class)));
    assertThat(qbftValidatorsValidationRule.validate(blockHeader, null, context)).isFalse();
  }
}
