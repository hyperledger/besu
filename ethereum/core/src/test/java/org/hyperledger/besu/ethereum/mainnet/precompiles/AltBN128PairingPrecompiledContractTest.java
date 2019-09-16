/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AltBN128PairingPrecompiledContractTest {

  @Mock MessageFrame messageFrame;
  @Mock GasCalculator gasCalculator;

  private final AltBN128PairingPrecompiledContract byzantiumContract =
      AltBN128PairingPrecompiledContract.byzantium(gasCalculator);
  private final AltBN128PairingPrecompiledContract istanbulContract =
      AltBN128PairingPrecompiledContract.istanbul(gasCalculator);

  @Test
  public void compute_validPoints() {
    final BytesValue input = validPointBytes();
    final BytesValue result = byzantiumContract.compute(input, messageFrame);
    assertThat(result).isEqualTo(AltBN128PairingPrecompiledContract.TRUE);
  }

  public BytesValue validPointBytes() {
    final BytesValue g1Point0 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"));
    final BytesValue g2Point0 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2"),
            BytesValue.fromHexString(
                "0x1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed"),
            BytesValue.fromHexString(
                "0x090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b"),
            BytesValue.fromHexString(
                "0x12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa"));
    final BytesValue g1Point1 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            BytesValue.fromHexString(
                "0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45"));
    final BytesValue g2Point1 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2"),
            BytesValue.fromHexString(
                "0x1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed"),
            BytesValue.fromHexString(
                "0x090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b"),
            BytesValue.fromHexString(
                "0x12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa"));

    return BytesValues.concatenate(g1Point0, g2Point0, g1Point1, g2Point1);
  }

  @Test
  public void compute_invalidPointsOutsideSubgroupG2() {
    final BytesValue g1Point0 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000002"));
    final BytesValue g2Point0 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x1382cd45e5674247f9c900b5c6f6cabbc189c2fabe2df0bf5acd84c97818f508"),
            BytesValue.fromHexString(
                "0x1246178655ab8f2f26956b189894b7eb93cd4215b9937e7969e44305f80f521e"),
            BytesValue.fromHexString(
                "0x08331c0a261a74e7e75db1232956663cbc88110f726159c5cba1857ecd03fa64"),
            BytesValue.fromHexString(
                "0x1fbf8045ce3e79b5cde4112d38bcd0efbdb1295d2eefdf58151ae309d7ded7db"));
    final BytesValue g1Point1 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"),
            BytesValue.fromHexString(
                "0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45"));
    final BytesValue g2Point1 =
        BytesValues.concatenate(
            BytesValue.fromHexString(
                "0x1382cd45e5674247f9c900b5c6f6cabbc189c2fabe2df0bf5acd84c97818f508"),
            BytesValue.fromHexString(
                "0x1246178655ab8f2f26956b189894b7eb93cd4215b9937e7969e44305f80f521e"),
            BytesValue.fromHexString(
                "0x08331c0a261a74e7e75db1232956663cbc88110f726159c5cba1857ecd03fa64"),
            BytesValue.fromHexString(
                "0x1fbf8045ce3e79b5cde4112d38bcd0efbdb1295d2eefdf58151ae309d7ded7db"));

    final BytesValue input = BytesValues.concatenate(g1Point0, g2Point0, g1Point1, g2Point1);
    final BytesValue result = byzantiumContract.compute(input, messageFrame);
    assertThat(result).isNull();
  }

  @Test
  public void gasPrice_byzantium() {
    assertThat(byzantiumContract.gasRequirement(validPointBytes())).isEqualTo(Gas.of(260_000));
  }

  @Test
  public void gasPrice_istanbul() {
    assertThat(istanbulContract.gasRequirement(validPointBytes())).isEqualTo(Gas.of(113_000));
  }
}
