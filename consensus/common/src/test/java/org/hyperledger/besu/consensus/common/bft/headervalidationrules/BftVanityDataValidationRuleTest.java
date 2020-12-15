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

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class BftVanityDataValidationRuleTest {

  private final BftVanityDataValidationRule validationRule = new BftVanityDataValidationRule();

  @Test
  public void testCases() {
    assertThat(headerWithVanityDataOfSize(0)).isFalse();
    assertThat(headerWithVanityDataOfSize(31)).isFalse();
    assertThat(headerWithVanityDataOfSize(32)).isTrue();
    assertThat(headerWithVanityDataOfSize(33)).isFalse();
  }

  public boolean headerWithVanityDataOfSize(final int extraDataSize) {
    final BftExtraData extraData =
        new BftExtraData(Bytes.wrap(new byte[extraDataSize]), emptyList(), empty(), 0, emptyList());
    final BlockHeader header =
        new BlockHeaderTestFixture().extraData(extraData.encode()).buildHeader();

    return validationRule.validate(header, null, null);
  }
}
