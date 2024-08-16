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
package org.hyperledger.besu.evm.precompile;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AltBN128PairingPrecompiledContractTest {

  @Mock MessageFrame messageFrame;
  @Mock GasCalculator gasCalculator;

  private final AltBN128PairingPrecompiledContract byzantiumContract =
      AltBN128PairingPrecompiledContract.byzantium(gasCalculator);
  private final AltBN128PairingPrecompiledContract istanbulContract =
      AltBN128PairingPrecompiledContract.istanbul(gasCalculator);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void compute_validPoints(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }
    final Bytes input = validPointBytes();
    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).getOutput();
    assertThat(result).isEqualTo(AltBN128PairingPrecompiledContract.TRUE);
  }

  Bytes validPointBytes() {
    final Bytes g1Point0 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"));
    final Bytes g2Point0 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2"),
            Bytes.fromHexString(
                "0x1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed"),
            Bytes.fromHexString(
                "0x090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b"),
            Bytes.fromHexString(
                "0x12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa"));
    final Bytes g1Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Bytes.fromHexString(
                "0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45"));
    final Bytes g2Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2"),
            Bytes.fromHexString(
                "0x1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed"),
            Bytes.fromHexString(
                "0x090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b"),
            Bytes.fromHexString(
                "0x12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa"));

    return Bytes.concatenate(g1Point0, g2Point0, g1Point1, g2Point1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void compute_invalidPointsOutsideSubgroupG2(final boolean useNative) {
    if (useNative) {
      AbstractAltBnPrecompiledContract.maybeEnableNative();
    } else {
      AbstractAltBnPrecompiledContract.disableNative();
    }
    final Bytes g1Point0 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"));
    final Bytes g2Point0 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x1382cd45e5674247f9c900b5c6f6cabbc189c2fabe2df0bf5acd84c97818f508"),
            Bytes.fromHexString(
                "0x1246178655ab8f2f26956b189894b7eb93cd4215b9937e7969e44305f80f521e"),
            Bytes.fromHexString(
                "0x08331c0a261a74e7e75db1232956663cbc88110f726159c5cba1857ecd03fa64"),
            Bytes.fromHexString(
                "0x1fbf8045ce3e79b5cde4112d38bcd0efbdb1295d2eefdf58151ae309d7ded7db"));
    final Bytes g1Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            Bytes.fromHexString(
                "0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45"));
    final Bytes g2Point1 =
        Bytes.concatenate(
            Bytes.fromHexString(
                "0x1382cd45e5674247f9c900b5c6f6cabbc189c2fabe2df0bf5acd84c97818f508"),
            Bytes.fromHexString(
                "0x1246178655ab8f2f26956b189894b7eb93cd4215b9937e7969e44305f80f521e"),
            Bytes.fromHexString(
                "0x08331c0a261a74e7e75db1232956663cbc88110f726159c5cba1857ecd03fa64"),
            Bytes.fromHexString(
                "0x1fbf8045ce3e79b5cde4112d38bcd0efbdb1295d2eefdf58151ae309d7ded7db"));

    final Bytes input = Bytes.concatenate(g1Point0, g2Point0, g1Point1, g2Point1);
    final Bytes result = byzantiumContract.computePrecompile(input, messageFrame).getOutput();
    assertThat(result).isNull();
  }

  @Test
  void gasPrice_byzantium() {
    // gas calculation is java only
    assertThat(byzantiumContract.gasRequirement(validPointBytes())).isEqualTo(260_000L);
  }

  @Test
  void gasPrice_istanbul() {
    // gas calculation is java only
    assertThat(istanbulContract.gasRequirement(validPointBytes())).isEqualTo(113_000L);
  }
}
