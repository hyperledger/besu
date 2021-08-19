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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.mainnet.MainnetMessageCallProcessor;
import org.hyperledger.besu.ethereum.mainnet.PrecompileContractRegistry;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;

public class TestCodeExecutor {

  private final ExecutionContextTestFixture fixture;
  private final BlockHeader blockHeader = new BlockHeaderTestFixture().number(13).buildHeader();
  private static final Address SENDER_ADDRESS = AddressHelpers.ofValue(244259721);

  public TestCodeExecutor(final ProtocolSchedule protocolSchedule) {
    fixture = ExecutionContextTestFixture.builder().protocolSchedule(protocolSchedule).build();
  }

  public MessageFrame executeCode(
      final String code, final long gasLimit, final Consumer<MutableAccount> accountSetup) {
    final ProtocolSpec protocolSpec = fixture.getProtocolSchedule().getByBlockNumber(0);
    final WorldUpdater worldState =
        createInitialWorldState(accountSetup, fixture.getStateArchive());
    final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

    final MainnetMessageCallProcessor messageCallProcessor =
        new MainnetMessageCallProcessor(protocolSpec.getEvm(), new PrecompileContractRegistry());

    final Transaction transaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .value(Wei.ZERO)
            .sender(SENDER_ADDRESS)
            .signature(
                SignatureAlgorithmFactory.getInstance()
                    .createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 1))
            .gasLimit(gasLimit)
            .to(SENDER_ADDRESS)
            .payload(Bytes.EMPTY)
            .gasPrice(Wei.ZERO)
            .nonce(0)
            .build();
    final MessageFrame initialFrame =
        new MessageFrameTestFixture()
            .messageFrameStack(messageFrameStack)
            .blockchain(fixture.getBlockchain())
            .worldState(worldState)
            .initialGas(Gas.of(gasLimit))
            .address(SENDER_ADDRESS)
            .originator(SENDER_ADDRESS)
            .contract(SENDER_ADDRESS)
            .gasPrice(transaction.getGasPrice().get())
            .inputData(transaction.getPayload())
            .sender(SENDER_ADDRESS)
            .value(transaction.getValue())
            .code(new Code(Bytes.fromHexString(code)))
            .blockHeader(blockHeader)
            .depth(0)
            .build();
    messageFrameStack.addFirst(initialFrame);

    while (!messageFrameStack.isEmpty()) {
      messageCallProcessor.process(messageFrameStack.peekFirst(), OperationTracer.NO_TRACING);
    }
    return initialFrame;
  }

  private WorldUpdater createInitialWorldState(
      final Consumer<MutableAccount> accountSetup, final WorldStateArchive stateArchive) {
    final MutableWorldState initialWorldState = stateArchive.getMutable();

    final WorldUpdater worldState = initialWorldState.updater();
    final MutableAccount senderAccount =
        worldState.getOrCreate(TestCodeExecutor.SENDER_ADDRESS).getMutable();
    accountSetup.accept(senderAccount);
    worldState.commit();
    initialWorldState.persist(null);
    return stateArchive.getMutable(initialWorldState.rootHash(), null).get().updater();
  }
}
