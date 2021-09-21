/*
 *  Copyright ConsenSys AG.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.junit.Test;

public class TimestampMoreRecentThanParentTest {
  final BlockHeader parentHeader = new BlockHeaderTestFixture().timestamp(10).buildHeader();

  @Test
  public void timestampLessThanMinimumFails() {
    final BlockHeader header = new BlockHeaderTestFixture().timestamp(11).buildHeader();
    final TimestampMoreRecentThanParent headerRule = new TimestampMoreRecentThanParent(5);
    assertThat(headerRule.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void timestampLessButWithinTolerancePasses() {
    final BlockHeader header = new BlockHeaderTestFixture().timestamp(14).buildHeader();
    final TimestampMoreRecentThanParent headerRule = new TimestampMoreRecentThanParent(5, 1);
    assertThat(headerRule.validate(header, parentHeader)).isTrue();
  }

  @Test
  public void timestampSameAsMinimumPasses() {
    final BlockHeader header = new BlockHeaderTestFixture().timestamp(15).buildHeader();
    final TimestampMoreRecentThanParent headerRule = new TimestampMoreRecentThanParent(5);
    assertThat(headerRule.validate(header, parentHeader)).isTrue();
  }

  @Test
  public void timestampGreaterThanMinimumPasses() {
    final BlockHeader header = new BlockHeaderTestFixture().timestamp(17).buildHeader();
    final TimestampMoreRecentThanParent headerRule = new TimestampMoreRecentThanParent(5);
    assertThat(headerRule.validate(header, parentHeader)).isTrue();
  }
}
