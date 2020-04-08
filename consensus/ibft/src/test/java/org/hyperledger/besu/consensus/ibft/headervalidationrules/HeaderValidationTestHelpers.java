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
package org.hyperledger.besu.consensus.ibft.headervalidationrules;

import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.consensus.ibft.IbftExtraDataFixture;
import org.hyperledger.besu.consensus.ibft.Vote;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class HeaderValidationTestHelpers {

  public static BlockHeader createProposedBlockHeader(
      final List<Address> validators,
      final List<NodeKey> committerNodeKeys,
      final boolean useDifferentRoundNumbersForCommittedSeals) {
    final int BASE_ROUND_NUMBER = 5;
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all

    final BlockHeader header = builder.buildHeader();

    final IbftExtraData ibftExtraData =
        IbftExtraDataFixture.createExtraData(
            header,
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            committerNodeKeys,
            BASE_ROUND_NUMBER,
            useDifferentRoundNumbersForCommittedSeals);

    builder.extraData(ibftExtraData.encode());
    return builder.buildHeader();
  }
}
