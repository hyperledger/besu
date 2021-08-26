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
package org.hyperledger.besu.consensus.common.bft.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraData;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

public class BftValidatorsValidationRuleTest {

  private final BftValidatorsValidationRule validatorsValidationRule =
      new BftValidatorsValidationRule();
  private final BftExtraData bftExtraData = mock(BftExtraData.class);
  private final BlockHeader blockHeader = mock(BlockHeader.class);

  @Test
  public void correctlyConstructedHeaderPassesValidation() {
    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithBftExtraData(validators, bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(validators);

    assertThat(validatorsValidationRule.validate(blockHeader, null, context)).isTrue();
  }

  @Test
  public void validatorsInNonAscendingOrderFailValidation() {

    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final ProtocolContext context =
        new ProtocolContext(null, null, setupContextWithBftExtraData(validators, bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(Lists.reverse(validators));

    assertThat(validatorsValidationRule.validate(blockHeader, null, context)).isFalse();
  }

  @Test
  public void mismatchingReportedValidatorsVsLocallyStoredListFailsValidation() {
    final List<Address> storedValidators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final List<Address> reportedValidators =
        Lists.newArrayList(
            AddressHelpers.ofValue(2), AddressHelpers.ofValue(3), AddressHelpers.ofValue(4));

    final ProtocolContext context =
        new ProtocolContext(
            null, null, setupContextWithBftExtraData(storedValidators, bftExtraData));
    when(bftExtraData.getValidators()).thenReturn(Lists.reverse(reportedValidators));

    assertThat(validatorsValidationRule.validate(blockHeader, null, context)).isFalse();
  }
}
