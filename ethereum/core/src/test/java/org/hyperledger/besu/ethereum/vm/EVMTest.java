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
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.operation.StopOperation;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EVMTest {

  @Mock private OperationRegistry operationRegistry;
  private final GasCalculator gasCalculator = new FrontierGasCalculator();
  private EVM evm;

  @Before
  public void setup() {
    evm = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
  }

  @Test
  public void assertThatEndOfScriptNotExplicitlySetInCodeReturnsAVirtualOperation() {
    final Bytes noEnd = Bytes.fromHexString("0x60203560003555606035604035556000");
    final Code code = Code.createLegacyCode(noEnd, Hash.hash(noEnd));
    final Operation operation = evm.operationAtOffset(code, code.getSize());
    assertThat(operation).isNotNull();
    assertThat(operation.isVirtualOperation()).isTrue();
  }

  @Test
  public void assertThatEndOfScriptExplicitlySetInCodeDoesNotReturnAVirtualOperation() {
    final Bytes ends = Bytes.fromHexString("0x6020356000355560603560403555600000");
    final Code code = Code.createLegacyCode(ends, Hash.hash(ends));
    when(operationRegistry.get(anyByte())).thenReturn(new StopOperation(gasCalculator));
    final Operation operation = evm.operationAtOffset(code, code.getSize() - 1);
    assertThat(operation).isNotNull();
    assertThat(operation.isVirtualOperation()).isFalse();
  }
}
