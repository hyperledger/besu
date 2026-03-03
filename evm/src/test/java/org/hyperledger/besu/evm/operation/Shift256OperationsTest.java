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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.operation.Shift256Operations.ALL_ONES;
import static org.hyperledger.besu.evm.operation.Shift256Operations.isShiftOverflow;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Shift256Operations} utility class. */
class Shift256OperationsTest {

  // region Constants Tests

  @Test
  void constants_areCorrect() {
    assertThat(ALL_ONES.size()).isEqualTo(32);
    assertThat(ALL_ONES.toHexString())
        .isEqualTo("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
  }

  // endregion

  // region isShiftOverflow Tests

  @Test
  void isShiftOverflow_empty_returnsFalse() {
    assertThat(isShiftOverflow(Bytes.EMPTY.toArrayUnsafe())).isFalse();
  }

  @Test
  void isShiftOverflow_singleByte_returnsFalse() {
    assertThat(isShiftOverflow(Bytes.of(0).toArrayUnsafe())).isFalse();
    assertThat(isShiftOverflow(Bytes.of(1).toArrayUnsafe())).isFalse();
    assertThat(isShiftOverflow(Bytes.of(128).toArrayUnsafe())).isFalse();
    assertThat(isShiftOverflow(Bytes.of(255).toArrayUnsafe())).isFalse();
  }

  @Test
  void isShiftOverflow_multiByteWithZeroPrefix_returnsFalse() {
    assertThat(isShiftOverflow(Bytes.of(0, 0, 42).toArrayUnsafe())).isFalse();
    assertThat(isShiftOverflow(Bytes.of(0, 0, 0, 0, 200).toArrayUnsafe())).isFalse();
  }

  @Test
  void isShiftOverflow_256_returnsTrue() {
    // 0x0100 = 256
    assertThat(isShiftOverflow(Bytes.fromHexString("0x0100").toArrayUnsafe())).isTrue();
  }

  @Test
  void isShiftOverflow_nonZeroHighByte_returnsTrue() {
    assertThat(isShiftOverflow(Bytes.of(1, 0).toArrayUnsafe())).isTrue();
    assertThat(isShiftOverflow(Bytes.of(0, 1, 0).toArrayUnsafe())).isTrue();
    assertThat(isShiftOverflow(Bytes.fromHexString("0x010000000000").toArrayUnsafe())).isTrue();
  }

  @Test
  void isShiftOverflow_largeValue_returnsTrue() {
    // Large shift amount should overflow
    assertThat(isShiftOverflow(Bytes.fromHexString("0xff00").toArrayUnsafe())).isTrue();
    assertThat(isShiftOverflow(Bytes.fromHexString("0x0200").toArrayUnsafe())).isTrue(); // 512
  }

  // endregion
}
