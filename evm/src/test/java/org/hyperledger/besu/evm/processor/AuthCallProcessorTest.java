/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.AuthOperation;
import org.hyperledger.besu.evm.toy.ToyWorld;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AuthCallProcessorTest extends MessageCallProcessorTest {

  MessageCallProcessor spyingMessageCallProcessor;
  ArgumentCaptor<MessageFrame> frameCaptor = ArgumentCaptor.forClass(MessageFrame.class);

  WorldUpdater toyWorld = new ToyWorld();

  @Test
  public void authCallHappyPath() {
    final EVM pragueEVM =
        MainnetEVMs.prague(new PragueGasCalculator(), BigInteger.ONE, EvmConfiguration.DEFAULT);
    final EVMExecutor executor = EVMExecutor.evm(pragueEVM);
    this.spyingMessageCallProcessor =
        spy(new MessageCallProcessor(pragueEVM, precompileContractRegistry));
    executor.messageCallProcessor(this.spyingMessageCallProcessor);

    executor.worldUpdater(toyWorld);
    executor.gas(10_000_000_000L);

    SignatureAlgorithm algo = SignatureAlgorithmFactory.getInstance();
    KeyPair keys = algo.generateKeyPair();
    Optional<Bytes> chainId = Optional.of(Bytes.of(1));
    long senderNonce = 0;
    Address invokerAddress = Address.fromHexString("0xdeadbeef");
    Bytes32 invoker = Bytes32.leftPad(invokerAddress);
    Bytes32 contractCommitment = Bytes32.leftPad(Bytes.fromHexString("0x1234"));
    Bytes authPreImage =
        Bytes.concatenate(
            Bytes.ofUnsignedShort(AuthOperation.MAGIC),
            Bytes32.leftPad(chainId.get()),
            Bytes32.leftPad(Bytes.ofUnsignedLong(senderNonce)),
            invoker,
            contractCommitment);
    Bytes32 messageHash = Hash.keccak256(authPreImage);
    SECPSignature signature = algo.sign(messageHash, keys);
    Bytes encodedSignature = signature.encodedBytes();

    Bytes authParam =
        Bytes.concatenate(
            encodedSignature.slice(64, 1), // y parity
            encodedSignature.slice(0, 32), // r
            encodedSignature.slice(32, 32), // s
            contractCommitment);

    toyWorld.createAccount(
        Address.extract(keys.getPublicKey()), 0, Wei.MAX_WEI); // initialize authority account
    toyWorld.createAccount(invokerAddress, 0, Wei.MAX_WEI); // initialize invoker account
    final Bytes codeBytes =
        Bytes.fromHexString(
            "0x"
                + "6061" // push 97 the calldata length
                + "6000" // push 0 the offset
                + "6000" // push 0 the destination offset
                + "37" // calldatacopy 97 bytes of the auth param to mem 0
                + "6061" // param is 97 bytes (0x61)
                + "6000" // push 0 where in mem to find auth param
                + "73" // push next 20 bytes for the authority address
                + Address.extract(keys.getPublicKey())
                    .toUnprefixedHexString() // push authority address
                + "F6" // AUTH call, should work and set authorizedBy on the frame
                + "6000" // push 0 for return length, we don't care about the return
                + "6000" // push 0 for return offset, we don't care about the return
                + "6000" // push 0 for input length
                + "6000" // push 0 for input offset
                + "60FF" // push 255 for the value being sent
                + "73deadbeefdeadbeefdeadbeefdeadbeefdeadbeef" // push20 the invokee address
                + "60FF" // push 255 gas
                + "F7"); // AUTHCALL, should work
    executor.contract(invokerAddress);
    executor.execute(codeBytes, authParam, Wei.ZERO, invokerAddress);
    verify(this.spyingMessageCallProcessor, times(2))
        .start(frameCaptor.capture(), any()); // one for parent frame, one for child
    List<MessageFrame> frames = frameCaptor.getAllValues();
    assertThat(frames.get(0).getStackItem(0)).isEqualTo((Bytes.of(1)));
  }

  @Test
  public void unauthorizedAuthCall() {
    final EVM pragueEVM =
        MainnetEVMs.prague(new PragueGasCalculator(), BigInteger.ONE, EvmConfiguration.DEFAULT);
    final EVMExecutor executor = EVMExecutor.evm(pragueEVM);
    this.spyingMessageCallProcessor =
        spy(new MessageCallProcessor(pragueEVM, precompileContractRegistry));
    executor.messageCallProcessor(this.spyingMessageCallProcessor);

    executor.gas(10_000_000_000L);

    final Bytes codeBytes =
        Bytes.fromHexString(
            "0x"
                + "6000" // push 0 for return length
                + "6000" // push 0 for return offset
                + "6000" // push 0 for input length
                + "6000" // push 0 for input offset
                + "60FF" // push 255 for the value being sent
                + "73deadbeefdeadbeefdeadbeefdeadbeefdeadbeef" // push20 the invokee address
                + "60FF" // push 255 gas
                + "F7"); // AUTHCALL without prior AUTH, should fail

    executor.execute(codeBytes, Bytes.EMPTY, Wei.ZERO, Address.ZERO);
    verify(this.spyingMessageCallProcessor).start(frameCaptor.capture(), any());
    assertThat(frameCaptor.getValue().getStackItem(0)).isEqualTo(Bytes32.ZERO);
  }
}
