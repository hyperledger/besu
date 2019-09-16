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
package org.hyperledger.besu.consensus.ibft.payload;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.TestHelpers;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.assertj.core.util.Lists;
import org.junit.Test;

public class PreparedCertificateTest {

  private static final ConsensusRoundIdentifier ROUND_IDENTIFIER =
      new ConsensusRoundIdentifier(0x1234567890ABCDEFL, 0xFEDCBA98);

  @Test
  public void roundTripRlpWithNoPreparePayloads() {
    final SignedData<ProposalPayload> signedProposalPayload = signedProposal();
    final List<SignedData<PreparePayload>> preparePayloads = Collections.emptyList();

    final PreparedCertificate preparedCert =
        new PreparedCertificate(signedProposalPayload, preparePayloads);
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    preparedCert.writeTo(rlpOut);

    final RLPInput rlpInput = RLP.input(rlpOut.encoded());
    PreparedCertificate actualPreparedCert = PreparedCertificate.readFrom(rlpInput);
    assertThat(actualPreparedCert.getPreparePayloads())
        .isEqualTo(preparedCert.getPreparePayloads());
    assertThat(actualPreparedCert.getProposalPayload())
        .isEqualTo(preparedCert.getProposalPayload());
  }

  @Test
  public void roundTripRlpWithPreparePayload() {
    final SignedData<ProposalPayload> signedProposalPayload = signedProposal();
    final PreparePayload preparePayload =
        new PreparePayload(ROUND_IDENTIFIER, Hash.fromHexStringLenient("0x8523ba6e7c5f59ae87"));
    final Signature signature = Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0);
    final SignedData<PreparePayload> signedPrepare = SignedData.from(preparePayload, signature);

    final PreparedCertificate preparedCert =
        new PreparedCertificate(signedProposalPayload, Lists.newArrayList(signedPrepare));
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    preparedCert.writeTo(rlpOut);

    final RLPInput rlpInput = RLP.input(rlpOut.encoded());
    PreparedCertificate actualPreparedCert = PreparedCertificate.readFrom(rlpInput);
    assertThat(actualPreparedCert.getPreparePayloads())
        .isEqualTo(preparedCert.getPreparePayloads());
    assertThat(actualPreparedCert.getProposalPayload())
        .isEqualTo(preparedCert.getProposalPayload());
  }

  private SignedData<ProposalPayload> signedProposal() {
    final Block block =
        TestHelpers.createProposalBlock(singletonList(AddressHelpers.ofValue(1)), ROUND_IDENTIFIER);
    final ProposalPayload proposalPayload = new ProposalPayload(ROUND_IDENTIFIER, block.getHash());
    final Signature signature = Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0);
    return SignedData.from(proposalPayload, signature);
  }
}
