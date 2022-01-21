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
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.QbftForksSchedulesFactory;
import org.hyperledger.besu.consensus.qbft.QbftProtocolSchedule;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import javax.inject.Named;

class QBFTGenesisFileModule extends GenesisFileModule {
  final QbftExtraDataCodec bftExtraDataEncoder;

  QBFTGenesisFileModule(final String genesisConfig) {
    super(genesisConfig);
    bftExtraDataEncoder = new QbftExtraDataCodec();
  }

  @Override
  ProtocolSchedule provideProtocolSchedule(
      final GenesisConfigOptions configOptions,
      @Named("RevertReasonEnabled") final boolean revertReasonEnabled) {
    final ForksSchedule<QbftConfigOptions> forksSchedule =
        QbftForksSchedulesFactory.create(configOptions);
    return QbftProtocolSchedule.create(
        configOptions, forksSchedule, revertReasonEnabled, bftExtraDataEncoder);
  }

  @Override
  BlockHeaderFunctions blockHashFunction() {
    return BftBlockHeaderFunctions.forOnchainBlock(bftExtraDataEncoder);
  }
}
