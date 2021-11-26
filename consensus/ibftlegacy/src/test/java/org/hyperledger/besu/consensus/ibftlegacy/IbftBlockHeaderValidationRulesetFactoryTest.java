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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.ibft.IbftLegacyContext;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.mockito.Mockito;

public class IbftBlockHeaderValidationRulesetFactoryTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private ProtocolContext setupContextWithValidators(final Collection<Address> validators) {
    final IbftLegacyContext bftContext = mock(IbftLegacyContext.class);
    final ValidatorProvider mockValidatorProvider = mock(ValidatorProvider.class);
    when(bftContext.getValidatorProvider()).thenReturn(mockValidatorProvider);
    when(mockValidatorProvider.getValidatorsAfterBlock(any())).thenReturn(validators);
    when(bftContext.as(Mockito.any())).thenReturn(bftContext);
    return new ProtocolContext(null, null, bftContext);
  }

  @Test
  public void ibftValidateHeaderPasses() {
    final KeyPair proposerKeyPair = SIGNATURE_ALGORITHM.get().generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader = buildBlockHeader(1, proposerKeyPair, validators, null);
    final BlockHeader blockHeader = buildBlockHeader(2, proposerKeyPair, validators, parentHeader);

    final BlockHeaderValidator validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5, 0).build();

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
    final KeyPair proposerKeyPair = SIGNATURE_ALGORITHM.get().generateKeyPair();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader = buildBlockHeader(1, proposerKeyPair, validators, null);
    final BlockHeader blockHeader = buildBlockHeader(2, proposerKeyPair, validators, null);

    final BlockHeaderValidator validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5, 0).build();

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
    builder.difficulty(Difficulty.ONE);

    // Construct an extraData block
    final IbftExtraData initialIbftExtraData =
        new IbftExtraData(
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            emptyList(),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0),
            validators);

    builder.extraData(initialIbftExtraData.encode());
    final BlockHeader parentHeader = builder.buildHeader();
    final Hash proposerSealHash =
        IbftBlockHashing.calculateDataHashForProposerSeal(parentHeader, initialIbftExtraData);

    final SECPSignature proposerSignature =
        SIGNATURE_ALGORITHM.get().sign(proposerSealHash, proposerKeyPair);

    final IbftExtraData proposedData =
        new IbftExtraData(
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerSignature),
            proposerSignature,
            validators);

    final Hash headerHashForCommitters =
        IbftBlockHashing.calculateDataHashForCommittedSeal(parentHeader, proposedData);
    final SECPSignature proposerAsCommitterSignature =
        SIGNATURE_ALGORITHM.get().sign(headerHashForCommitters, proposerKeyPair);

    final IbftExtraData sealedData =
        new IbftExtraData(
            Bytes.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerAsCommitterSignature),
            proposerSignature,
            validators);

    builder.extraData(sealedData.encode());
    return builder.buildHeader();
  }
}
