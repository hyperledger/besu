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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class KeccakHasherTest {

  private static class KeccakSealableBlockHeader extends SealableBlockHeader {

    protected KeccakSealableBlockHeader(
        final Hash parentHash,
        final Hash ommersHash,
        final Address coinbase,
        final Hash stateRoot,
        final Hash transactionsRoot,
        final Hash receiptsRoot,
        final LogsBloomFilter logsBloom,
        final Difficulty difficulty,
        final long number,
        final long gasLimit,
        final long gasUsed,
        final long timestamp,
        final Bytes extraData,
        final Wei baseFee,
        final Bytes32 random) {
      super(
          parentHash,
          ommersHash,
          coinbase,
          stateRoot,
          transactionsRoot,
          receiptsRoot,
          logsBloom,
          difficulty,
          number,
          gasLimit,
          gasUsed,
          timestamp,
          extraData,
          baseFee,
          random);
    }
  }

  @Test
  public void testHasher() {

    PoWSolution solution =
        KeccakHasher.KECCAK256.hash(
            12345678L,
            42L,
            new EpochCalculator.DefaultEpochCalculator(),
            Bytes.fromHexString(
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"));

    assertThat(solution.getMixHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0xeffd292d6666dba4d6a4c221dd6d4b34b4ec3972a4cb0d944a8a8936cceca713"));
  }

  @Test
  public void testHasherFromBlock() {

    KeccakSealableBlockHeader header =
        new KeccakSealableBlockHeader(
            Hash.fromHexString(
                "0xad22d4d8f0e94032cb32e86027e0a5533d945ed95088264e91dd71e4fbaebeda"),
            Hash.fromHexString(
                "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"),
            Address.fromHexString("0x6A9ECfa04e99726eC105517AC7ae1aba550BeA6c"),
            Hash.fromHexString(
                "0x43e3325393fbc583a5a0b56e98073fb81e82d992b52406a79d662b690a4d2753"),
            Hash.fromHexString(
                "0x40c339f7715932ec591d8c0c588bacfaed9bddc7519a1e6e87cf45be639de810"),
            Hash.fromHexString(
                "0xeb1e644436f93be8a9938dfe598cb7fd729f9d201b6f7c0695bee883b3ea6a5b"),
            LogsBloomFilter.fromHexString(
                "0x0800012104000104c00400108000400000003000000040008400000002800100000a00000000000001010401040001000001002000000000020020080000240200000000012260010000084800420200040000100000030800802000112020001a200800020000000000500010100a00000000020401480000000010001048000011104800c002410000000010800000000014200040000400000000000000600020c00000004010080000000020100200000200000800001024c4000000080100004002004808000102920408810000002000008000000008000120400020008200d80000000010010000008028004000010000008220000200000100100800"),
            Difficulty.of(3963642329L),
            4156209L,
            8000000L,
            7987824L,
            1538483791L,
            Bytes.fromHexString("0xd88301080f846765746888676f312e31302e31856c696e7578"),
            null,
            null);

    PoWSolution result =
        KeccakHasher.KECCAK256.hash(
            Bytes.fromHexString("0xf245822d3412da7f").toLong(),
            4156209L,
            new EpochCalculator.DefaultEpochCalculator(),
            EthHash.hashHeader(header));

    assertThat(result.getMixHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0xd033f82e170ff16640e902fad569243c39bce9e4da948ccc298c541b34cd263b"));
  }
}
