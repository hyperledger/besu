/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftValidatorModeTransitionLogger;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorModeTransitionLogger;

/**
 * Adaptor class to allow the {@link ValidatorModeTransitionLogger} to be used as a {@link
 * QbftValidatorModeTransitionLogger}.
 */
public class QbftValidatorModeTransitionLoggerAdaptor implements QbftValidatorModeTransitionLogger {

  private final ValidatorModeTransitionLogger validatorModeTransitionLogger;

  /**
   * Create a new instance of the adaptor.
   *
   * @param validatorModeTransitionLogger the {@link ValidatorModeTransitionLogger} to adapt.
   */
  public QbftValidatorModeTransitionLoggerAdaptor(
      final ValidatorModeTransitionLogger validatorModeTransitionLogger) {
    this.validatorModeTransitionLogger = validatorModeTransitionLogger;
  }

  @Override
  public void logTransitionChange(final QbftBlockHeader parentHeader) {
    validatorModeTransitionLogger.logTransitionChange(BlockUtil.toBesuBlockHeader(parentHeader));
  }
}
