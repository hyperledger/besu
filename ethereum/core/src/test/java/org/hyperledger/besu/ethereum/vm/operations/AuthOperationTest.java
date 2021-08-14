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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class AuthOperationTest {

  @Test
  public void testInvalidYParity() {
    Address invokerAddress = Address.wrap(Bytes.random(20));
    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .address(invokerAddress)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(10000))
            .build();
    AuthOperation authOperation = new AuthOperation(new IstanbulGasCalculator());
    messageFrame.pushStackItem(UInt256.ONE);
    messageFrame.pushStackItem(UInt256.ONE);
    messageFrame.pushStackItem(UInt256.valueOf(2));
    messageFrame.pushStackItem(UInt256.ONE);
    authOperation.executeFixedCostOperation(messageFrame, null);
    assertThat(messageFrame.popStackItem()).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void testValidSignature() {
    AuthOperation authOperation = new AuthOperation(new IstanbulGasCalculator());
    Address invokerAddress = Address.wrap(Bytes.random(20));
    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .contract(invokerAddress)
            .address(invokerAddress)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(10000))
            .build();
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    Bytes32 commit = Bytes32.random();
    KeyPair keyPair = signatureAlgorithm.generateKeyPair();
    SECPSignature sig =
        signatureAlgorithm.sign(
            Hash.keccak256(
                Bytes.concatenate(Bytes.of(0x03), Bytes32.leftPad(invokerAddress), commit)),
            keyPair);
    messageFrame.pushStackItem(UInt256.valueOf(sig.getS()));
    messageFrame.pushStackItem(UInt256.valueOf(sig.getR()));
    messageFrame.pushStackItem(UInt256.fromBytes(Bytes32.leftPad(Bytes.of(sig.getRecId()))));
    messageFrame.pushStackItem(UInt256.fromBytes(commit));
    authOperation.executeFixedCostOperation(messageFrame, null);
    assertThat(messageFrame.popStackItem())
        .isEqualTo(Bytes32.leftPad(Address.extract(keyPair.getPublicKey())));
  }

  @Test
  public void testBadCommit() {
    AuthOperation authOperation = new AuthOperation(new IstanbulGasCalculator());
    Address invokerAddress = Address.wrap(Bytes.random(20));
    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .contract(invokerAddress)
            .address(invokerAddress)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(10000))
            .build();
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    Bytes32 commit = Bytes32.random();
    KeyPair keyPair = signatureAlgorithm.generateKeyPair();
    SECPSignature sig =
        signatureAlgorithm.sign(
            Hash.keccak256(
                Bytes.concatenate(Bytes.of(0x03), Bytes32.leftPad(invokerAddress), commit)),
            keyPair);
    messageFrame.pushStackItem(UInt256.valueOf(sig.getS()));
    messageFrame.pushStackItem(UInt256.valueOf(sig.getR()));
    messageFrame.pushStackItem(UInt256.fromBytes(Bytes32.leftPad(Bytes.of(sig.getRecId()))));
    messageFrame.pushStackItem(UInt256.fromBytes(commit).add(1)); // make it invalid.
    authOperation.executeFixedCostOperation(messageFrame, null);
    assertThat(messageFrame.popStackItem())
        .isNotEqualTo(Bytes32.leftPad(Address.extract(keyPair.getPublicKey())));
  }

  @Test
  public void testCannotRecoverKey() {
    AuthOperation authOperation = new AuthOperation(new IstanbulGasCalculator());
    Address invokerAddress = Address.wrap(Bytes.random(20));
    BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    Blockchain blockchain = mock(Blockchain.class);
    MessageFrame messageFrame =
        new MessageFrameTestFixture()
            .contract(invokerAddress)
            .address(invokerAddress)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .initialGas(Gas.of(10000))
            .build();
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    Bytes32 commit = Bytes32.random();
    KeyPair keyPair = signatureAlgorithm.generateKeyPair();
    SECPSignature sig =
        signatureAlgorithm.sign(
            Hash.keccak256(
                Bytes.concatenate(Bytes.of(0x03), Bytes32.leftPad(invokerAddress), commit)),
            keyPair);
    messageFrame.pushStackItem(UInt256.valueOf(sig.getS()));
    messageFrame.pushStackItem(UInt256.MAX_VALUE); // invalid r value
    messageFrame.pushStackItem(UInt256.fromBytes(Bytes32.leftPad(Bytes.of(sig.getRecId()))));
    messageFrame.pushStackItem(UInt256.fromBytes(commit));
    authOperation.executeFixedCostOperation(messageFrame, null);
    assertThat(messageFrame.popStackItem())
        .isNotEqualTo(Bytes32.leftPad(Address.extract(keyPair.getPublicKey())));
  }
}
