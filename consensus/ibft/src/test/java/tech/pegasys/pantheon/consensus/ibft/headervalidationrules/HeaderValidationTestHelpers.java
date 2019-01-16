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
package tech.pegasys.pantheon.consensus.ibft.headervalidationrules;

import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraDataFixture;
import tech.pegasys.pantheon.consensus.ibft.Vote;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

public class HeaderValidationTestHelpers {

  public static BlockHeader createProposedBlockHeader(
      final List<Address> validators,
      final List<KeyPair> committerKeyPairs,
      final boolean useDifferentRoundNumbersForCommittedSeals) {
    final int BASE_ROUND_NUMBER = 5;
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.number(1); // must NOT be block 0, as that should not contain seals at all

    final BlockHeader header = builder.buildHeader();

    final IbftExtraData ibftExtraData =
        IbftExtraDataFixture.createExtraData(
            header,
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            Optional.of(Vote.authVote(Address.fromHexString("1"))),
            validators,
            committerKeyPairs,
            BASE_ROUND_NUMBER,
            useDifferentRoundNumbersForCommittedSeals);

    builder.extraData(ibftExtraData.encode());
    return builder.buildHeader();
  }
}
