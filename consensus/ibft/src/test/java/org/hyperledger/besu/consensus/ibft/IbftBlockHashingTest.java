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
package org.hyperledger.besu.consensus.ibft;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Java6Assertions.assertThat;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.PrivateKey;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

public class IbftBlockHashingTest {

  private static final List<KeyPair> COMMITTERS_KEY_PAIRS = committersKeyPairs();
  private static final List<Address> VALIDATORS =
      Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
  private static final Optional<Vote> VOTE = Optional.of(Vote.authVote(Address.fromHexString("3")));
  private static final int ROUND = 0x00FEDCBA;
  private static final BytesValue VANITY_DATA = vanityBytes();

  private static final BlockHeader HEADER_TO_BE_HASHED = headerToBeHashed();
  private static final Hash EXPECTED_HEADER_HASH = expectedHeaderHash();

  @Test
  public void testCalculateHashOfIbft2BlockOnChain() {
    Hash actualHeaderHash = IbftBlockHashing.calculateHashOfIbftBlockOnChain(HEADER_TO_BE_HASHED);
    assertThat(actualHeaderHash).isEqualTo(EXPECTED_HEADER_HASH);
  }

  @Test
  public void testRecoverCommitterAddresses() {
    List<Address> actualCommitterAddresses =
        IbftBlockHashing.recoverCommitterAddresses(
            HEADER_TO_BE_HASHED, IbftExtraData.decode(HEADER_TO_BE_HASHED));

    List<Address> expectedCommitterAddresses =
        COMMITTERS_KEY_PAIRS.stream()
            .map(keyPair -> Util.publicKeyToAddress(keyPair.getPublicKey()))
            .collect(Collectors.toList());

    assertThat(actualCommitterAddresses).isEqualTo(expectedCommitterAddresses);
  }

  @Test
  public void testCalculateDataHashForCommittedSeal() {
    Hash dataHahsForCommittedSeal =
        IbftBlockHashing.calculateDataHashForCommittedSeal(
            HEADER_TO_BE_HASHED, IbftExtraData.decode(HEADER_TO_BE_HASHED));

    BlockHeaderBuilder builder = setHeaderFieldsExceptForExtraData();

    List<Signature> commitSeals =
        COMMITTERS_KEY_PAIRS.stream()
            .map(keyPair -> SECP256K1.sign(dataHahsForCommittedSeal, keyPair))
            .collect(Collectors.toList());

    IbftExtraData extraDataWithCommitSeals =
        new IbftExtraData(VANITY_DATA, commitSeals, VOTE, ROUND, VALIDATORS);

    builder.extraData(extraDataWithCommitSeals.encode());
    BlockHeader actualHeader = builder.buildBlockHeader();
    assertThat(actualHeader).isEqualTo(HEADER_TO_BE_HASHED);
  }

  private static List<KeyPair> committersKeyPairs() {
    return IntStream.rangeClosed(1, 4)
        .mapToObj(i -> KeyPair.create(PrivateKey.create(UInt256.of(i).getBytes())))
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
    builder.difficulty(UInt256.ONE);
    builder.number(1);
    builder.gasLimit(4704588);
    builder.gasUsed(0);
    builder.timestamp(1530674616);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.nonce(0);
    builder.blockHeaderFunctions(IbftBlockHeaderFunctions.forOnChainBlock());
    return builder;
  }

  private static BytesValue vanityBytes() {
    final byte[] vanity_bytes = new byte[32];
    for (int i = 0; i < vanity_bytes.length; i++) {
      vanity_bytes[i] = (byte) i;
    }
    return BytesValue.wrap(vanity_bytes);
  }

  private static BlockHeader headerToBeHashed() {
    BlockHeaderBuilder builder = setHeaderFieldsExceptForExtraData();

    builder.extraData(
        new IbftExtraData(VANITY_DATA, emptyList(), VOTE, ROUND, VALIDATORS)
            .encodeWithoutCommitSeals());

    BytesValueRLPOutput rlpForHeaderFroCommittersSigning = new BytesValueRLPOutput();
    builder.buildBlockHeader().writeTo(rlpForHeaderFroCommittersSigning);

    List<Signature> commitSeals =
        COMMITTERS_KEY_PAIRS.stream()
            .map(
                keyPair ->
                    SECP256K1.sign(Hash.hash(rlpForHeaderFroCommittersSigning.encoded()), keyPair))
            .collect(Collectors.toList());

    IbftExtraData extraDataWithCommitSeals =
        new IbftExtraData(VANITY_DATA, commitSeals, VOTE, ROUND, VALIDATORS);

    builder.extraData(extraDataWithCommitSeals.encode());
    return builder.buildBlockHeader();
  }

  private static Hash expectedHeaderHash() {
    BlockHeaderBuilder builder = setHeaderFieldsExceptForExtraData();

    builder.extraData(
        new IbftExtraData(VANITY_DATA, emptyList(), VOTE, 0, VALIDATORS)
            .encodeWithoutCommitSealsAndRoundNumber());

    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    builder.buildBlockHeader().writeTo(rlpOutput);

    return Hash.hash(rlpOutput.encoded());
  }
}
