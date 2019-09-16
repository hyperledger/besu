/*
 * Copyright 2019 ConsenSys AG.
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
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.junit.Test;

public class IbftVanityDataValidationRuleTest {

  private final IbftVanityDataValidationRule validationRule = new IbftVanityDataValidationRule();

  @Test
  public void testCases() {
    assertThat(headerWithVanityDataOfSize(0)).isFalse();
    assertThat(headerWithVanityDataOfSize(31)).isFalse();
    assertThat(headerWithVanityDataOfSize(32)).isTrue();
    assertThat(headerWithVanityDataOfSize(33)).isFalse();
  }

  public boolean headerWithVanityDataOfSize(final int extraDataSize) {
    final IbftExtraData extraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[extraDataSize]), emptyList(), empty(), 0, emptyList());
    final BlockHeader header =
        new BlockHeaderTestFixture().extraData(extraData.encode()).buildHeader();

    return validationRule.validate(header, null, null);
  }
}
