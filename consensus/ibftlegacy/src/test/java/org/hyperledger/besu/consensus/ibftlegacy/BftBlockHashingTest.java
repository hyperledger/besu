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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class BftBlockHashingTest {

  private static final Address PROPOSER_IN_HEADER =
      Address.fromHexString("0x24defc2d149861d3d245749b81fe0e6b28e04f31");
  private static final List<Address> VALIDATORS_IN_HEADER =
      Arrays.asList(
          PROPOSER_IN_HEADER,
          Address.fromHexString("0x2a813d7db3de19b07f92268b6d4125ed295cbe00"),
          Address.fromHexString("0x3814f17bd4b7ce47ab8146684b3443c0a4b2fc2c"),
          Address.fromHexString("0xc332d0db1704d18f89a590e7586811e36d37ce04"));
  private static final List<Address> COMMITTERS_IN_HEADER =
      Arrays.asList(
          Address.fromHexString("0x3814f17bd4b7ce47ab8146684b3443c0a4b2fc2c"),
          PROPOSER_IN_HEADER,
          Address.fromHexString("0x2a813d7db3de19b07f92268b6d4125ed295cbe00"));
  private static final Hash KNOWN_BLOCK_HASH =
      Hash.fromHexString("0x0d60351c129af309fc8597c81358652d3d0f0e3141b5432888c4aae405ee0184");

  private final BlockHeader header = createKnownHeaderFromCapturedData();

  @Test
  public void recoverProposerAddressFromSeal() {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    final Address proposerAddress = IbftBlockHashing.recoverProposerAddress(header, ibftExtraData);

    assertThat(proposerAddress).isEqualTo(PROPOSER_IN_HEADER);
  }

  @Test
  public void readValidatorListFromExtraData() {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    Assertions.assertThat(ibftExtraData.getValidators()).isEqualTo(VALIDATORS_IN_HEADER);
  }

  @Test
  public void recoverCommitterAddresses() {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    final List<Address> committers =
        IbftBlockHashing.recoverCommitterAddresses(header, ibftExtraData);

    assertThat(committers).isEqualTo(COMMITTERS_IN_HEADER);
  }

  @Test
  public void calculateBlockHash() {
    assertThat(header.getHash()).isEqualTo(KNOWN_BLOCK_HASH);
  }

  /*
  Header information was extracted from a chain export (RLP) from a Quorum IBFT network.
  The hash was determined by looking at the parentHash of the subsequent block (i.e. not calculated
  by internal calculations, but rather by Quorum).
   */
  private BlockHeader createKnownHeaderFromCapturedData() {
    final BlockHeaderBuilder builder = new BlockHeaderBuilder();

    final String extraDataHexString =
        "0xd783010800846765746887676f312e392e32856c696e757800000"
            + "00000000000f90164f8549424defc2d149861d3d245749b81fe0e6b28e04f31942a813d7db3de19b07f92268b6d4"
            + "125ed295cbe00943814f17bd4b7ce47ab8146684b3443c0a4b2fc2c94c332d0db1704d18f89a590e7586811e36d3"
            + "7ce04b8417480a32e81936a40da3b8b730c28963a80011fdddb70470573675a11c7871873165e213b80b1ed5bf5a"
            + "59a31874baf1d6e83d55141f719ada73815c8712c4c6501f8c9b8417ba97752c9a3d14ae8c5f6f864c2808b816a0"
            + "d3ebef9a3b03c3cf9e31311baeb2e32609ccc99f13488e9e8ea192debf1c26f8c70c2332dfbb8456292fd9366110"
            + "0b841bbf2d1710a41bee7895dadbbbc92713ba9e74129bb665984f349950d7b5275303db99b12ea3483430079dd5"
            + "d90bcc3962f72217863725f6cd72ab5c10c8c540001b8415df74d9bf9687a3da10a4660cfd6fd6739df59db5535f"
            + "a3a7c58382ec587f4fe581089a9e3cd4b8c3b77eeabdd756f1f34ffb990cfd47e81bb205bd10be619d001";

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
    builder.extraData(Bytes.fromHexString(extraDataHexString));
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.nonce(0);
    builder.blockHeaderFunctions(new LegacyIbftBlockHeaderFunctions());

    return builder.buildBlockHeader();
  }
}
