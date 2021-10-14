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

import static org.apache.tuweni.bytes.Bytes32.leftPad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.LogOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class LogOperationTest {

  static final Bytes HEX_32_F =
      Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
  static final Bytes HEX_32_7 =
      Bytes.fromHexString("0x7777777777777777777777777777777777777777777777777777777777777777");
  static final Bytes HEX_16_F = Bytes.fromHexString("0xffffffffffffffffffffffffffffffff");
  static final Bytes HEX_16_7 = Bytes.fromHexString("0x77777777777777777777777777777777");

  private MessageFrame createMessageFrame(
      final Address address, final Gas initialGas, final Gas remainingGas) {
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
  public void underlyingStackItemMutates() {
    final LogOperation operation = new LogOperation(4, new LondonGasCalculator());
    final Address address = Address.fromHexString("0x18675309");
    final MessageFrame frame = createMessageFrame(address, Gas.of(300_000), Gas.of(300_000));
    final MutableBytes32 shifty32 = MutableBytes32.wrap(HEX_32_F.toArray());
    final MutableBytes shifty16 = MutableBytes.wrap(HEX_16_F.toArray());
    frame.pushStackItem(HEX_16_F);
    frame.pushStackItem(HEX_32_F);
    frame.pushStackItem(shifty16);
    frame.pushStackItem(shifty32);
    frame.pushStackItem(UInt256.ZERO);
    frame.pushStackItem(UInt256.ZERO);

    final OperationResult result = operation.execute(frame, null);
    shifty32.set(0, HEX_32_7);
    shifty16.set(0, HEX_16_7);

    final Log expected =
        new Log(
            address,
            Bytes.EMPTY,
            List.of(
                LogTopic.create(HEX_32_F),
                LogTopic.create(leftPad(HEX_16_F)),
                LogTopic.create(HEX_32_F),
                LogTopic.create(leftPad(HEX_16_F))));

    assertThat(List.of(expected)).isEqualTo(frame.getLogs());
  }
}
