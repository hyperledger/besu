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
package org.hyperledger.besu.ethereum.goquorum;

import static org.hyperledger.besu.ethereum.goquorum.GoQuorumPrivateStateUtil.getPrivateWorldState;

import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;

import java.util.Optional;

public class GoQuorumBlockValidator extends MainnetBlockValidator {

  private final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters;

  public GoQuorumBlockValidator(
      final BlockHeaderValidator blockHeaderValidator,
      final BlockBodyValidator blockBodyValidator,
      final BlockProcessor blockProcessor,
      final BadBlockManager badBlockManager,
      final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters) {
    super(blockHeaderValidator, blockBodyValidator, blockProcessor, badBlockManager);

    this.goQuorumPrivacyParameters = goQuorumPrivacyParameters;

    if (!(blockProcessor instanceof GoQuorumBlockProcessor)) {
      throw new IllegalStateException(
          "GoQuorumBlockValidator requires an instance of GoQuorumBlockProcessor");
    }
  }

  @Override
  protected BlockProcessor.Result processBlock(
      final ProtocolContext context, final MutableWorldState worldState, final Block block) {
    final MutableWorldState privateWorldState =
        getPrivateWorldState(goQuorumPrivacyParameters, worldState.rootHash(), block.getHash());

    return ((GoQuorumBlockProcessor) blockProcessor)
        .processBlock(context.getBlockchain(), worldState, privateWorldState, block);
  }
}
