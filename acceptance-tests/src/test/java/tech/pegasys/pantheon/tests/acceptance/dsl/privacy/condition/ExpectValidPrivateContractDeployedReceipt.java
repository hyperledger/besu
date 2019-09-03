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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

public class ExpectValidPrivateContractDeployedReceipt implements PrivateContractCondition {

  private final String contractAddress;
  private final String senderAddress;

  public ExpectValidPrivateContractDeployedReceipt(
      final String contractAddress, final String senderAddress) {
    this.contractAddress = contractAddress;
    this.senderAddress = senderAddress;
  }

  @Override
  public void verify(final Contract contract) {

    assertThat(contract).isNotNull();
    final Optional<TransactionReceipt> receipt = contract.getTransactionReceipt();

    // We're expecting a receipt
    assertThat(receipt).isNotNull();
    assertThat(receipt.isPresent()).isTrue();
    final TransactionReceipt transactionReceipt = receipt.get();

    // Contract transaction has no 'to' address or contract address
    assertThat(transactionReceipt.getTo()).isNull();

    // Address generation is deterministic, based on the sender address and the transaction nonce
    assertThat(transactionReceipt.getContractAddress()).isEqualTo(contractAddress);

    // Address for the account that signed (and paid) for the contract deployment transaction
    assertThat(transactionReceipt.getFrom()).isEqualTo(senderAddress);
  }
}
