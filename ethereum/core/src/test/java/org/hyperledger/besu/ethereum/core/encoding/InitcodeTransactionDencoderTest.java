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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InitcodeTransactionDencoderTest {
  private static Stream<Arguments> provideTestVector() {
    return Stream.of(
        Arguments.of(
            "no list",
            "0xf8710a168477359400852e90edd000837a120094000000000000000000000000abcdef0987654321808487654321c0c080a02222222222222222222222222222222222222222222222222222222222222222a02222222222222222222222222222222222222222222222222222222222222222",
            "must contain at least one initcode"),
        Arguments.of(
            "zero entry",
            "0xf8760a168477359400852e90edd000837a120094000000000000000000000000abcdef0987654321808487654321c0c5831234568080a02222222222222222222222222222222222222222222222222222222222222222a02222222222222222222222222222222222222222222222222222222222222222",
            "cannot be zero length"));
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("provideTestVector")
  void initcodeTransactionDecoderFailure(
      final String ignoredName, final String invalidTx, final String failureSubtext) {
    RLPInput rlpIn = new BytesValueRLPInput(Bytes.fromHexString(invalidTx), false);
    try {
      InitcodeTransactionDecoder.decode(rlpIn);
      fail("The transaction is not valid");
    } catch (IllegalArgumentException iae) {
      assertThat(iae.getMessage()).contains(failureSubtext);
    }
  }
}
