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
package org.hyperledger.besu.evm.contractvalidation;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaxCodeSizeRule implements ContractValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(MaxCodeSizeRule.class);

  private final int maxCodeSize;

  MaxCodeSizeRule(final int maxCodeSize) {
    this.maxCodeSize = maxCodeSize;
  }

  @Override
  public Optional<ExceptionalHaltReason> validate(final MessageFrame frame) {
    final int contractCodeSize = frame.getOutputData().size();
    if (contractCodeSize <= maxCodeSize) {
      return Optional.empty();
    } else {
      LOG.trace(
          "Contract creation error: code size {} exceeds code size limit {}",
          contractCodeSize,
          maxCodeSize);
      return Optional.of(ExceptionalHaltReason.CODE_TOO_LARGE);
    }
  }

  public static ContractValidationRule of(final int maxCodeSize) {
    return new MaxCodeSizeRule(maxCodeSize);
  }
}
