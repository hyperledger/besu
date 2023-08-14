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

package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the {@link BlobGasValidationRule} class. */
public class BlobGasValidationRuleTest {

  private CancunGasCalculator gasCalculator;
  private BlobGasValidationRule blobGasValidationRule;

  @BeforeEach
  public void setUp() {
    gasCalculator = new CancunGasCalculator();
    blobGasValidationRule = new BlobGasValidationRule(gasCalculator);
  }

  /** Tests that the header blob gas matches the calculated blob gas and passes validation. */
  @Test
  public void validateHeader_BlobGasMatchesCalculated_SuccessValidation() {
    long target = gasCalculator.getTargetBlobGasPerBlock();

    // Create parent header
    final BlockHeaderTestFixture parentBuilder = new BlockHeaderTestFixture();
    parentBuilder.excessBlobGas(BlobGas.of(1L));
    parentBuilder.blobGasUsed(target);
    final BlockHeader parentHeader = parentBuilder.buildHeader();

    // Create block header with matching excessBlobGas
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.excessBlobGas(BlobGas.of(1L));
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(blobGasValidationRule.validate(header, parentHeader)).isTrue();
  }

  /**
   * Tests that the header blob gas is different from the calculated blob gas and fails validation.
   */
  @Test
  public void validateHeader_BlobGasDifferentFromCalculated_FailsValidation() {
    long target = gasCalculator.getTargetBlobGasPerBlock();

    // Create parent header
    final BlockHeaderTestFixture parentBuilder = new BlockHeaderTestFixture();
    parentBuilder.excessBlobGas(BlobGas.of(1L));
    parentBuilder.blobGasUsed(target);
    final BlockHeader parentHeader = parentBuilder.buildHeader();

    // Create block header with different excessBlobGas
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(blobGasValidationRule.validate(header, parentHeader)).isFalse();
  }
}
