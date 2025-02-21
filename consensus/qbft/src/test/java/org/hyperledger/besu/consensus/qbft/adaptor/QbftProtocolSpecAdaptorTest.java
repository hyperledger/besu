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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockImporter;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QbftProtocolSpecAdaptorTest {
  @Mock private ProtocolSpec besuProtocolSpec;
  @Mock private ProtocolContext besuProtocolContext;

  @Test
  void createsBlockImporterUsingBesuBlockImporter() {
    final BlockImporter besuBlockImporter = Mockito.mock(BlockImporter.class);
    when(besuProtocolSpec.getBlockImporter()).thenReturn(besuBlockImporter);

    final QbftProtocolSpecAdaptor qbftProtocolSpec =
        new QbftProtocolSpecAdaptor(besuProtocolSpec, besuProtocolContext);
    final QbftBlockImporter qbftBlockImporter = qbftProtocolSpec.getBlockImporter();
    assertThat(qbftBlockImporter).hasFieldOrPropertyWithValue("blockImporter", besuBlockImporter);
  }

  @Test
  void createsBlockValidatorUsingBesuBlockValidator() {
    final BlockValidator besuBlockValidator = Mockito.mock(BlockValidator.class);
    when(besuProtocolSpec.getBlockValidator()).thenReturn(besuBlockValidator);

    final QbftProtocolSpecAdaptor qbftProtocolSpec =
        new QbftProtocolSpecAdaptor(besuProtocolSpec, besuProtocolContext);
    final QbftBlockValidator qbftBlockValidator = qbftProtocolSpec.getBlockValidator();
    assertThat(qbftBlockValidator)
        .hasFieldOrPropertyWithValue("blockValidator", besuBlockValidator);
  }
}
