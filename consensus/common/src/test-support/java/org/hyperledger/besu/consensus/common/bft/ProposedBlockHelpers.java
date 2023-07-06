/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class ProposedBlockHelpers {

  public static Block createProposalBlock(
      final List<Address> validators, final ConsensusRoundIdentifier roundId) {
    final BlockOptions blockOptions =
        BlockOptions.create()
            .setBlockNumber(roundId.getSequenceNumber())
            .hasOmmers(false)
            .hasTransactions(false);

    if (validators.size() > 0) {
      blockOptions.setCoinbase(validators.get(0));
    }
    return new BlockDataGenerator().block(blockOptions);
  }

  public static Block createProposalBlock(
      final List<Address> validators,
      final ConsensusRoundIdentifier roundId,
      final BftExtraDataCodec bftExtraDataCodec) {
    final Bytes extraData =
        bftExtraDataCodec.encode(
            new BftExtraData(
                Bytes.wrap(new byte[32]),
                Collections.emptyList(),
                Optional.empty(),
                roundId.getRoundNumber(),
                validators));
    final BlockOptions blockOptions =
        BlockOptions.create()
            .setExtraData(extraData)
            .setBlockNumber(roundId.getSequenceNumber())
            .setBlockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec))
            .hasOmmers(false)
            .hasTransactions(false);

    if (validators.size() > 0) {
      blockOptions.setCoinbase(validators.get(0));
    }
    return new BlockDataGenerator().block(blockOptions);
  }
}
