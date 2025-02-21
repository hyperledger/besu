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

import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.qbft.validator.ValidatorModeTransitionLogger;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QbftValidatorModeTransitionLoggerAdaptorTest {
  @Mock private ValidatorModeTransitionLogger logger;

  @Test
  void logTransitionChangeDelegatesToLogger() {
    BlockHeader header = new BlockHeaderTestFixture().buildHeader();
    QbftBlockHeaderAdaptor qbftHeader = new QbftBlockHeaderAdaptor(header);

    var adaptor = new QbftValidatorModeTransitionLoggerAdaptor(logger);
    adaptor.logTransitionChange(qbftHeader);
    verify(logger).logTransitionChange(header);
  }
}
