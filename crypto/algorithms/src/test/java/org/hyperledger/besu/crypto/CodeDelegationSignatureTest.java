/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class CodeDelegationSignatureTest {

  private static final BigInteger TWO_POW_256 = BigInteger.valueOf(2).pow(256);

  @Test
  void testValidInputs() {
    BigInteger r = BigInteger.ONE;
    BigInteger s = BigInteger.TEN;
    BigInteger yParity = BigInteger.ONE;

    CodeDelegationSignature result = CodeDelegationSignature.create(r, s, yParity);

    assertThat(r).isEqualTo(result.getR());
    assertThat(s).isEqualTo(result.getS());
    assertThat(yParity.byteValue()).isEqualTo(result.getRecId());
  }

  @Test
  void testNullRValue() {
    BigInteger s = BigInteger.TEN;
    BigInteger yParity = BigInteger.ZERO;

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> CodeDelegationSignature.create(null, s, yParity));
  }

  @Test
  void testNullSValue() {
    BigInteger r = BigInteger.ONE;
    BigInteger yParity = BigInteger.ZERO;

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> CodeDelegationSignature.create(r, null, yParity));
  }

  @Test
  void testRValueExceedsTwoPow256() {
    BigInteger r = TWO_POW_256;
    BigInteger s = BigInteger.TEN;
    BigInteger yParity = BigInteger.ZERO;

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CodeDelegationSignature.create(r, s, yParity))
        .withMessageContainingAll("Invalid 'r' value, should be < 2^256");
  }

  @Test
  void testSValueExceedsTwoPow256() {
    BigInteger r = BigInteger.ONE;
    BigInteger s = TWO_POW_256;
    BigInteger yParity = BigInteger.ZERO;

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CodeDelegationSignature.create(r, s, yParity))
        .withMessageContainingAll("Invalid 's' value, should be < 2^256");
  }

  @Test
  void testYParityExceedsTwoPow256() {
    BigInteger r = BigInteger.ONE;
    BigInteger s = BigInteger.TWO;
    BigInteger yParity = TWO_POW_256;

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CodeDelegationSignature.create(r, s, yParity))
        .withMessageContainingAll("Invalid 'yParity' value, should be < 2^256");
  }

  @Test
  void testValidYParityZero() {
    BigInteger r = BigInteger.ONE;
    BigInteger s = BigInteger.TEN;
    BigInteger yParity = BigInteger.ZERO;

    CodeDelegationSignature result = CodeDelegationSignature.create(r, s, yParity);

    assertThat(r).isEqualTo(result.getR());
    assertThat(s).isEqualTo(result.getS());
    assertThat(yParity.byteValue()).isEqualTo(result.getRecId());
  }
}
