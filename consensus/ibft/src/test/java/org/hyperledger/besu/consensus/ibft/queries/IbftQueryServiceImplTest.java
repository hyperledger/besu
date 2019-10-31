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
package org.hyperledger.besu.consensus.ibft.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibft.IbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.ibft.IbftBlockInterface;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.NonBesuBlockHeader;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.plugin.services.query.IbftQueryService;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class IbftQueryServiceImplTest {

  private Blockchain blockchain = mock(Blockchain.class);

  private final List<KeyPair> validatorKeys =
      Lists.newArrayList(KeyPair.generate(), KeyPair.generate());

  private final List<KeyPair> signingKeys = Lists.newArrayList(validatorKeys.get(0));
  private final int ROUND_NUMBER_IN_BLOCK = 5;

  private IbftExtraData signedExtraData;
  private BlockHeader blockHeader;

  @Before
  public void setup() {

    final Collection<Address> validators =
        validatorKeys.stream()
            .map(validatorKeys -> Util.publicKeyToAddress(validatorKeys.getPublicKey()))
            .collect(Collectors.toList());

    final IbftExtraData unsignedExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            Collections.emptyList(),
            Optional.empty(),
            ROUND_NUMBER_IN_BLOCK,
            validators);

    final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
    blockHeaderTestFixture.number(1); // can't be genesis block (due to extradata serialisation)
    blockHeaderTestFixture.blockHeaderFunctions(IbftBlockHeaderFunctions.forOnChainBlock());
    blockHeaderTestFixture.extraData(unsignedExtraData.encode());

    final BlockHeader unsignedBlockHeader = blockHeaderTestFixture.buildHeader();

    final Collection<Signature> validatorSignatures =
        signingKeys.stream()
            .map(
                keyPair ->
                    SECP256K1.sign(
                        IbftBlockHashing.calculateDataHashForCommittedSeal(unsignedBlockHeader),
                        keyPair))
            .collect(Collectors.toList());

    signedExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            validatorSignatures,
            Optional.empty(),
            ROUND_NUMBER_IN_BLOCK,
            validators);

    blockHeaderTestFixture.extraData(signedExtraData.encode());
    blockHeader = blockHeaderTestFixture.buildHeader();
  }

  @Test
  public void roundNumberFromBlockIsReturned() {
    final IbftQueryService service = new IbftQueryServiceImpl(new IbftBlockInterface(), blockchain);

    assertThat(service.getRoundNumberFrom(blockHeader)).isEqualTo(ROUND_NUMBER_IN_BLOCK);
  }

  @Test
  public void getRoundNumberThrowsIfBlockIsNotOnTheChain() {
    final NonBesuBlockHeader header =
        new NonBesuBlockHeader(blockHeader.getHash(), blockHeader.getExtraData());
    when(blockchain.getBlockHeader(blockHeader.getHash())).thenReturn(Optional.empty());

    final IbftQueryService service = new IbftQueryServiceImpl(new IbftBlockInterface(), blockchain);
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> service.getRoundNumberFrom(header));
  }

  @Test
  public void getSignersReturnsAddressesOfSignersInBlock() {
    final IbftQueryService service = new IbftQueryServiceImpl(new IbftBlockInterface(), blockchain);

    final List<Address> signers =
        signingKeys.stream()
            .map(keyPair -> Util.publicKeyToAddress(keyPair.getPublicKey()))
            .collect(Collectors.toList());

    assertThat(service.getSignersFrom(blockHeader)).containsExactlyElementsOf(signers);
  }

  @Test
  public void getSignersTheowsIfBlockIsNotOnTheChain() {
    final NonBesuBlockHeader header =
        new NonBesuBlockHeader(blockHeader.getHash(), blockHeader.getExtraData());
    when(blockchain.getBlockHeader(blockHeader.getHash())).thenReturn(Optional.empty());

    final IbftQueryService service = new IbftQueryServiceImpl(new IbftBlockInterface(), blockchain);
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> service.getSignersFrom(header));
  }
}
