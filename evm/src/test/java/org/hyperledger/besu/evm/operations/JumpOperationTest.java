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
package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.JumpOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.testutils.FakeBlockValues;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JumpOperationTest {

  private static final IstanbulGasCalculator gasCalculator = new IstanbulGasCalculator();

  private static final int CURRENT_PC = 1;

  private Address address;
  private EVM evm;

  private TestMessageFrameBuilder createMessageFrameBuilder(final long initialGas) {
    final BlockValues blockValues = new FakeBlockValues(1337);
    return new TestMessageFrameBuilder()
        .address(address)
        .blockValues(blockValues)
        .initialGas(initialGas);
  }

  @Before
  public void init() {
    address = Address.fromHexString("0x18675309");

    final OperationRegistry registry = new OperationRegistry();
    registry.put(new JumpOperation(gasCalculator));
    registry.put(new JumpDestOperation(gasCalculator));
    evm = new EVM(registry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.PARIS);
  }

  @Test
  public void shouldJumpWhenLocationIsJumpDest() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final Bytes jumpBytes = Bytes.fromHexString("0x6003565b00");
    final MessageFrame frame =
        createMessageFrameBuilder(10_000L)
            .pushStackItem(UInt256.fromHexString("0x03"))
            .code(CodeFactory.createCode(jumpBytes, Hash.hash(jumpBytes), 0, false))
            .build();
    frame.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frame, evm);
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  public void shouldJumpWhenLocationIsJumpDestAndAtEndOfCode() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final Bytes jumpBytes = Bytes.fromHexString("0x6003565b");
    final MessageFrame frame =
        createMessageFrameBuilder(10_000L)
            .pushStackItem(UInt256.fromHexString("0x03"))
            .code(CodeFactory.createCode(jumpBytes, Hash.hash(jumpBytes), 0, false))
            .build();
    frame.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frame, evm);
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  public void shouldHaltWithInvalidJumDestinationWhenLocationIsOutsideOfCodeRange() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final Bytes jumpBytes = Bytes.fromHexString("0x6801000000000000000c565b00");
    final MessageFrame frameDestinationGreaterThanCodeSize =
        createMessageFrameBuilder(100L)
            .pushStackItem(UInt256.fromHexString("0xFFFFFFFF"))
            .code(CodeFactory.createCode(jumpBytes, Hash.hash(jumpBytes), 0, false))
            .build();
    frameDestinationGreaterThanCodeSize.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frameDestinationGreaterThanCodeSize, evm);
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
    final Bytes badJump = Bytes.fromHexString("0x60045600");
    final MessageFrame frameDestinationEqualsToCodeSize =
        createMessageFrameBuilder(100L)
            .pushStackItem(UInt256.fromHexString("0x04"))
            .code(CodeFactory.createCode(badJump, Hash.hash(badJump), 0, false))
            .build();
    frameDestinationEqualsToCodeSize.setPC(CURRENT_PC);

    final OperationResult result2 = operation.execute(frameDestinationEqualsToCodeSize, evm);
    assertThat(result2.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  }

  @Test
  public void longContractsValidate() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final Bytes longCode =
        Bytes.fromHexString(
            "0x60006000351461001157600050610018565b6101016020525b60016000351461002a5760005061002f565b326020525b60026000351461004157600050610046565b336020525b6003600035146100585760005061005d565b306020525b60046000351461006f57600050610075565b60016020525b60005160005260006020351461008d576000506100b6565b5a600052602051315060165a60005103036000555a600052602051315060165a60005103036001555b6001602035146100c8576000506100f1565b5a6000526020513b5060165a60005103036000555a6000526020513b5060165a60005103036001555b6002602035146101035760005061012c565b5a6000526020513f5060165a60005103036000555a6000526020513f5060165a60005103036001555b60036020351461013e5760005061017a565b6106a5610100525a600052602060006101006020513c60205a60005103036000555a600052602060006101006020513c60205a60005103036001555b00");

    final MessageFrame longContract =
        createMessageFrameBuilder(100L)
            .pushStackItem(UInt256.fromHexString("0x12c"))
            .code(CodeFactory.createCode(longCode, Hash.hash(longCode), 0, false))
            .build();
    longContract.setPC(255);

    final OperationResult result = operation.execute(longContract, evm);
    assertThat(result.getHaltReason()).isNull();
  }
}
