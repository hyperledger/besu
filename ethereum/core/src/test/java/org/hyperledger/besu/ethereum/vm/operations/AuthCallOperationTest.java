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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.BerlinGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.PuxiGasCalculator;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class AuthCallOperationTest {

  private final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
  private final WorldUpdater worldStateUpdater = worldStateArchive.getMutable().updater();

  @Test
  public void testThrows64th() {
    Address invokerAddress = Address.wrap(Bytes.random(20));
    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .address(invokerAddress)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(34103 + 1024))
            .authorized(invokerAddress)
            .build();
    AuthCallOperation authCallOperation = new AuthCallOperation(new PuxiGasCalculator());
    messageFrame.pushStackItem(UInt256.ONE); // retLength
    messageFrame.pushStackItem(UInt256.ONE); // retOffset
    messageFrame.pushStackItem(UInt256.valueOf(2)); // argsLength
    messageFrame.pushStackItem(UInt256.ONE); // argsOffset
    messageFrame.pushStackItem(UInt256.ONE); // valueExt
    messageFrame.pushStackItem(UInt256.ONE); // value
    messageFrame.pushStackItem(UInt256.ONE); // addr
    messageFrame.pushStackItem(UInt256.valueOf((long) ((1024 * 63.0 / 64) + 1))); // gas
    Operation.OperationResult result = authCallOperation.execute(messageFrame, null);
    assertThat(result.getHaltReason().get()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_GAS);
  }

  @Test
  public void testJustEnoughGas() {
    Address currentContactAddress = Address.wrap(Bytes.random(20));
    Address invokerAddress = Address.wrap(Bytes.random(20));
    worldStateUpdater.getOrCreate(invokerAddress);
    worldStateUpdater.getOrCreate(currentContactAddress).getMutable().setBalance(Wei.ONE);
    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .address(currentContactAddress)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(34703 + 1024))
            .authorized(invokerAddress)
            .worldState(worldStateUpdater)
            .build();
    AuthCallOperation authCallOperation = new AuthCallOperation(new PuxiGasCalculator());
    messageFrame.pushStackItem(UInt256.ZERO); // retLength
    messageFrame.pushStackItem(UInt256.ZERO); // retOffset
    messageFrame.pushStackItem(UInt256.ZERO); // argsLength
    messageFrame.pushStackItem(UInt256.ZERO); // argsOffset
    messageFrame.pushStackItem(UInt256.ZERO); // valueExt
    messageFrame.pushStackItem(UInt256.ZERO); // value
    messageFrame.pushStackItem(UInt256.fromBytes(Bytes32.leftPad(invokerAddress))); // addr
    messageFrame.pushStackItem(UInt256.valueOf((long) ((1024 * 63.0 / 64)))); // just enough gas.
    Operation.OperationResult result = authCallOperation.execute(messageFrame, null);
    assertThat(result.getHaltReason()).isEmpty();
    assertThat(messageFrame.popStackItem())
        .isEqualTo(
            UInt256.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000003f0"));
    assertThat(messageFrame.getMessageFrameStack().getFirst()).isNotNull();
  }

  @Test
  public void testCaptureCaller() {
    Address aa = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    Address bb = Address.fromHexString("0x000000000000000000000000000000000000bbbb");
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    SECPPrivateKey privateKey =
        signatureAlgorithm.createPrivateKey(
            Bytes32.fromHexString(
                "0xb71c71a67e1177ad4e901695e1b4b9ee17ae16c6668d313eac2f96dbcda3f291"));
    KeyPair keyPair = signatureAlgorithm.createKeyPair(privateKey);
    Address caller = Address.extract(keyPair.getPublicKey());
    MutableAccount aaAccount = worldStateUpdater.getOrCreate(aa).getMutable();
    aaAccount.setBalance(Wei.ZERO);
    aaAccount.setNonce(0L);

    MutableAccount bbAccount = worldStateUpdater.getOrCreate(bb).getMutable();
    bbAccount.setBalance(Wei.ZERO);
    bbAccount.setNonce(0L);
    bbAccount.setCode(Bytes.fromHexString("0x33600055"));

    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .address(aa)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(34703 + 1024))
            .authorized(caller)
            .worldState(worldStateUpdater)
            .build();
    messageFrame.pushStackItem(UInt256.ZERO); // retLength
    messageFrame.pushStackItem(UInt256.ZERO); // retOffset
    messageFrame.pushStackItem(UInt256.ZERO); // argsLength
    messageFrame.pushStackItem(UInt256.ZERO); // argsOffset
    messageFrame.pushStackItem(UInt256.ZERO); // valueExt
    messageFrame.pushStackItem(UInt256.ZERO); // value
    messageFrame.pushStackItem(
        UInt256.fromHexString("000000000000000000000000000000000000bbbb")); // addr
    messageFrame.pushStackItem(UInt256.ZERO); // gas.
    AuthCallOperation authCallOperation = new AuthCallOperation(new BerlinGasCalculator());
    Operation.OperationResult result = authCallOperation.execute(messageFrame, null);
    assertThat(result.getHaltReason()).isEmpty();
    assertThat(messageFrame.popStackItem())
        .isEqualTo(
            UInt256.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"));
    assertThat(messageFrame.getMessageFrameStack().getFirst()).isNotNull();
    MessageFrame childMessageFrame = messageFrame.getMessageFrameStack().getFirst();
    assertThat(childMessageFrame.getSenderAddress()).isEqualTo(caller);
  }
}
