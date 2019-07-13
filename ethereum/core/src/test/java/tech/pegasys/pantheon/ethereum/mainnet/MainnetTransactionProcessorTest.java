/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.ValidationResult.invalid;
import static tech.pegasys.pantheon.ethereum.mainnet.ValidationResult.valid;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetTransactionProcessorTest {

  private static final int MAX_STACK_SIZE = 1024;

  private MainnetTransactionProcessor transactionProcessor;

  @Mock private GasCalculator gasCalculator;
  @Mock private TransactionValidator transactionValidator;
  @Mock private AbstractMessageProcessor contractCreationProcessor;
  @Mock private AbstractMessageProcessor messageCallProcessor;

  @Mock private Blockchain blockchain;
  @Mock private WorldUpdater worldState;
  @Mock private ProcessableBlockHeader blockHeader;
  @Mock private Transaction transaction;
  @Mock private BlockHashLookup blockHashLookup;

  @Before
  public void before() {
    transactionProcessor =
        new MainnetTransactionProcessor(
            gasCalculator,
            transactionValidator,
            contractCreationProcessor,
            messageCallProcessor,
            false,
            MAX_STACK_SIZE,
            Account.DEFAULT_VERSION);
  }

  @Test
  public void shouldCallTransactionValidatorWithExpectedTransactionValidationParams() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        transactionValidationParamCaptor();

    final TransactionValidationParams expectedValidationParams =
        new TransactionValidationParams.Builder().build();

    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        Address.fromHexString("1"),
        blockHashLookup,
        false,
        new TransactionValidationParams.Builder().build());

    assertThat(txValidationParamCaptor.getValue())
        .isEqualToComparingFieldByField(expectedValidationParams);
  }

  private ArgumentCaptor<TransactionValidationParams> transactionValidationParamCaptor() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        ArgumentCaptor.forClass(TransactionValidationParams.class);
    when(transactionValidator.validate(any())).thenReturn(valid());
    // returning invalid transaction to halt method execution
    when(transactionValidator.validateForSender(any(), any(), txValidationParamCaptor.capture()))
        .thenReturn(invalid(TransactionInvalidReason.INCORRECT_NONCE));
    return txValidationParamCaptor;
  }
}
