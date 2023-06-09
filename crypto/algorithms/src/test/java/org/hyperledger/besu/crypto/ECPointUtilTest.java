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
package org.hyperledger.besu.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.spec.ECPoint;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class ECPointUtilTest {

  @Test
  public void ecPointWithBigIntegerByteArrayNot32ShouldBeEncoded() {
    final BigInteger xCoord =
        Bytes.fromHexString("0x00c139989725b24ac6214f97d4ad75e74ddec4208ddc4a277195d95f7ca56735b6")
            .toBigInteger();
    final BigInteger yCoord =
        Bytes.fromHexString("0x4b80989faae422b06a2a2fe75766402eb8e89b782bdb7c352c584cda8fd239")
            .toBigInteger();
    final Bytes expectedEncoded =
        Bytes.fromHexString(
            "0xc139989725b24ac6214f97d4ad75e74ddec4208ddc4a277195d95f7ca56735b6004b80989faae422b06a2a2fe75766402eb8e89b782bdb7c352c584cda8fd239");

    assertThat(xCoord.toByteArray()).hasSize(33);
    assertThat(yCoord.toByteArray()).hasSize(31);

    final ECPoint ecPoint = new ECPoint(xCoord, yCoord);
    final Bytes encodedBytes = ECPointUtil.getEncodedBytes(ecPoint);

    assertThat(encodedBytes.toArray()).hasSize(64);
    assertThat(encodedBytes).isEqualByComparingTo(expectedEncoded);
  }

  @Test
  public void ecPointWithBigIntegerAs32ShouldBeEncoded() {
    final BigInteger xCoord =
        Bytes.fromHexString("0x1575de790d7d00623a3f7e6ec2d0b69d65c5c183f8773a033d2c20dd20008271")
            .toBigInteger();
    final BigInteger yCoord =
        Bytes.fromHexString("0x61772e076283d628beb591a23412c7d906d11b011ca66f188d4052db1e2ad615")
            .toBigInteger();
    final Bytes expectedEncoded =
        Bytes.fromHexString(
            "0x1575de790d7d00623a3f7e6ec2d0b69d65c5c183f8773a033d2c20dd2000827161772e076283d628beb591a23412c7d906d11b011ca66f188d4052db1e2ad615");

    assertThat(xCoord.toByteArray()).hasSize(32);
    assertThat(yCoord.toByteArray()).hasSize(32);

    final ECPoint ecPoint = new ECPoint(xCoord, yCoord);
    final Bytes encodedBytes = ECPointUtil.getEncodedBytes(ecPoint);

    assertThat(encodedBytes.toArray()).hasSize(64);
    assertThat(encodedBytes).isEqualByComparingTo(expectedEncoded);
  }
}
