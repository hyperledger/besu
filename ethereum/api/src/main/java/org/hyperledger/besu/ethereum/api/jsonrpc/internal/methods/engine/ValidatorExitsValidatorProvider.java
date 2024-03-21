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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidator.StubValidatorExitsValidator;

public class ValidatorExitsValidatorProvider {

  static ValidatorExitsValidator getValidatorExitsValidator(
      final ProtocolSchedule protocolSchedule, final long blockTimestamp, final long blockNumber) {

    final BlockHeader blockHeader =
        BlockHeaderBuilder.createDefault()
            .timestamp(blockTimestamp)
            .number(blockNumber)
            .buildBlockHeader();
    return getValidatorExitsValidator(protocolSchedule.getByBlockHeader(blockHeader));
  }

  @SuppressWarnings("unused")
  private static ValidatorExitsValidator getValidatorExitsValidator(final ProtocolSpec protocolSchedule) {
    //TODO hook up proper logic to chose validator based on milestone
    return new StubValidatorExitsValidator();
  }
}
