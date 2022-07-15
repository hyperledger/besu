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
package org.hyperledger.besu.ethereum.vm.operations;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.SStoreOperation;
import org.hyperledger.besu.evm.operation.TLoadOperation;
import org.hyperledger.besu.evm.operation.TStoreOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.mockito.Mockito.mock;

public class TStoreOperationTest {

  private static final GasCalculator gasCalculator = new ShanghaiGasCalculator();

  private MessageFrame createMessageFrame(
      final Address address, final long initialGas, final long remainingGas) {
    final Blockchain blockchain = mock(Blockchain.class);

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    final WorldUpdater worldStateUpdater = worldStateArchive.getMutable().updater();
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    final MessageFrame frame =
        new MessageFrameTestFixture()
            .address(address)
            .worldUpdater(worldStateUpdater)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(initialGas)
            .build();
    worldStateUpdater.getOrCreate(address).getMutable().setBalance(Wei.of(1));
    worldStateUpdater.commit();
    frame.setGasRemaining(remainingGas);

    return frame;
  }

  @Test
  public void tstoreInsufficientGas() {
    long initialGas = 10_000L;
    long remainingGas = 99L; // TSTORE cost should be 100
    final TStoreOperation operation = new TStoreOperation(gasCalculator);
    final MessageFrame frame =
            createMessageFrame(Address.fromHexString("0x18675309"), initialGas, remainingGas);
    frame.pushStackItem(UInt256.ZERO);
    frame.pushStackItem(UInt256.fromHexString("0x01"));

    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getHaltReason()).isEqualTo(Optional.of(INSUFFICIENT_GAS));
  }

  @Test
  public void tStoreSimpleTest() {
    long initialGas = 10_000L;
    long remainingGas = 10_000L;
    final TStoreOperation operation = new TStoreOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrame(Address.fromHexString("0x18675309"), initialGas, remainingGas);
    frame.pushStackItem(UInt256.ZERO);
    frame.pushStackItem(UInt256.fromHexString("0x01"));

    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getHaltReason()).isEqualTo(Optional.empty());
  }

  @Test
  public void tLoadEmpty() {
    long initialGas = 10_000L;
    long remainingGas = 10_000L;
    final MessageFrame frame =
            createMessageFrame(Address.fromHexString("0x18675309"), initialGas, remainingGas);

    final TLoadOperation tload = new TLoadOperation(gasCalculator);
    frame.pushStackItem(UInt256.fromHexString("0x01"));
    final OperationResult tloadResult = tload.execute(frame, null);
    assertThat(tloadResult.getHaltReason()).isEqualTo(Optional.empty());
    UInt256 tloadValue = UInt256.fromBytes(frame.popStackItem());
    assertThat(tloadValue).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void tStoreTLoad() {
    long initialGas = 10_000L;
    long remainingGas = 10_000L;
    final TStoreOperation tstore = new TStoreOperation(gasCalculator);
    final MessageFrame frame =
            createMessageFrame(Address.fromHexString("0x18675309"), initialGas, remainingGas);
    frame.pushStackItem(UInt256.ONE);
    frame.pushStackItem(UInt256.fromHexString("0x01"));

    final OperationResult result = tstore.execute(frame, null);
    assertThat(result.getHaltReason()).isEqualTo(Optional.empty());

    TLoadOperation tload = new TLoadOperation(gasCalculator);
    frame.pushStackItem(UInt256.fromHexString("0x01"));
    OperationResult tloadResult = tload.execute(frame, null);
    assertThat(tloadResult.getHaltReason()).isEqualTo(Optional.empty());
    UInt256 tloadValue = UInt256.fromBytes(frame.popStackItem());
    assertThat(tloadValue).isEqualTo(UInt256.ONE);

    // Loading from a different location returns default value
    frame.pushStackItem(UInt256.fromHexString("0x02"));
    tloadResult = tload.execute(frame, null);
    assertThat(tloadResult.getHaltReason()).isEqualTo(Optional.empty());
    tloadValue = UInt256.fromBytes(frame.popStackItem());
    assertThat(tloadValue).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void tStoreUpdate() {
    long initialGas = 10_000L;
    long remainingGas = 10_000L;
    final TStoreOperation tstore = new TStoreOperation(gasCalculator);
    final MessageFrame frame =
            createMessageFrame(Address.fromHexString("0x18675309"), initialGas, remainingGas);
    frame.pushStackItem(UInt256.ONE);
    frame.pushStackItem(UInt256.fromHexString("0x01"));

    OperationResult result = tstore.execute(frame, null);
    assertThat(result.getHaltReason()).isEqualTo(Optional.empty());

    // Store 2 at position 1
    frame.pushStackItem(UInt256.fromHexString("0x02"));
    frame.pushStackItem(UInt256.fromHexString("0x01"));

    result = tstore.execute(frame, null);
    assertThat(result.getHaltReason()).isEqualTo(Optional.empty());

    final TLoadOperation tload = new TLoadOperation(gasCalculator);
    frame.pushStackItem(UInt256.fromHexString("0x01"));
    final OperationResult tloadResult = tload.execute(frame, null);
    assertThat(tloadResult.getHaltReason()).isEqualTo(Optional.empty());
    UInt256 tloadValue = UInt256.fromBytes(frame.popStackItem());
    assertThat(tloadValue).isEqualTo(UInt256.fromHexString("0x02"));
  }
}
