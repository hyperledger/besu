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
import org.checkerframework.checker.guieffect.qual.UI;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.LondonGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetEvmRegistries;
import org.hyperledger.besu.ethereum.mainnet.MainnetMessageCallProcessor;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation;
import org.hyperledger.besu.ethereum.vm.OperationRegistry;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import static org.assertj.core.api.Assertions.assertThat;

public class CallOperationTest implements Consumer<MessageFrame> {

  private final GasCalculator gasCalculator = new LondonGasCalculator();

  //private static final int CURRENT_PC = 1;

  private Blockchain blockchain;
  private final Address caller = Address.fromHexString("0x000000000000000000000000000000ca1100f022");
  private final Address callee = Address.fromHexString("0x00000000000000000000000000000000b0b0face");
  //private final Address instigator = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
  private WorldUpdater worldStateUpdater;
  private EVM evm;
  //  private final OperationTracer tracer = new DebugOperationTracer(TraceOptions.DEFAULT);
  private MessageFrame.Builder frameBuilder;
  private CallOperation callOperation;
  private JumpOperation jumpOperation;


  @Before
  public void init() {
    blockchain = mock(Blockchain.class);

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();


    worldStateUpdater = worldStateArchive.getMutable().updater();
    worldStateUpdater.getOrCreate(caller).getMutable().setBalance(Wei.ONE);
    worldStateUpdater.commit();

    worldStateUpdater.getOrCreate(callee);
    worldStateUpdater.commit();

    evm = MainnetEvmRegistries.london(this.gasCalculator, BigInteger.ONE);
    this.callOperation = Mockito.spy(new CallOperation(this.gasCalculator));
    evm.getOperations().put(this.callOperation, 0);
    this.jumpOperation = Mockito.spy(new JumpOperation(this.gasCalculator));
    evm.getOperations().put(this.jumpOperation, 0);


    this.frameBuilder = new MessageFrame.Builder();
    createCaller();

  }

  private MessageFrame createCaller() {
    final UInt256 retLength = UInt256.valueOf(256L);
    final UInt256 retOffset = UInt256.valueOf(100);
    final UInt256 argsLength = UInt256.ZERO;
    final UInt256 argsOffset = UInt256.valueOf(1999);
    final UInt256 value = UInt256.valueOf(1);
    final UInt256 addr = UInt256.fromHexString("0xb0b0face");
    final UInt256 gas = UInt256.ONE;

    MessageFrame frame = frameBuilder.code(new Code())
            .type(MessageFrame.Type.MESSAGE_CALL)
            .sender(this.caller)
            .blockchain(blockchain)
            .messageFrameStack(new ArrayDeque<MessageFrame>())
            .worldState(worldStateUpdater)
            .initialGas(Gas.of(100000))
            .address(this.caller)
            .originator(Address.ZERO)
            .contract(Address.ZERO)
            .gasPrice(Wei.ONE)
            .inputData(Bytes.EMPTY)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .blockHeader(mock(BlockHeader.class))
            .depth(1)
            .completer(this)
            .miningBeneficiary(Address.ZERO)
            .blockHashLookup(mock(BlockHashLookup.class))
            .contractAccountVersion(0)
            .build();
    frame.setCurrentOperation(this.callOperation);
    frame.pushStackItem(retLength);
    frame.pushStackItem(retOffset);
    frame.pushStackItem(argsLength);
    frame.pushStackItem(argsOffset);
    frame.pushStackItem(value);
    frame.pushStackItem(addr);
    frame.pushStackItem(gas);

    //frame.expandMemory(UInt256.ZERO, UInt256.valueOf(500));
    //frame.writeMemory(
      //      UInt256.fromBytes(memoryOffset), UInt256.valueOf(codeBytes.size()), codeBytes);

    return frame;
  }


  @Test
  public void shouldNotRecalcJumpsOnSubcall() {
    // send Message to caller.
    // once complete, assert that Code constructor was only called twice, for caller and callee
    MessageFrame setupCallerContract = createCaller();
    Operation.OperationResult retval = this.callOperation.execute(setupCallerContract, evm);
    assertThat(retval.getHaltReason().isEmpty()).isTrue();
    setupCallerContract = createCaller();
    retval = this.callOperation.execute(setupCallerContract, evm);

    assertThat(retval.getHaltReason().isEmpty()).isTrue();
    Mockito.verify(this.callOperation, times(2)).execute(any(),any());
    Mockito.verify(this.callOperation, times(1)).createAndCache(any());
    Mockito.verify(this.callOperation, times(1)).loadFromCache(any());
}
  @Override
  public void accept(final MessageFrame messageFrame) {
    System.out.println(messageFrame.toString());
  }


}
