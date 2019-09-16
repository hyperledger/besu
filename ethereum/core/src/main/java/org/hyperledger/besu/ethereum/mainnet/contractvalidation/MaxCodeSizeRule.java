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
package org.hyperledger.besu.ethereum.mainnet.contractvalidation;

import org.hyperledger.besu.ethereum.mainnet.ContractValidationRule;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MaxCodeSizeRule implements ContractValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  private final int maxCodeSize;

  private MaxCodeSizeRule(final int maxCodeSize) {
    this.maxCodeSize = maxCodeSize;
  }

  @Override
  public boolean validate(final MessageFrame frame) {
    final int contractCodeSize = frame.getOutputData().size();
    if (contractCodeSize <= maxCodeSize) {
      return true;
    } else {
      LOG.trace(
          "Contract creation error: code size {} exceeds code size limit {}",
          contractCodeSize,
          maxCodeSize);
      return false;
    }
  }

  public static ContractValidationRule of(final int maxCodeSize) {
    return new MaxCodeSizeRule(maxCodeSize);
  }
}
