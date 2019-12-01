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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.vm.operations.StopOperation;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EVMTest {

  private static final int CONTRACT_ACCOUNT_VERSION = 1;
  @Mock private OperationRegistry operationRegistry;
  @Mock private GasCalculator gasCalculator;
  private EVM evm;

  @Before
  public void setup() {
    evm = new EVM(operationRegistry, gasCalculator);
  }

  @Test
  public void assertThatEndOfScriptNotExplicitlySetInCodeReturnsAVirtualOperation() {
    final Code code = new Code(Bytes.fromHexString("0x60203560003555606035604035556000"));
    final Operation operation =
        evm.operationAtOffset(code, CONTRACT_ACCOUNT_VERSION, code.getSize());
    assertThat(operation).isNotNull();
    assertThat(operation.isVirtualOperation()).isTrue();
  }

  @Test
  public void assertThatEndOfScriptExplicitlySetInCodeDoesNotReturnAVirtualOperation() {
    final Code code = new Code(Bytes.fromHexString("0x6020356000355560603560403555600000"));
    when(operationRegistry.getOrDefault(
            anyByte(), eq(CONTRACT_ACCOUNT_VERSION), any(Operation.class)))
        .thenReturn(new StopOperation(gasCalculator));
    final Operation operation =
        evm.operationAtOffset(code, CONTRACT_ACCOUNT_VERSION, code.getSize() - 1);
    assertThat(operation).isNotNull();
    assertThat(operation.isVirtualOperation()).isFalse();
  }
}
