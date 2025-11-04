/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BlockBodyEncoderTest {
  @Test
  public void testEncode() {
    List<Transaction> transactions =
        List.of(
            Transaction.builder()
                .nonce(1)
                .gasPrice(Wei.of(2))
                .gasLimit(3)
                .to(Address.fromHexString("04"))
                .value(Wei.of(5))
                .payload(Bytes.fromHexStringLenient("06"))
                .v(BigInteger.valueOf(7))
                .signature(
                    SECPSignature.create(
                        BigInteger.valueOf(8),
                        BigInteger.valueOf(9),
                        (byte) 0x00,
                        BigInteger.valueOf(10)))
                .build());
    List<BlockHeader> ommers = List.of(new BlockHeaderTestFixture().buildHeader());
    Optional<List<Withdrawal>> withdrawals =
        Optional.of(
            List.of(
                new Withdrawal(
                    UInt64.valueOf(1),
                    UInt64.valueOf(2),
                    Address.fromHexString("03"),
                    GWei.of(4))));
    Optional<BlockAccessList> blockAccessList = Optional.of(BlockAccessList.builder().build());
    BlockBody blockBody = new BlockBody(transactions, ommers, withdrawals, blockAccessList);

    BlockBodyEncoder blockBodyEncoder = new BlockBodyEncoder();
    Bytes actualRlp = blockBodyEncoder.encode(blockBody);

    String expectedRlpHex =
        "0xdedd01020394000000000000000000000000000000000000000405061b0809f901f0f901eda0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347940000000000000000000000000000000000000001a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000808080808080a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470880000000000000000d9d8010294000000000000000000000000000000000000000304c0";
    Assertions.assertEquals(expectedRlpHex, actualRlp.toHexString());
  }

  @Test
  public void testEncodeWrapped() {
    List<Transaction> transactions =
        List.of(
            Transaction.builder()
                .nonce(1)
                .gasPrice(Wei.of(2))
                .gasLimit(3)
                .to(Address.fromHexString("04"))
                .value(Wei.of(5))
                .payload(Bytes.fromHexStringLenient("06"))
                .v(BigInteger.valueOf(7))
                .signature(
                    SECPSignature.create(
                        BigInteger.valueOf(8),
                        BigInteger.valueOf(9),
                        (byte) 0x00,
                        BigInteger.valueOf(10)))
                .build());
    List<BlockHeader> ommers = List.of(new BlockHeaderTestFixture().buildHeader());
    Optional<List<Withdrawal>> withdrawals =
        Optional.of(
            List.of(
                new Withdrawal(
                    UInt64.valueOf(1),
                    UInt64.valueOf(2),
                    Address.fromHexString("03"),
                    GWei.of(4))));
    Optional<BlockAccessList> blockAccessList = Optional.of(BlockAccessList.builder().build());
    BlockBody blockBody = new BlockBody(transactions, ommers, withdrawals, blockAccessList);

    BlockBodyEncoder blockBodyEncoder = new BlockBodyEncoder();
    Bytes actualRlp = blockBodyEncoder.encodeWrapped(blockBody);

    String expectedRlpHex =
        "0xf9022ddedd01020394000000000000000000000000000000000000000405061b0809f901f0f901eda0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347940000000000000000000000000000000000000001a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000808080808080a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470880000000000000000d9d8010294000000000000000000000000000000000000000304c0";
    Assertions.assertEquals(expectedRlpHex, actualRlp.toHexString());
  }
}
