/*
 * Copyright contributors to Hyperledger Besu
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

package org.hyperledger.besu.evm.code;

import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.JumpOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayDeque;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class CodeV0Test {

  private static final IstanbulGasCalculator gasCalculator = new IstanbulGasCalculator();

  private static final int CURRENT_PC = 1;
  private EVM evm;

  @Before
  public void startUp() {
    final OperationRegistry registry = new OperationRegistry();
    registry.put(new JumpOperation(gasCalculator));
    registry.put(new JumpDestOperation(gasCalculator));
    evm = new EVM(registry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.PARIS);
  }

  @Test
  public void shouldReuseJumpDestMap() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final Bytes jumpBytes = Bytes.fromHexString("0x6003565b00");
    final CodeV0 getsCached =
        (CodeV0) spy(CodeFactory.createCode(jumpBytes, Hash.hash(jumpBytes), 0, false));
    MessageFrame frame = createJumpFrame(getsCached);

    OperationResult result = operation.execute(frame, evm);
    assertNull(result.getHaltReason());
    Mockito.verify(getsCached, times(1)).calculateJumpDests();

    // do it again to prove we don't recalculate, and we hit the cache

    frame = createJumpFrame(getsCached);

    result = operation.execute(frame, evm);
    assertNull(result.getHaltReason());
    Mockito.verify(getsCached, times(1)).calculateJumpDests();
  }

  @Nonnull
  private MessageFrame createJumpFrame(final CodeV0 getsCached) {
    final MessageFrame frame =
        MessageFrame.builder()
            .type(MESSAGE_CALL)
            .messageFrameStack(new ArrayDeque<>())
            .worldUpdater(mock(WorldUpdater.class))
            .initialGas(10_000L)
            .address(Address.ZERO)
            .originator(Address.ZERO)
            .contract(Address.ZERO)
            .gasPrice(Wei.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(Address.ZERO)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(getsCached)
            .blockValues(mock(BlockValues.class))
            .depth(0)
            .completer(f -> {})
            .miningBeneficiary(Address.ZERO)
            .blockHashLookup(l -> Hash.EMPTY)
            .build();

    frame.setPC(CURRENT_PC);
    frame.pushStackItem(UInt256.fromHexString("0x03"));
    return frame;
  }
}
