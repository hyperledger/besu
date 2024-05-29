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
package org.hyperledger.besu.evm.operations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.operation.AuthOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class AuthOperationTest {

  @Test
  public void testAuthOperation() {
    SignatureAlgorithm algo = SignatureAlgorithmFactory.getInstance();
    KeyPair keys = algo.generateKeyPair();
    Address authingAddress = Address.extract(keys.getPublicKey());
    EVM fakeEVM = mock(EVM.class);

    Optional<Bytes> chainId = Optional.of(Bytes.of(1));
    when(fakeEVM.getChainId()).thenReturn(chainId);
    long senderNonce = 0;
    Address invokerAddress = Address.fromHexString("0xdeadbeef");
    Bytes32 invoker = Bytes32.leftPad(invokerAddress);
    Bytes32 contractCommitment = Bytes32.leftPad(Bytes.fromHexString("0x1234"));
    Bytes authPreImage =
        Bytes.concatenate(
            Bytes.ofUnsignedShort(AuthOperation.MAGIC),
            chainId.get(),
            Bytes32.leftPad(Bytes.ofUnsignedLong(senderNonce)),
            invoker,
            contractCommitment);
    Bytes32 messageHash = Hash.keccak256(authPreImage);
    SECPSignature signature = algo.sign(messageHash, keys);

    MessageFrame frame = mock(MessageFrame.class);
    when(frame.getContractAddress()).thenReturn(invokerAddress);
    MutableAccount authingAccount = mock(MutableAccount.class);
    when(authingAccount.getAddress()).thenReturn(authingAddress);
    when(authingAccount.getNonce()).thenReturn(senderNonce);
    when(frame.getRemainingGas()).thenReturn(1000000L);
    WorldUpdater state = mock(WorldUpdater.class);

    when(state.getAccount(authingAddress)).thenReturn(authingAccount);

    when(frame.getWorldUpdater()).thenReturn(state);

    when(frame.getSenderAddress()).thenReturn(authingAddress);
    when(state.getSenderAccount(frame)).thenReturn(authingAccount);
    when(frame.getStackItem(0)).thenReturn(authingAddress);
    when(frame.getStackItem(1)).thenReturn(Bytes.of(0));
    when(frame.getStackItem(2)).thenReturn(Bytes.of(97));
    Bytes encodedSignature = signature.encodedBytes();
    when(frame.readMemory(0, 1)).thenReturn(encodedSignature.slice(64, 1));
    when(frame.readMemory(1, 32)).thenReturn(Bytes32.wrap(encodedSignature.slice(0, 32).toArray()));
    when(frame.readMemory(33, 32))
        .thenReturn(Bytes32.wrap(encodedSignature.slice(32, 32).toArray()));
    when(frame.readMemory(65, 32)).thenReturn(contractCommitment);

    AuthOperation authOperation = new AuthOperation(new PragueGasCalculator());
    authOperation.execute(frame, fakeEVM);
    verify(frame).setAuthorizedBy(authingAddress);
    verify(frame).pushStackItem(UInt256.ONE);
  }

  @Test
  public void testAuthOperationNegative() {
    SignatureAlgorithm algo = SignatureAlgorithmFactory.getInstance();
    KeyPair keys = algo.generateKeyPair();
    Address authingAddress = Address.extract(keys.getPublicKey());
    EVM fakeEVM = mock(EVM.class);

    Optional<Bytes> chainId = Optional.of(Bytes.of(1));
    when(fakeEVM.getChainId()).thenReturn(chainId);
    long senderNonce = 0;
    Address invokerAddress = Address.fromHexString("0xdeadbeef");
    Bytes32 invoker = Bytes32.leftPad(invokerAddress);
    Bytes32 contractCommitment = Bytes32.leftPad(Bytes.fromHexString("0x1234"));
    Bytes authPreImage =
        Bytes.concatenate(
            Bytes.ofUnsignedShort(AuthOperation.MAGIC),
            chainId.get(),
            Bytes32.leftPad(Bytes.ofUnsignedLong(senderNonce)),
            invoker,
            contractCommitment);
    Bytes32 messageHash = Hash.keccak256(authPreImage);

    // Generate a new key pair to create an incorrect signature
    KeyPair wrongKeys = algo.generateKeyPair();
    SECPSignature wrongSignature = algo.sign(messageHash, wrongKeys);

    MessageFrame frame = mock(MessageFrame.class);
    when(frame.getRemainingGas()).thenReturn(1000000L);
    when(frame.getContractAddress()).thenReturn(invokerAddress);
    MutableAccount authingAccount = mock(MutableAccount.class);
    when(authingAccount.getAddress()).thenReturn(authingAddress);
    when(authingAccount.getNonce()).thenReturn(senderNonce);

    WorldUpdater state = mock(WorldUpdater.class);

    when(state.getAccount(authingAddress)).thenReturn(authingAccount);

    when(frame.getWorldUpdater()).thenReturn(state);

    when(frame.getSenderAddress()).thenReturn(authingAddress);
    when(state.getSenderAccount(frame)).thenReturn(authingAccount);
    when(frame.getStackItem(0)).thenReturn(authingAddress);
    when(frame.getStackItem(1)).thenReturn(Bytes.of(0));
    when(frame.getStackItem(2)).thenReturn(Bytes.of(97));
    Bytes encodedSignature = wrongSignature.encodedBytes(); // Use the wrong signature
    when(frame.readMemory(0, 1)).thenReturn(encodedSignature.slice(64, 1));
    when(frame.readMemory(1, 32)).thenReturn(Bytes32.wrap(encodedSignature.slice(0, 32).toArray()));
    when(frame.readMemory(33, 32))
        .thenReturn(Bytes32.wrap(encodedSignature.slice(32, 32).toArray()));
    when(frame.readMemory(65, 32)).thenReturn(contractCommitment);

    AuthOperation authOperation = new AuthOperation(new PragueGasCalculator());
    authOperation.execute(frame, fakeEVM);
    verify(frame, never()).setAuthorizedBy(authingAddress); // The address should not be authorized
    verify(frame).pushStackItem(UInt256.ZERO); // The stack should contain UInt256.ZERO
  }
}
