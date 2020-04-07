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
package org.hyperledger.besu.consensus.ibft;

import static java.util.Collections.singletonList;

import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.Hash;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TestHelpers {

  public static ConsensusRoundIdentifier createFrom(
      final ConsensusRoundIdentifier initial, final int offSetSequence, final int offSetRound) {
    return new ConsensusRoundIdentifier(
        initial.getSequenceNumber() + offSetSequence, initial.getRoundNumber() + offSetRound);
  }

  public static Block createProposalBlock(
      final List<Address> validators, final ConsensusRoundIdentifier roundId) {
    final Bytes extraData =
        new IbftExtraData(
                Bytes.wrap(new byte[32]),
                Collections.emptyList(),
                Optional.empty(),
                roundId.getRoundNumber(),
                validators)
            .encode();
    final BlockOptions blockOptions =
        BlockOptions.create()
            .setExtraData(extraData)
            .setBlockNumber(roundId.getSequenceNumber())
            .setBlockHeaderFunctions(IbftBlockHeaderFunctions.forCommittedSeal());
    return new BlockDataGenerator().block(blockOptions);
  }

  public static Proposal createSignedProposalPayload(final NodeKey signerNodeKey) {
    return createSignedProposalPayloadWithRound(signerNodeKey, 0xFEDCBA98);
  }

  public static Proposal createSignedProposalPayloadWithRound(
      final NodeKey signerNodeKey, final int round) {
    final MessageFactory messageFactory = new MessageFactory(signerNodeKey);
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(0x1234567890ABCDEFL, round);
    final Block block =
        TestHelpers.createProposalBlock(singletonList(AddressHelpers.ofValue(1)), roundIdentifier);
    return messageFactory.createProposal(roundIdentifier, block, Optional.empty());
  }

  public static Prepare createSignedPreparePayload(final NodeKey signerKeys) {
    final MessageFactory messageFactory = new MessageFactory(signerKeys);
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(0x1234567890ABCDEFL, 0xFEDCBA98);
    return messageFactory.createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
  }

  public static Commit createSignedCommitPayload(final NodeKey signerKeys) {
    final MessageFactory messageFactory = new MessageFactory(signerKeys);
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(0x1234567890ABCDEFL, 0xFEDCBA98);
    return messageFactory.createCommit(
        roundIdentifier,
        Hash.fromHexStringLenient("0"),
        Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0));
  }

  public static RoundChange createSignedRoundChangePayload(final NodeKey signerKeys) {
    final MessageFactory messageFactory = new MessageFactory(signerKeys);
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(0x1234567890ABCDEFL, 0xFEDCBA98);
    return messageFactory.createRoundChange(roundIdentifier, Optional.empty());
  }
}
