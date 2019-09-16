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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.uint.UInt256;

import org.junit.Test;

public class ConstantFieldValidationRuleTest {

  @Test
  public void ommersFieldValidatesCorrectly() {

    final ConstantFieldValidationRule<Hash> uut =
        new ConstantFieldValidationRule<>(
            "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH);

    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();
    blockHeaderBuilder.ommersHash(Hash.EMPTY_LIST_HASH);
    BlockHeader header = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, null)).isTrue();

    blockHeaderBuilder.ommersHash(Hash.ZERO);
    header = blockHeaderBuilder.buildHeader();
    assertThat(uut.validate(header, null)).isFalse();
  }

  @Test
  public void difficultyFieldIsValidatedCorrectly() {
    final ConstantFieldValidationRule<UInt256> uut =
        new ConstantFieldValidationRule<>("Difficulty", BlockHeader::getDifficulty, UInt256.ONE);

    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();
    blockHeaderBuilder.difficulty(UInt256.ONE);
    BlockHeader header = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, null)).isTrue();

    blockHeaderBuilder.difficulty(UInt256.ZERO);
    header = blockHeaderBuilder.buildHeader();
    assertThat(uut.validate(header, null)).isFalse();
  }
}
