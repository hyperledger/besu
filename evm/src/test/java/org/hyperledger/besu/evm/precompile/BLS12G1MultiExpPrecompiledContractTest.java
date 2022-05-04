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
 *
 */
package org.hyperledger.besu.evm.precompile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.evm.frame.MessageFrame;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.io.CharStreams;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

@RunWith(Parameterized.class)
public class BLS12G1MultiExpPrecompiledContractTest {

  final BLS12G1MultiExpPrecompiledContract contract = new BLS12G1MultiExpPrecompiledContract();

  private final MessageFrame messageFrame = mock(MessageFrame.class);

  @Parameterized.Parameters
  public static Iterable<String[]> parameters() throws IOException {
    return CharStreams.readLines(
            new InputStreamReader(
                Objects.requireNonNull(
                    BLS12G1MultiExpPrecompiledContractTest.class.getResourceAsStream(
                        "g1_multiexp.csv")),
                UTF_8))
        .stream()
        .map(line -> line.split(",", 4))
        .collect(Collectors.toList());
  }

  @Parameterized.Parameter(0)
  public String input;

  @Parameterized.Parameter(1)
  public String expectedResult;

  @Parameterized.Parameter(2)
  public String expectedGasUsed;

  @Parameterized.Parameter(3)
  public String notes;

  @Test
  public void shouldCalculate() {
    if ("input".equals(input)) {
      // skip the header row
      return;
    }
    final Bytes input = Bytes.fromHexString(this.input);
    final Bytes expectedComputation =
        expectedResult == null ? null : Bytes.fromHexString(expectedResult);
    final Bytes actualComputation = contract.compute(input, messageFrame);
    if (actualComputation == null) {
      final ArgumentCaptor<Bytes> revertReason = ArgumentCaptor.forClass(Bytes.class);
      verify(messageFrame).setRevertReason(revertReason.capture());
      assertThat(new String(revertReason.getValue().toArrayUnsafe(), UTF_8)).isEqualTo(notes);

      assertThat(expectedComputation.size()).isZero();
    } else {
      assertThat(actualComputation).isEqualTo(expectedComputation);
      assertThat(contract.gasRequirement(input)).isEqualTo(Long.parseLong(expectedGasUsed));
    }
  }
}
