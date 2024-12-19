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
package org.hyperledger.besu.evm.fluent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

import com.google.common.collect.MultimapBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EVMExecutorTest {

  @Test
  void currentEVM() {
    var subject = EVMExecutor.evm();
    assertThat(subject.getEVMVersion()).isEqualTo(EvmSpecVersion.CANCUN);
  }

  @ParameterizedTest
  @EnumSource(EvmSpecVersion.class)
  void evmByRequest(final EvmSpecVersion version) {
    var subject = EVMExecutor.evm(version);
    assertThat(subject.getEVMVersion()).isEqualTo(version);
  }

  @ParameterizedTest
  @EnumSource(EvmSpecVersion.class)
  void evmWithChainIDByRequest(final EvmSpecVersion version) {
    var subject = EVMExecutor.evm(version, BigInteger.TEN);
    assertThat(subject.getEVMVersion()).isEqualTo(version);
    if (EvmSpecVersion.ISTANBUL.compareTo(version) <= 0) {
      assertThat(subject.getChainId()).map(Bytes::trimLeadingZeros).map(Bytes::toInt).contains(10);
    } else {
      assertThat(subject.getChainId()).isEmpty();
    }
  }

  @ParameterizedTest
  @EnumSource(EvmSpecVersion.class)
  void evmWithChainIDByBytes(final EvmSpecVersion version) {
    var subject = EVMExecutor.evm(version, Bytes.fromHexString("0xc4a1201d"));
    assertThat(subject.getEVMVersion()).isEqualTo(version);
    if (EvmSpecVersion.ISTANBUL.compareTo(version) <= 0) {
      assertThat(subject.getChainId())
          .map(Bytes::trimLeadingZeros)
          .map(Bytes::toInt)
          .contains(0xc4a1201d);
    } else {
      assertThat(subject.getChainId()).isEmpty();
    }
  }

  @Test
  void customEVM() {
    var subject =
        EVMExecutor.evm(
            new EVM(
                new OperationRegistry(),
                new FrontierGasCalculator(),
                EvmConfiguration.DEFAULT,
                EvmSpecVersion.EXPERIMENTAL_EIPS));
    assertThat(subject).isNotNull();
  }

  @Test
  void nullEVM() {
    assertThrows(NullPointerException.class, () -> EVMExecutor.evm((EVM) null));
  }

  @SuppressWarnings({"removal", "InlineMeInliner"})
  @Test
  void defaultChainIdAPIs() {
    Bytes32 defaultChainId = Bytes32.leftPad(Bytes.of(1));

    EVMExecutor istanbulEVM = EVMExecutor.istanbul(EvmConfiguration.DEFAULT);
    assertThat(istanbulEVM.getChainId()).contains(defaultChainId);

    EVMExecutor berlinEVM = EVMExecutor.berlin(EvmConfiguration.DEFAULT);
    assertThat(berlinEVM.getChainId()).contains(defaultChainId);

    EVMExecutor londonEVM = EVMExecutor.london(EvmConfiguration.DEFAULT);
    assertThat(londonEVM.getChainId()).contains(defaultChainId);

    EVMExecutor parisEVM = EVMExecutor.paris(EvmConfiguration.DEFAULT);
    assertThat(parisEVM.getChainId()).contains(defaultChainId);

    EVMExecutor shanghaiEVM = EVMExecutor.shanghai(EvmConfiguration.DEFAULT);
    assertThat(shanghaiEVM.getChainId()).contains(defaultChainId);

    EVMExecutor cancunEVM = EVMExecutor.cancun(EvmConfiguration.DEFAULT);
    assertThat(cancunEVM.getChainId()).contains(defaultChainId);

    EVMExecutor cancunEOFEVM =
        EVMExecutor.cancunEOF(defaultChainId.toBigInteger(), EvmConfiguration.DEFAULT);
    assertThat(cancunEOFEVM.getChainId()).contains(defaultChainId);

    EVMExecutor pragueEVM =
        EVMExecutor.prague(defaultChainId.toBigInteger(), EvmConfiguration.DEFAULT);
    assertThat(pragueEVM.getChainId()).contains(defaultChainId);

    EVMExecutor osakaEVM =
        EVMExecutor.osaka(defaultChainId.toBigInteger(), EvmConfiguration.DEFAULT);
    assertThat(osakaEVM.getChainId()).contains(defaultChainId);

    EVMExecutor futureEipsVM = EVMExecutor.futureEips(EvmConfiguration.DEFAULT);
    assertThat(futureEipsVM.getChainId()).contains(defaultChainId);
  }

  @Test
  void executeBytes() {
    var result =
        EVMExecutor.evm(EvmSpecVersion.SHANGHAI)
            .worldUpdater(createSimpleWorld().updater())
            .execute(Bytes.fromHexString("0x6001600255"), Bytes.EMPTY, Wei.ZERO, Address.ZERO);
    assertThat(result).isNotNull();
  }

  @Test
  void giantExecuteStack() {
    SimpleWorld simpleWorld = createSimpleWorld();

    var tracer = new StandardJsonTracer(System.out, false, true, true, false);
    var result =
        EVMExecutor.evm(EvmSpecVersion.SHANGHAI)
            .messageFrameType(MessageFrame.Type.CONTRACT_CREATION)
            .worldUpdater(simpleWorld.updater())
            .tracer(tracer)
            .contract(Address.fromHexString("0x100"))
            .gas(15_000_000L)
            .sender(Address.fromHexString("0x200"))
            .receiver(Address.fromHexString("0x300"))
            .coinbase(Address.fromHexString("0x400"))
            .number(1)
            .timestamp(9999)
            .gasLimit(15_000_000)
            .commitWorldState()
            .gasPriceGWei(Wei.ONE)
            .blobGasPrice(Wei.ONE)
            .callData(Bytes.fromHexString("0x12345678"))
            .ethValue(Wei.fromEth(1))
            .code(Bytes.fromHexString("0x6001600255"))
            .blockValues(new SimpleBlockValues())
            .difficulty(Bytes.ofUnsignedLong(1L))
            .mixHash(Bytes32.ZERO)
            .baseFee(Wei.ONE)
            .number(1)
            .timestamp(100L)
            .gasLimit(15_000_000L)
            .blockHashLookup((__, ___) -> Hash.ZERO)
            .versionedHashes(Optional.empty())
            .precompileContractRegistry(new PrecompileContractRegistry())
            .requireDeposit(false)
            .initialNonce(42)
            .contractValidationRules(List.of())
            .forceCommitAddresses(List.of())
            .warmAddress(Address.ZERO)
            .accessListWarmStorage(
                Address.ZERO, Bytes32.ZERO, Bytes32.leftPad(Bytes.ofUnsignedLong(2L)))
            .messageCallProcessor(new MessageCallProcessor(null, null))
            .contractCallProcessor(new ContractCreationProcessor(null, true, null, 1L))
            .execute();
    assertThat(result).isNotNull();
  }

  @Test
  void anternateExecStack() {
    SimpleWorld simpleWorld = createSimpleWorld();
    var result =
        EVMExecutor.evm(EvmSpecVersion.SHANGHAI)
            .worldUpdater(simpleWorld.updater())
            .messageFrameType(MessageFrame.Type.MESSAGE_CALL)
            .code(Bytes.fromHexString("0x6001600255"))
            .prevRandao(Bytes32.ZERO)
            .accessListWarmAddresses(Set.of())
            .accessListWarmStorage(MultimapBuilder.linkedHashKeys().arrayListValues().build())
            .execute();
    assertThat(result).isNotNull();
  }

  @Nonnull
  private static SimpleWorld createSimpleWorld() {
    SimpleWorld simpleWorld = new SimpleWorld();

    simpleWorld.createAccount(Address.fromHexString("0x0"), 1, Wei.fromEth(100));
    simpleWorld.createAccount(Address.fromHexString("0x100"), 1, Wei.fromEth(100));
    simpleWorld.createAccount(Address.fromHexString("0x200"), 1, Wei.fromEth(100));
    simpleWorld.createAccount(Address.fromHexString("0x300"), 1, Wei.fromEth(100));
    simpleWorld.createAccount(Address.fromHexString("0x400"), 1, Wei.fromEth(100));
    return simpleWorld;
  }
}
