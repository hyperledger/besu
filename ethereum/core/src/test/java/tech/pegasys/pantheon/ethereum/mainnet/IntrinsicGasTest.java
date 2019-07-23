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
package tech.pegasys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntrinsicGasTest {

  private final GasCalculator gasCalculator;
  private final Gas expectedGas;
  private final String txRlp;

  public IntrinsicGasTest(
      final GasCalculator gasCalculator, final Gas expectedGas, final String txRlp) {
    this.gasCalculator = gasCalculator;
    this.expectedGas = expectedGas;
    this.txRlp = txRlp;
  }

  @Parameters
  public static Collection<Object[]> data() {
    final GasCalculator frontier = new FrontierGasCalculator();
    final GasCalculator istanbul = new IstanbulGasCalculator();
    return Arrays.asList(
        new Object[][] {
          // EnoughGAS
          {
            frontier,
            Gas.of(21952),
            "0xf86d80018259d894095e7baea6a6c7c4c2dfeb977efac326af552d870a8e0358ac39584bc98a7c979f984b031ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          {
            istanbul,
            Gas.of(21224),
            "0xf86d80018259d894095e7baea6a6c7c4c2dfeb977efac326af552d870a8e0358ac39584bc98a7c979f984b031ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          // FirstZeroBytes
          {
            frontier,
            Gas.of(21180),
            "0xf87c80018261a894095e7baea6a6c7c4c2dfeb977efac326af552d870a9d00000000000000000000000000010000000000000000000000000000001ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          {
            istanbul,
            Gas.of(21128),
            "0xf87c80018261a894095e7baea6a6c7c4c2dfeb977efac326af552d870a9d00000000000000000000000000010000000000000000000000000000001ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          // LastZeroBytes
          {
            frontier,
            Gas.of(21180),
            "0xf87c80018261a894095e7baea6a6c7c4c2dfeb977efac326af552d870a9d01000000000000000000000000000000000000000000000000000000001ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          {
            istanbul,
            Gas.of(21128),
            "0xf87c80018261a894095e7baea6a6c7c4c2dfeb977efac326af552d870a9d01000000000000000000000000000000000000000000000000000000001ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          // NotEnoughGAS
          {
            frontier,
            Gas.of(21952),
            "0xf86d800182521c94095e7baea6a6c7c4c2dfeb977efac326af552d870a8e0358ac39584bc98a7c979f984b031ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          {
            istanbul,
            Gas.of(21224),
            "0xf86d800182521c94095e7baea6a6c7c4c2dfeb977efac326af552d870a8e0358ac39584bc98a7c979f984b031ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a0efffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          // ZeroBytes
          {
            frontier,
            Gas.of(21116),
            "0xf87c80018261a894095e7baea6a6c7c4c2dfeb977efac326af552d870a9d00000000000000000000000000000000000000000000000000000000001ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
          {
            istanbul,
            Gas.of(21116),
            "0xf87c80018261a894095e7baea6a6c7c4c2dfeb977efac326af552d870a9d00000000000000000000000000000000000000000000000000000000001ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804"
          },
        });
  }

  @Test
  public void validateGasCost() {
    Transaction t = Transaction.readFrom(RLP.input(BytesValue.fromHexString(txRlp)));
    assertThat(gasCalculator.transactionIntrinsicGasCost(t)).isEqualTo(expectedGas);
  }
}
