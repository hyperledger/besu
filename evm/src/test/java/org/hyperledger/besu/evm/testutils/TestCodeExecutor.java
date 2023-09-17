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
package org.hyperledger.besu.evm.testutils;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.toy.ToyWorld;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;

public class TestCodeExecutor {

  private final BlockValues blockValues = new FakeBlockValues(13);
  private static final Address SENDER_ADDRESS = Address.fromHexString("0xe8f1b89");
  private final EVM evm;

  public TestCodeExecutor(final EVM evm) {
    this.evm = evm;
  }

  public MessageFrame executeCode(
      final String codeHexString,
      final long gasLimit,
      final Consumer<MutableAccount> accountSetup) {
    final WorldUpdater worldUpdater = createInitialWorldState(accountSetup);
    return executeCode(codeHexString, gasLimit, worldUpdater);
  }

  public MessageFrame executeCode(
      final String codeHexString, final long gasLimit, final WorldUpdater worldUpdater) {
    final MessageCallProcessor messageCallProcessor =
        new MessageCallProcessor(evm, new PrecompileContractRegistry());
    final Bytes codeBytes = Bytes.fromHexString(codeHexString.replaceAll("\\s", ""));
    final Code code = evm.getCode(Hash.hash(codeBytes), codeBytes);

    final MessageFrame initialFrame =
        new TestMessageFrameBuilder()
            .worldUpdater(worldUpdater)
            .initialGas(gasLimit)
            .address(SENDER_ADDRESS)
            .originator(SENDER_ADDRESS)
            .contract(SENDER_ADDRESS)
            .gasPrice(Wei.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(SENDER_ADDRESS)
            .value(Wei.ZERO)
            .code(code)
            .blockValues(blockValues)
            .build();

    final Deque<MessageFrame> messageFrameStack = initialFrame.getMessageFrameStack();
    while (!messageFrameStack.isEmpty()) {
      messageCallProcessor.process(messageFrameStack.peekFirst(), OperationTracer.NO_TRACING);
    }
    return initialFrame;
  }

  public static void deployContract(
      final WorldUpdater worldUpdater, final Address contractAddress, final String codeHexString) {
    var updater = worldUpdater.updater();
    final MutableAccount contract = updater.getOrCreate(contractAddress);

    contract.setNonce(0);
    contract.clearStorage();
    contract.setCode(Bytes.fromHexStringLenient(codeHexString));
    updater.commit();
  }

  public static WorldUpdater createInitialWorldState(final Consumer<MutableAccount> accountSetup) {
    ToyWorld toyWorld = new ToyWorld();

    final WorldUpdater worldState = toyWorld.updater();
    final MutableAccount senderAccount = worldState.getOrCreate(TestCodeExecutor.SENDER_ADDRESS);
    accountSetup.accept(senderAccount);
    worldState.commit();
    return toyWorld.updater();
  }
}
