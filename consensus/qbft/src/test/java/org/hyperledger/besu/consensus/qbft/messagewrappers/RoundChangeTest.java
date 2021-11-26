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
package org.hyperledger.besu.consensus.qbft.messagewrappers;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class RoundChangeTest {
  private static final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();
  private static final BftExtraData extraData =
      new BftExtraData(
          Bytes32.ZERO, Collections.emptyList(), Optional.empty(), 1, Collections.emptyList());

  private static final Block BLOCK =
      new Block(
          new BlockHeaderTestFixture()
              .extraData(new QbftExtraDataCodec().encode(extraData))
              .buildHeader(),
          new BlockBody(Collections.emptyList(), Collections.emptyList()));

  @Test
  public void canRoundTripARoundChangeMessage() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final Address addr = Util.publicKeyToAddress(nodeKey.getPublicKey());

    final RoundChangePayload payload =
        new RoundChangePayload(
            new ConsensusRoundIdentifier(1, 1),
            Optional.of(new PreparedRoundMetadata(BLOCK.getHash(), 0)));

    final SignedData<RoundChangePayload> signedRoundChangePayload =
        SignedData.create(payload, nodeKey.sign(payload.hashForSignature()));

    final PreparePayload preparePayload =
        new PreparePayload(new ConsensusRoundIdentifier(1, 0), BLOCK.getHash());
    final SignedData<PreparePayload> signedPreparePayload =
        SignedData.create(preparePayload, nodeKey.sign(preparePayload.hashForSignature()));

    final RoundChange roundChange =
        new RoundChange(
            signedRoundChangePayload, Optional.of(BLOCK), List.of(signedPreparePayload));

    final RoundChange decodedRoundChange =
        RoundChange.decode(roundChange.encode(), bftExtraDataCodec);

    assertThat(decodedRoundChange.getMessageType()).isEqualTo(QbftV1.ROUND_CHANGE);
    assertThat(decodedRoundChange.getAuthor()).isEqualTo(addr);
    assertThat(decodedRoundChange.getSignedPayload())
        .isEqualToComparingFieldByField(signedRoundChangePayload);
    assertThat(decodedRoundChange.getProposedBlock()).isNotEmpty();
    assertThat(decodedRoundChange.getProposedBlock().get()).isEqualToComparingFieldByField(BLOCK);
    assertThat(decodedRoundChange.getPrepares()).hasSize(1);
    assertThat(decodedRoundChange.getPrepares().get(0))
        .isEqualToComparingFieldByField(signedPreparePayload);
  }

  @Test
  public void canRoundTripEmptyPreparedRoundAndPreparedList() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final Address addr = Util.publicKeyToAddress(nodeKey.getPublicKey());

    final RoundChangePayload payload =
        new RoundChangePayload(new ConsensusRoundIdentifier(1, 1), Optional.empty());

    final SignedData<RoundChangePayload> signedRoundChangePayload =
        SignedData.create(payload, nodeKey.sign(payload.hashForSignature()));

    final RoundChange roundChange =
        new RoundChange(signedRoundChangePayload, Optional.empty(), Collections.emptyList());

    final RoundChange decodedRoundChange =
        RoundChange.decode(roundChange.encode(), bftExtraDataCodec);

    assertThat(decodedRoundChange.getMessageType()).isEqualTo(QbftV1.ROUND_CHANGE);
    assertThat(decodedRoundChange.getAuthor()).isEqualTo(addr);
    assertThat(decodedRoundChange.getSignedPayload())
        .isEqualToComparingFieldByField(signedRoundChangePayload);
    assertThat(decodedRoundChange.getProposedBlock()).isEmpty();
    assertThat(decodedRoundChange.getPrepares()).isEmpty();
  }
}
