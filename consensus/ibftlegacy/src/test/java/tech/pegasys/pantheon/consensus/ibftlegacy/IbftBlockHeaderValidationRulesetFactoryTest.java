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
package tech.pegasys.pantheon.consensus.ibftlegacy;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

public class IbftBlockHeaderValidationRulesetFactoryTest {

  private ProtocolContext<IbftContext> setupContextWithValidators(
      final Collection<Address> validators) {
    final IbftContext ibftContext = mock(IbftContext.class);
    final VoteTallyCache mockCache = mock(VoteTallyCache.class);
    final VoteTally mockVoteTally = mock(VoteTally.class);
    when(ibftContext.getVoteTallyCache()).thenReturn(mockCache);
    when(mockCache.getVoteTallyAfterBlock(any())).thenReturn(mockVoteTally);
    when(mockVoteTally.getValidators()).thenReturn(validators);

    return new ProtocolContext<>(null, null, ibftContext);
  }

  @Test
  public void ibftValidateHeaderPasses() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader = buildBlockHeader(1, proposerKeyPair, validators, null);
    final BlockHeader blockHeader = buildBlockHeader(2, proposerKeyPair, validators, parentHeader);

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5, TestClock.fixed());

    assertThat(
            validator.validateHeader(
                blockHeader,
                parentHeader,
                setupContextWithValidators(validators),
                HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void ibftValidateHeaderFails() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader = buildBlockHeader(1, proposerKeyPair, validators, null);
    final BlockHeader blockHeader = buildBlockHeader(2, proposerKeyPair, validators, null);

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5, TestClock.fixed());

    assertThat(
            validator.validateHeader(
                blockHeader,
                parentHeader,
                setupContextWithValidators(validators),
                HeaderValidationMode.FULL))
        .isFalse();
  }

  private BlockHeader buildBlockHeader(
      final long number,
      final KeyPair proposerKeyPair,
      final List<Address> validators,
      final BlockHeader parent) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();

    if (parent != null) {
      builder.parentHash(parent.getHash());
    }
    builder.number(number);
    builder.gasLimit(5000);
    builder.timestamp(6000 * number);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.ommersHash(Hash.EMPTY_LIST_HASH);
    builder.nonce(IbftLegacyBlockInterface.DROP_NONCE);
    builder.difficulty(UInt256.ONE);

    // Construct an extraData block
    final IbftExtraData initialIbftExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            emptyList(),
            Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 0),
            validators);

    builder.extraData(initialIbftExtraData.encode());
    final BlockHeader parentHeader = builder.buildHeader();
    final Hash proposerSealHash =
        IbftBlockHashing.calculateDataHashForProposerSeal(parentHeader, initialIbftExtraData);

    final Signature proposerSignature = SECP256K1.sign(proposerSealHash, proposerKeyPair);

    final IbftExtraData proposedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerSignature),
            proposerSignature,
            validators);

    final Hash headerHashForCommitters =
        IbftBlockHashing.calculateDataHashForCommittedSeal(parentHeader, proposedData);
    final Signature proposerAsCommitterSignature =
        SECP256K1.sign(headerHashForCommitters, proposerKeyPair);

    final IbftExtraData sealedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerAsCommitterSignature),
            proposerSignature,
            validators);

    builder.extraData(sealedData.encode());
    return builder.buildHeader();
  }
}
