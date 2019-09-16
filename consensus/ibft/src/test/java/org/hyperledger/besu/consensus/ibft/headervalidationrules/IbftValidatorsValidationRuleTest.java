/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.consensus.ibft.headervalidationrules;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.IbftContextBuilder.setupContextWithValidators;

import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;

public class IbftValidatorsValidationRuleTest {

  private final IbftValidatorsValidationRule validatorsValidationRule =
      new IbftValidatorsValidationRule();

  @Test
  public void correctlyConstructedHeaderPassesValidation() {
    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final BlockHeader header =
        HeaderValidationTestHelpers.createProposedBlockHeader(validators, emptyList(), false);

    assertThat(validatorsValidationRule.validate(header, null, context)).isTrue();
  }

  @Test
  public void validatorsInNonAscendingOrderFailValidation() {

    final List<Address> validators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(validators));

    final BlockHeader header =
        HeaderValidationTestHelpers.createProposedBlockHeader(
            Lists.reverse(validators), emptyList(), false);

    assertThat(validatorsValidationRule.validate(header, null, context)).isFalse();
  }

  @Test
  public void mismatchingReportedValidatorsVsLocallyStoredListFailsValidation() {
    final List<Address> storedValidators =
        Lists.newArrayList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));

    final List<Address> reportedValidators =
        Lists.newArrayList(
            AddressHelpers.ofValue(2), AddressHelpers.ofValue(3), AddressHelpers.ofValue(4));

    final ProtocolContext<IbftContext> context =
        new ProtocolContext<>(null, null, setupContextWithValidators(storedValidators));

    final BlockHeader header =
        HeaderValidationTestHelpers.createProposedBlockHeader(
            reportedValidators, emptyList(), false);

    assertThat(validatorsValidationRule.validate(header, null, context)).isFalse();
  }
}
