/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.ibftmessagedata;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.Test;

public class NewRoundPayloadTest {

  private static final ConsensusRoundIdentifier ROUND_IDENTIFIER =
      new ConsensusRoundIdentifier(0x1234567890ABCDEFL, 0xFEDCBA98);

  @Test
  public void roundTripRlpWithNoRoundChangePayloads() {
    final Block block =
        TestHelpers.createProposalBlock(singletonList(AddressHelpers.ofValue(1)), 0);
    final ProposalPayload proposalPayload = new ProposalPayload(ROUND_IDENTIFIER, block);
    final Signature signature = Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0);
    final SignedData<ProposalPayload> proposalPayloadSignedData =
        SignedData.from(proposalPayload, signature);

    final RoundChangeCertificate roundChangeCertificate =
        new RoundChangeCertificate(Collections.emptyList());
    final NewRoundPayload expectedNewRoundPayload =
        new NewRoundPayload(ROUND_IDENTIFIER, roundChangeCertificate, proposalPayloadSignedData);
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    expectedNewRoundPayload.writeTo(rlpOut);

    final RLPInput rlpInput = RLP.input(rlpOut.encoded());
    final NewRoundPayload newRoundPayload = NewRoundPayload.readFrom(rlpInput);
    assertThat(newRoundPayload.getProposalPayload()).isEqualTo(proposalPayloadSignedData);
    assertThat(newRoundPayload.getRoundChangeCertificate()).isEqualTo(roundChangeCertificate);
    assertThat(newRoundPayload.getRoundIdentifier()).isEqualTo(ROUND_IDENTIFIER);
    assertThat(newRoundPayload.getMessageType()).isEqualTo(IbftV2.NEW_ROUND);
  }

  @Test
  public void roundTripRlpWithRoundChangePayloads() {
    final Block block =
        TestHelpers.createProposalBlock(singletonList(AddressHelpers.ofValue(1)), 0);
    final ProposalPayload proposalPayload = new ProposalPayload(ROUND_IDENTIFIER, block);
    final Signature signature = Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0);
    final SignedData<ProposalPayload> signedProposal = SignedData.from(proposalPayload, signature);

    final PreparePayload preparePayload =
        new PreparePayload(ROUND_IDENTIFIER, Hash.fromHexStringLenient("0x8523ba6e7c5f59ae87"));
    final SignedData<PreparePayload> signedPrepare = SignedData.from(preparePayload, signature);
    final PreparedCertificate preparedCert =
        new PreparedCertificate(signedProposal, Lists.newArrayList(signedPrepare));

    final RoundChangePayload roundChangePayload =
        new RoundChangePayload(ROUND_IDENTIFIER, Optional.of(preparedCert));
    SignedData<RoundChangePayload> signedRoundChange =
        SignedData.from(roundChangePayload, signature);

    final RoundChangeCertificate roundChangeCertificate =
        new RoundChangeCertificate(Lists.list(signedRoundChange));
    final NewRoundPayload expectedNewRoundPayload =
        new NewRoundPayload(ROUND_IDENTIFIER, roundChangeCertificate, signedProposal);
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    expectedNewRoundPayload.writeTo(rlpOut);

    final RLPInput rlpInput = RLP.input(rlpOut.encoded());
    final NewRoundPayload newRoundPayload = NewRoundPayload.readFrom(rlpInput);
    assertThat(newRoundPayload.getProposalPayload()).isEqualTo(signedProposal);
    assertThat(newRoundPayload.getRoundChangeCertificate()).isEqualTo(roundChangeCertificate);
    assertThat(newRoundPayload.getRoundIdentifier()).isEqualTo(ROUND_IDENTIFIER);
    assertThat(newRoundPayload.getMessageType()).isEqualTo(IbftV2.NEW_ROUND);
  }
}
