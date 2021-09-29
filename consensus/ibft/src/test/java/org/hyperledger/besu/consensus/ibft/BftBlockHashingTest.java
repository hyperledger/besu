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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Java6Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class BftBlockHashingTest {

  private static final List<NodeKey> COMMITTERS_NODE_KEYS = committersNodeKeys();
  private static final List<Address> VALIDATORS =
      Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
  private static final Optional<Vote> VOTE = Optional.of(Vote.authVote(Address.fromHexString("3")));
  private static final int ROUND = 0x00FEDCBA;
  private static final Bytes VANITY_DATA = vanityBytes();

  private final IbftExtraDataCodec bftExtraDataEncoder = new IbftExtraDataCodec();
  private final BftBlockHashing bftBlockHashing = new BftBlockHashing(bftExtraDataEncoder);
  private final BlockHeader headerToBeHashed = headerToBeHashed();
  private final Hash EXPECTED_HEADER_HASH = expectedHeaderHash();

  @Test
  public void testCalculateHashOfIbft2BlockOnchain() {
    Hash actualHeaderHash = bftBlockHashing.calculateHashOfBftBlockOnchain(headerToBeHashed);
    assertThat(actualHeaderHash).isEqualTo(EXPECTED_HEADER_HASH);
  }

  @Test
  public void testRecoverCommitterAddresses() {
    List<Address> actualCommitterAddresses =
        bftBlockHashing.recoverCommitterAddresses(
            headerToBeHashed, bftExtraDataEncoder.decode(headerToBeHashed));

    List<Address> expectedCommitterAddresses =
        COMMITTERS_NODE_KEYS.stream()
            .map(nodeKey -> Util.publicKeyToAddress(nodeKey.getPublicKey()))
            .collect(Collectors.toList());

    assertThat(actualCommitterAddresses).isEqualTo(expectedCommitterAddresses);
  }

  @Test
  public void testCalculateDataHashForCommittedSeal() {
    Hash dataHahsForCommittedSeal =
        bftBlockHashing.calculateDataHashForCommittedSeal(
            headerToBeHashed, bftExtraDataEncoder.decode(headerToBeHashed));

    BlockHeaderBuilder builder = setHeaderFieldsExceptForExtraData();

    List<SECPSignature> commitSeals =
        COMMITTERS_NODE_KEYS.stream()
            .map(nodeKey -> nodeKey.sign(dataHahsForCommittedSeal))
            .collect(Collectors.toList());

    final BftExtraData extraDataWithCommitSeals =
        new BftExtraData(VANITY_DATA, commitSeals, VOTE, ROUND, VALIDATORS);

    builder.extraData(bftExtraDataEncoder.encode(extraDataWithCommitSeals));
    BlockHeader actualHeader = builder.buildBlockHeader();
    assertThat(actualHeader).isEqualTo(headerToBeHashed);
  }

  private static List<NodeKey> committersNodeKeys() {
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

    return IntStream.rangeClosed(1, 4)
        .mapToObj(
            i ->
                NodeKeyUtils.createFrom(
                    (signatureAlgorithm.createKeyPair(
                        signatureAlgorithm.createPrivateKey(UInt256.valueOf(i))))))
        .collect(Collectors.toList());
  }

  private static BlockHeaderBuilder setHeaderFieldsExceptForExtraData() {
    final BlockHeaderBuilder builder = new BlockHeaderBuilder();
    builder.parentHash(
        Hash.fromHexString("0xa7762d3307dbf2ae6a1ae1b09cf61c7603722b2379731b6b90409cdb8c8288a0"));
    builder.ommersHash(
        Hash.fromHexString("0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"));
    builder.coinbase(Address.fromHexString("0x0000000000000000000000000000000000000000"));
    builder.stateRoot(
        Hash.fromHexString("0xca07595b82f908822971b7e848398e3395e59ee52565c7ef3603df1a1fa7bc80"));
    builder.transactionsRoot(
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"));
    builder.receiptsRoot(
        Hash.fromHexString("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"));
    builder.logsBloom(
        LogsBloomFilter.fromHexString(
            "0x000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                + "0000"));
    builder.difficulty(Difficulty.ONE);
    builder.number(1);
    builder.gasLimit(4704588);
    builder.gasUsed(0);
    builder.timestamp(1530674616);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.nonce(0);
    builder.blockHeaderFunctions(BftBlockHeaderFunctions.forOnchainBlock(new IbftExtraDataCodec()));
    return builder;
  }

  private static Bytes vanityBytes() {
    final byte[] vanity_bytes = new byte[32];
    for (int i = 0; i < vanity_bytes.length; i++) {
      vanity_bytes[i] = (byte) i;
    }
    return Bytes.wrap(vanity_bytes);
  }

  private BlockHeader headerToBeHashed() {
    BlockHeaderBuilder builder = setHeaderFieldsExceptForExtraData();

    builder.extraData(
        bftExtraDataEncoder.encodeWithoutCommitSeals(
            new BftExtraData(VANITY_DATA, emptyList(), VOTE, ROUND, VALIDATORS)));

    BytesValueRLPOutput rlpForHeaderFroCommittersSigning = new BytesValueRLPOutput();
    builder.buildBlockHeader().writeTo(rlpForHeaderFroCommittersSigning);

    List<SECPSignature> commitSeals =
        COMMITTERS_NODE_KEYS.stream()
            .map(nodeKey -> nodeKey.sign(Hash.hash(rlpForHeaderFroCommittersSigning.encoded())))
            .collect(Collectors.toList());

    BftExtraData extraDataWithCommitSeals =
        new BftExtraData(VANITY_DATA, commitSeals, VOTE, ROUND, VALIDATORS);

    builder.extraData(bftExtraDataEncoder.encode(extraDataWithCommitSeals));
    return builder.buildBlockHeader();
  }

  private Hash expectedHeaderHash() {
    BlockHeaderBuilder builder = setHeaderFieldsExceptForExtraData();

    builder.extraData(
        bftExtraDataEncoder.encodeWithoutCommitSealsAndRoundNumber(
            new BftExtraData(VANITY_DATA, emptyList(), VOTE, 0, VALIDATORS)));

    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    builder.buildBlockHeader().writeTo(rlpOutput);

    return Hash.hash(rlpOutput.encoded());
  }
}
