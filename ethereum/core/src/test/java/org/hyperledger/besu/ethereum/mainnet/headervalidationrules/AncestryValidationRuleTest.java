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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.junit.jupiter.api.Test;

public class AncestryValidationRuleTest {

  @Test
  public void incrementingNumberAndLinkedHashesReturnTrue() {
    final AncestryValidationRule uut = new AncestryValidationRule();

    final BlockHeader parentHeader = new BlockHeaderTestFixture().buildHeader();
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    headerBuilder.parentHash(parentHeader.getHash());
    headerBuilder.number(parentHeader.getNumber() + 1);

    final BlockHeader header = headerBuilder.buildHeader();
    assertThat(uut.validate(header, parentHeader)).isTrue();
  }

  @Test
  public void mismatchedHashesReturnsFalse() {
    final AncestryValidationRule uut = new AncestryValidationRule();

    final BlockHeader parentHeader = new BlockHeaderTestFixture().buildHeader();
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    headerBuilder.parentHash(Hash.EMPTY);
    headerBuilder.number(parentHeader.getNumber() + 1);

    final BlockHeader header = headerBuilder.buildHeader();
    assertThat(uut.validate(header, parentHeader)).isFalse();
  }

  @Test
  public void nonIncrementingBlockNumberReturnsFalse() {
    final AncestryValidationRule uut = new AncestryValidationRule();

    final BlockHeader parentHeader = new BlockHeaderTestFixture().buildHeader();
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    headerBuilder.parentHash(parentHeader.getHash());
    headerBuilder.number(parentHeader.getNumber() + 2);

    final BlockHeader header = headerBuilder.buildHeader();
    assertThat(uut.validate(header, parentHeader)).isFalse();
  }
}
