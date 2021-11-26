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
package org.hyperledger.besu.consensus.ibftlegacy;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.validator.VoteType.ADD;
import static org.hyperledger.besu.consensus.common.validator.VoteType.DROP;

import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class IbftLegacyBlockInterfaceTest {

  private static final KeyPair proposerKeys =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();
  private static final Address proposerAddress =
      Util.publicKeyToAddress(proposerKeys.getPublicKey());
  private static final List<Address> validatorList = singletonList(proposerAddress);

  private final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
  private final IbftLegacyBlockInterface blockInterface = new IbftLegacyBlockInterface();
  private final BlockHeaderBuilder builder =
      BlockHeaderBuilder.fromHeader(headerBuilder.buildHeader())
          .blockHeaderFunctions(new LegacyIbftBlockHeaderFunctions());

  @Before
  public void setup() {
    // must set "number" to ensure extradata is correctly deserialised during hashing.
    headerBuilder.coinbase(AddressHelpers.ofValue(0)).number(1);
  }

  @Test
  public void headerWithZeroCoinbaseReturnsAnEmptyVote() {
    assertThat(blockInterface.extractVoteFromHeader(headerBuilder.buildHeader())).isEmpty();
  }

  @Test
  public void headerWithNonceOfZeroReportsDropVote() {
    headerBuilder.nonce(0x0L).coinbase(AddressHelpers.ofValue(2));
    final BlockHeader header =
        TestHelpers.createIbftSignedBlockHeader(headerBuilder, proposerKeys, validatorList);
    final Optional<ValidatorVote> extractedVote = blockInterface.extractVoteFromHeader(header);

    assertThat(extractedVote)
        .contains(new ValidatorVote(DROP, proposerAddress, header.getCoinbase()));
  }

  @Test
  public void headerWithNonceOfMaxLongReportsAddVote() {
    headerBuilder.nonce(0xFFFFFFFFFFFFFFFFL).coinbase(AddressHelpers.ofValue(2));

    final BlockHeader header =
        TestHelpers.createIbftSignedBlockHeader(headerBuilder, proposerKeys, validatorList);
    final Optional<ValidatorVote> extractedVote = blockInterface.extractVoteFromHeader(header);

    assertThat(extractedVote)
        .contains(new ValidatorVote(ADD, proposerAddress, header.getCoinbase()));
  }

  @Test
  public void blendingAddVoteToHeaderResultsInHeaderWithNonceOfMaxLong() {
    final ValidatorVote vote =
        new ValidatorVote(ADD, AddressHelpers.ofValue(1), AddressHelpers.ofValue(2));
    final BlockHeaderBuilder builderWithVote =
        IbftLegacyBlockInterface.insertVoteToHeaderBuilder(builder, Optional.of(vote));

    final BlockHeader header = builderWithVote.buildBlockHeader();

    assertThat(header.getCoinbase()).isEqualTo(vote.getRecipient());
    assertThat(header.getNonce()).isEqualTo(0xFFFFFFFFFFFFFFFFL);
  }

  @Test
  public void blendingDropVoteToHeaderResultsInHeaderWithNonceOfZero() {
    final ValidatorVote vote =
        new ValidatorVote(DROP, AddressHelpers.ofValue(1), AddressHelpers.ofValue(2));
    final BlockHeaderBuilder builderWithVote =
        IbftLegacyBlockInterface.insertVoteToHeaderBuilder(builder, Optional.of(vote));

    final BlockHeader header = builderWithVote.buildBlockHeader();

    assertThat(header.getCoinbase()).isEqualTo(vote.getRecipient());
    assertThat(header.getNonce()).isEqualTo(0x0L);
  }

  @Test
  public void nonVoteBlendedIntoHeaderResultsInACoinbaseOfZero() {
    final BlockHeaderBuilder builderWithVote =
        IbftLegacyBlockInterface.insertVoteToHeaderBuilder(builder, Optional.empty());

    final BlockHeader header = builderWithVote.buildBlockHeader();

    assertThat(header.getCoinbase()).isEqualTo(AddressHelpers.ofValue(0));
    assertThat(header.getNonce()).isEqualTo(0x0L);
  }

  @Test
  public void extractsValidatorsFromHeader() {
    final BlockHeader header =
        TestHelpers.createIbftSignedBlockHeader(headerBuilder, proposerKeys, validatorList);

    final Collection<Address> extractedValidators = blockInterface.validatorsInBlock(header);

    assertThat(extractedValidators).isEqualTo(validatorList);
  }
}
