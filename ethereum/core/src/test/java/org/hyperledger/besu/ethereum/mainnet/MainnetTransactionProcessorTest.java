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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateUsingCache;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.contract.CodeCache;
import org.hyperledger.besu.ethereum.core.contract.CodeLoader;
import org.hyperledger.besu.ethereum.core.contract.ContractCacheConfiguration;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class MainnetTransactionProcessorTest {

  private static final int MAX_STACK_SIZE = 1024;

  private MainnetTransactionProcessor transactionProcessor;

  private final GasCalculator gasCalculator = new LondonGasCalculator();
  @Mock private MainnetTransactionValidator transactionValidator;
  @Mock private AbstractMessageProcessor contractCreationProcessor;
  private MainnetMessageCallProcessor messageCallProcessor;
  private CodeLoader loader;
  private CodeCache cache;
  @Mock private Blockchain blockchain;
  @Mock private WorldUpdater worldState;
  @Mock private ProcessableBlockHeader blockHeader;
  @Mock private Transaction transaction;
  @Mock private BlockHashLookup blockHashLookup;
  private static String manyJumps;

  @BeforeClass
  public static void init() throws IOException {
    URL manyJumpsURL = MainnetTransactionProcessorTest.class.getResource("manyJumps.hex");
    manyJumps = Resources.toString(manyJumpsURL, Charsets.UTF_8);
  }

  @Before
  public void before() {
    EVM evm = mock(EVM.class);
    Answer<Void> dequeFrame =
        invocation -> {
          Object[] args = invocation.getArguments();
          MessageFrame frame = (MessageFrame) args[0];
          frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
          return null;
        };
    doAnswer(dequeFrame).when(evm).runToHalt(any(), any());
    messageCallProcessor =
        new MainnetMessageCallProcessor(evm, mock(PrecompileContractRegistry.class));

    when(transactionValidator.validateForSender(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(transactionValidator.validate(any(), any(), any())).thenReturn(ValidationResult.valid());
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(70L));

    this.loader = spy(new CodeLoader());
    this.cache =
        new CodeCache(ContractCacheConfiguration.getInstance().getContractCacheWeight(), loader);
    this.worldState = spy(createInMemoryWorldStateUsingCache(this.cache).updater());

    transactionProcessor =
        new MainnetTransactionProcessor(
            gasCalculator,
            transactionValidator,
            contractCreationProcessor,
            messageCallProcessor,
            false,
            MAX_STACK_SIZE,
            FeeMarket.legacy(),
            CoinbaseFeePriceCalculator.frontier());
  }

  @Test
  public void shouldCallTransactionValidatorWithExpectedTransactionValidationParams() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        transactionValidationParamCaptor();

    final TransactionValidationParams expectedValidationParams =
        ImmutableTransactionValidationParams.builder().build();

    transactionProcessor.processTransaction(
        blockchain,
        mock(WorldUpdater.class),
        blockHeader,
        transaction,
        Address.fromHexString("1"),
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build());

    assertThat(txValidationParamCaptor.getValue())
        .isEqualToComparingFieldByField(expectedValidationParams);
  }

  @Test
  public void shouldCacheCode() {

    KeyPair senderKeys = SignatureAlgorithmFactory.getInstance().generateKeyPair();
    Address sending = Address.extract(senderKeys.getPublicKey());
    Address contractAddr = Address.fromHexString("B0B0FACE");
    Transaction messageToContract =
        new TransactionTestFixture()
            .to(Optional.of(contractAddr))
            .sender(sending)
            .type(TransactionType.EIP1559)
            .maxFeePerGas(Optional.of(Wei.ONE))
            .gasLimit(300000L)
            .maxPriorityFeePerGas(Optional.of(Wei.ONE))
            .createTransaction(senderKeys);

    worldState.getOrCreateSenderAccount(sending).getMutable().setBalance(Wei.fromEth(1000L));
    worldState.createAccount(contractAddr).getMutable().setCode(Bytes.fromHexString(manyJumps));

    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        messageToContract,
        Address.fromHexString("1"),
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build());

    EvmAccount contractAccount = worldState.getAccount(contractAddr);
    Mockito.verify(worldState, times(1)).getContract(argThat(new AccountMatcher(contractAccount)));
    Mockito.verify(loader, times(1)).load(any());

    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        messageToContract,
        Address.fromHexString("1"),
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build());

    Mockito.verify(worldState, times(2)).getContract(argThat(new AccountMatcher(contractAccount)));
    Mockito.verify(loader, times(1)).load(any());
  }

  @Test
  public void shouldEvictOnAccountDestruct() {
    KeyPair senderKeys = SignatureAlgorithmFactory.getInstance().generateKeyPair();

    Address sending = Address.extract(senderKeys.getPublicKey());
    Address contractAddr = Address.fromHexString("B0B0FACE");

    Transaction messageToContract =
        new TransactionTestFixture()
            .to(Optional.of(contractAddr))
            .sender(sending)
            .gasLimit(30000)
            .createTransaction(senderKeys);
    ;

    this.worldState.getOrCreateSenderAccount(sending).getMutable().setBalance(Wei.fromEth(1000L));
    this.worldState
        .createAccount(contractAddr)
        .getMutable()
        .setCode(Bytes.fromHexString(manyJumps));
    this.worldState.commit();

    transactionProcessor.processTransaction(
        blockchain,
        this.worldState,
        blockHeader,
        messageToContract,
        Address.fromHexString("1"),
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build());

    EvmAccount contractAccount = this.worldState.getAccount(contractAddr);
    Mockito.verify(this.worldState, times(1))
        .getContract(argThat(new AccountMatcher(contractAccount)));
    Mockito.verify(loader, times(1)).load(any());

    this.worldState.deleteAccount(contractAccount.getAddress());
    this.worldState.commit();
    assertThat(this.worldState.get(contractAccount.getAddress())).isNull();
    assertThat(this.cache.size()).isEqualTo(0);
  }

  private ArgumentCaptor<TransactionValidationParams> transactionValidationParamCaptor() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        ArgumentCaptor.forClass(TransactionValidationParams.class);
    when(transactionValidator.validate(any(), any(), any())).thenReturn(ValidationResult.valid());
    // returning invalid transaction to halt method execution
    when(transactionValidator.validateForSender(any(), any(), txValidationParamCaptor.capture()))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.INCORRECT_NONCE));
    return txValidationParamCaptor;
  }

  private static class AccountMatcher implements ArgumentMatcher<Account> {

    private final Account left;

    private AccountMatcher(final Account left) {
      this.left = left;
    }

    @Override
    public boolean matches(final Account right) {
      return left.getAddressHash().equals(right.getAddressHash());
    }
  }
}
