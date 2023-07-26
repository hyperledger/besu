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

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the {@link DataGasValidationRule} class. */
public class DataGasValidationRuleTest {

  private CancunGasCalculator gasCalculator;
  private DataGasValidationRule dataGasValidationRule;

  @BeforeEach
  public void setUp() {
    gasCalculator = new CancunGasCalculator();
    dataGasValidationRule = new DataGasValidationRule(gasCalculator);
  }

  /** Tests that the header data gas matches the calculated data gas and passes validation. */
  @Test
  public void validateHeader_DataGasMatchesCalculated_SuccessValidation() {
    long target = gasCalculator.getTargetDataGasPerBlock();

    // Create parent header
    final BlockHeaderTestFixture parentBuilder = new BlockHeaderTestFixture();
    parentBuilder.excessDataGas(DataGas.of(1L));
    parentBuilder.dataGasUsed(target);
    final BlockHeader parentHeader = parentBuilder.buildHeader();

    // Create block header with matching excessDataGas
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.excessDataGas(DataGas.of(1L));
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(dataGasValidationRule.validate(header, parentHeader)).isTrue();
  }

  /**
   * Tests that the header data gas is different from the calculated data gas and fails validation.
   */
  @Test
  public void validateHeader_DataGasDifferentFromCalculated_FailsValidation() {
    long target = gasCalculator.getTargetDataGasPerBlock();

    // Create parent header
    final BlockHeaderTestFixture parentBuilder = new BlockHeaderTestFixture();
    parentBuilder.excessDataGas(DataGas.of(1L));
    parentBuilder.dataGasUsed(target);
    final BlockHeader parentHeader = parentBuilder.buildHeader();

    // Create block header with different excessDataGas
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader header = headerBuilder.buildHeader();

    assertThat(dataGasValidationRule.validate(header, parentHeader)).isFalse();
  }
}
