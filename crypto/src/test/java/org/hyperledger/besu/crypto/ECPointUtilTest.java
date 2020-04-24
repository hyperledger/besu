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

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ECPointUtilTest {

  @Test
  public void smallerByteArrayFromBigIntegerAdjusted() {
    final Bytes32 bytes32 =
        Bytes32.fromHexString("0x00169d3beacb40076ad4c8fd813a80c4d0b8e16df84066b9a7bdf2c50bff2de0");
    final BigInteger value = bytes32.toUnsignedBigInteger();
    // conversion from BigInteger results in 31 byte array
    final byte[] bigIntByteArray = value.toByteArray();
    Assertions.assertThat(bigIntByteArray).hasSize(31);

    final byte[] bytes = ECPointUtil.toUnsignedByteArray(value);

    Assertions.assertThat(bytes.length).isEqualTo(32);
    Assertions.assertThat(bytes).containsExactly(bytes32.toArray());
  }

  @Test
  public void largerByteArrayFromBigIntegerAdjusted() {
    final Bytes32 bytesValue =
        Bytes32.fromHexString("0xd5653abfe1713c793039cdc1e4587c851e1759ed37337db7a8ee6eec2dea1a56");
    final BigInteger value = bytesValue.toUnsignedBigInteger();
    // conversion from BigInteger results in 33 byte array
    final byte[] bigIntByteArray = value.toByteArray();
    Assertions.assertThat(bigIntByteArray).hasSize(33);

    final byte[] bytes = ECPointUtil.toUnsignedByteArray(value);

    Assertions.assertThat(bytes.length).isEqualTo(32);
    Assertions.assertThat(bytes).containsExactly(bytesValue.toArray());
  }

  @Test
  public void noAdjustmentRequired() {
    final Bytes32 bytesValue =
        Bytes32.fromHexString("0x5a0e86ad52892ab9a241e2f8cd26151a4432b8bd17ef27d211eb323f94dbac72");
    final BigInteger value = bytesValue.toUnsignedBigInteger();
    // conversion from BigInteger results in 32 byte array
    final byte[] bigIntByteArray = value.toByteArray();
    Assertions.assertThat(bigIntByteArray).hasSize(32);

    final byte[] bytes = ECPointUtil.toUnsignedByteArray(value);

    Assertions.assertThat(bytes.length).isEqualTo(32);
    Assertions.assertThat(bytes).containsExactly(bytesValue.toArray());
  }

  @Test
  public void invalidSizeThrowsError() {
    final Bytes bytes36 =
        Bytes.fromHexString(
            "0xb0a1162dafd9d224c0f1d2de9113e247d40497c2b7a1b85dab088b57ac1c67ae27d18292");
    final BigInteger value = bytes36.toUnsignedBigInteger();

    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> ECPointUtil.toUnsignedByteArray(value))
        .withMessage("Unexpected byte[] size when converting ECPoint: " + 37);
  }
}
