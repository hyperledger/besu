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

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockImporter;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockValidator;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSpec;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

/** Adaptor class to allow a {@link ProtocolSpec} to be used as a {@link QbftProtocolSpec}. */
public class QbftProtocolSpecAdaptor implements QbftProtocolSpec {
  private final ProtocolSpec besuProtocolSpec;
  private final ProtocolContext context;

  /**
   * Constructs a new Qbft protocol spec.
   *
   * @param besuProtocolSpec The Besu protocol spec.
   * @param context The protocol context.
   */
  public QbftProtocolSpecAdaptor(
      final ProtocolSpec besuProtocolSpec, final ProtocolContext context) {
    this.besuProtocolSpec = besuProtocolSpec;
    this.context = context;
  }

  @Override
  public QbftBlockImporter getBlockImporter() {
    return new QbftBlockImporterAdaptor(besuProtocolSpec.getBlockImporter(), context);
  }

  @Override
  public QbftBlockValidator getBlockValidator() {
    return new QbftBlockValidatorAdaptor(besuProtocolSpec.getBlockValidator(), context);
  }
}
