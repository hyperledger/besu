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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hyperledger.besu.evm.EOFTestConstants.EOF_CREATE_CONTRACT;
import static org.hyperledger.besu.evm.EOFTestConstants.INNER_CONTRACT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class EofCreateOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount account = mock(MutableAccount.class);
  private final MutableAccount newAccount = mock(MutableAccount.class);

  static final Bytes CALL_DATA =
      Bytes.fromHexString(
          "cafebaba600dbaadc0de57aff60061e5cafebaba600dbaadc0de57aff60061e5"); // 32 bytes

  public static final String SENDER = "0xdeadc0de00000000000000000000000000000000";

  //  private static final int SHANGHAI_CREATE_GAS = 41240;

  @Test
  void innerContractIsCorrect() {
    final EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    Code code = evm.getCodeUncached(INNER_CONTRACT);
    assertThat(code.isValid()).isTrue();

    final MessageFrame messageFrame = testMemoryFrame(code, CALL_DATA);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(newAccount.isStorageEmpty()).thenReturn(true);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final MessageFrame createFrame = messageFrame.getMessageFrameStack().peek();
    assertThat(createFrame).isNotNull();
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm, false, List.of(), 0, List.of());
    ccp.process(createFrame, OperationTracer.NO_TRACING);

    final Log log = createFrame.getLogs().get(0);
    final Bytes calculatedTopic = log.getTopics().get(0);
    assertThat(calculatedTopic).isEqualTo(CALL_DATA);
  }

  @Test
  void eofCreatePassesInCallData() {
    Bytes outerContract = EOF_CREATE_CONTRACT;
    final EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);

    Code code = evm.getCodeUncached(outerContract);
    if (!code.isValid()) {
      System.out.println(outerContract);
      fail(((CodeInvalid) code).getInvalidReason());
    }

    final MessageFrame messageFrame = testMemoryFrame(code, CALL_DATA);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(newAccount.isStorageEmpty()).thenReturn(true);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    var precompiles = MainnetPrecompiledContracts.prague(evm.getGasCalculator());
    final MessageFrame createFrame = messageFrame.getMessageFrameStack().peek();
    assertThat(createFrame).isNotNull();
    final MessageCallProcessor mcp = new MessageCallProcessor(evm, precompiles);
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm, false, List.of(), 0, List.of());
    while (!createFrame.getMessageFrameStack().isEmpty()) {
      var frame = createFrame.getMessageFrameStack().peek();
      assert frame != null;
      (switch (frame.getType()) {
            case CONTRACT_CREATION -> ccp;
            case MESSAGE_CALL -> mcp;
          })
          .process(frame, OperationTracer.NO_TRACING);
    }

    final Log log = createFrame.getLogs().get(0);
    final String calculatedTopic = log.getTopics().get(0).slice(0, 2).toHexString();
    assertThat(calculatedTopic).isEqualTo("0xc0de");

    assertThat(createFrame.getCreates())
        .containsExactly(Address.fromHexString("0x8c308e96997a8052e3aaab5af624cb827218687a"));
  }

  private MessageFrame testMemoryFrame(final Code code, final Bytes initData) {
    return MessageFrame.builder()
        .type(MessageFrame.Type.MESSAGE_CALL)
        .contract(Address.ZERO)
        .inputData(initData)
        .sender(Address.fromHexString(SENDER))
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(code)
        .completer(__ -> {})
        .address(Address.fromHexString(SENDER))
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .blockValues(mock(BlockValues.class))
        .gasPrice(Wei.ZERO)
        .miningBeneficiary(Address.ZERO)
        .originator(Address.ZERO)
        .initialGas(100000L)
        .worldUpdater(worldUpdater)
        .build();
  }
}
