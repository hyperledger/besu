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
package org.hyperledger.besu.consensus.qbft.validator;

import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;

import java.util.Optional;

public class ValidatorSelectorConfig {

  private final long block;
  private final VALIDATOR_SELECTION_MODE validatorSelectionMode;
  private final Optional<String> contractAddress;

  private ValidatorSelectorConfig(
      final long block,
      final VALIDATOR_SELECTION_MODE validatorSelectionMode,
      final Optional<String> contractAddress) {
    this.block = block;
    this.validatorSelectionMode = validatorSelectionMode;
    this.contractAddress = contractAddress;
  }

  public static ValidatorSelectorConfig createBlockConfig(final long block) {
    return new ValidatorSelectorConfig(
        block, VALIDATOR_SELECTION_MODE.BLOCKHEADER, Optional.empty());
  }

  public static ValidatorSelectorConfig createContractConfig(
      final long block, final String contractAddress) {
    return new ValidatorSelectorConfig(
        block, VALIDATOR_SELECTION_MODE.CONTRACT, Optional.of(contractAddress));
  }

  public static Optional<ValidatorSelectorConfig> fromQbftFork(final QbftFork qbftFork) {
    if (qbftFork.getValidatorSelectionMode().isPresent()) {
      final VALIDATOR_SELECTION_MODE mode = qbftFork.getValidatorSelectionMode().get();
      if (mode == VALIDATOR_SELECTION_MODE.BLOCKHEADER) {
        return Optional.of(createBlockConfig(qbftFork.getForkBlock()));
      } else if (mode == VALIDATOR_SELECTION_MODE.CONTRACT
          && qbftFork.getValidatorContractAddress().isPresent()) {
        return Optional.of(
            createContractConfig(
                qbftFork.getForkBlock(), qbftFork.getValidatorContractAddress().get()));
      }
    }
    return Optional.empty();
  }

  public static ValidatorSelectorConfig fromQbftConfig(final QbftConfigOptions qbftConfigOptions) {
    if (qbftConfigOptions.getValidatorContractAddress().isPresent()) {
      return createContractConfig(0, qbftConfigOptions.getValidatorContractAddress().get());
    } else {
      return createBlockConfig(0);
    }
  }

  public long getBlock() {
    return block;
  }

  public VALIDATOR_SELECTION_MODE getValidatorSelectionMode() {
    return validatorSelectionMode;
  }

  public Optional<String> getContractAddress() {
    return contractAddress;
  }
}
