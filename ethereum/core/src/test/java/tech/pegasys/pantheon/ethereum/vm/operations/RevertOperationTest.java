/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.vm.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.mainnet.ConstantinopleGasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class RevertOperationTest {

  private final String code;

  private final MessageFrame messageFrame = mock(MessageFrame.class);
  private final RevertOperation operation = new RevertOperation(new ConstantinopleGasCalculator());

  @Parameters(name = "sender: {0}, salt: {1}, code: {2}")
  public static Object[][] params() {
    return new Object[][] {
      {
        "0x6c726576657274656420646174616000557f726576657274206d657373616765000000000000000000000000000000000000600052600e6000fd",
      }
    };
  }

  public RevertOperationTest(final String code) {
    this.code = code;
  }

  @Before
  public void setUp() {
    when(messageFrame.popStackItem())
        .thenReturn(Bytes32.fromHexString("0x00"))
        .thenReturn(Bytes32.fromHexString("0x0e"));
    when(messageFrame.readMemory(UInt256.ZERO, UInt256.of(0x0e)))
        .thenReturn(BytesValue.fromHexString("726576657274206d657373616765"));
  }

  @Test
  public void shouldReturnReason() {
    assertTrue(code.contains("726576657274206d657373616765"));
    ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
    operation.execute(messageFrame);
    Mockito.verify(messageFrame).setRevertReason(arg.capture());
    assertEquals("revert message", arg.getValue());
  }
}
