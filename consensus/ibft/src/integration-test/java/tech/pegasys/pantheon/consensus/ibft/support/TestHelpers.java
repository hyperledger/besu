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
package tech.pegasys.pantheon.consensus.ibft.support;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;

public class TestHelpers {

  public static SignedData<CommitPayload> createSignedCommentPayload(
      final Block block, final KeyPair signingKeyPair, final ConsensusRoundIdentifier roundId) {

    final IbftExtraData extraData = IbftExtraData.decode(block.getHeader().getExtraData());

    final Signature commitSeal =
        SECP256K1.sign(
            IbftBlockHashing.calculateDataHashForCommittedSeal(block.getHeader(), extraData),
            signingKeyPair);

    final MessageFactory messageFactory = new MessageFactory(signingKeyPair);

    return messageFactory.createSignedCommitPayload(roundId, block.getHash(), commitSeal);
  }
}
