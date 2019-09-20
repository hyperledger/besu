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
package org.hyperledger.besu.tests.acceptance.dsl.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;

import java.math.BigInteger;
import java.util.Optional;

import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

public class ExpectValidTransactionReceipt {

  private final String senderAddress;
  private final String contractAddress;

  public ExpectValidTransactionReceipt(final Account sender, final String contractAddress) {
    this.contractAddress = contractAddress;
    this.senderAddress = sender.getAddress();
  }

  public void verify(final Contract contract) {

    assertThat(contract).isNotNull();
    final Optional<TransactionReceipt> receipt = contract.getTransactionReceipt();

    // We're expecting a receipt
    assertThat(receipt).isNotNull();
    assertThat(receipt.isPresent()).isTrue();
    final TransactionReceipt transactionReceipt = receipt.get();

    // Contract transaction has no 'to' address or contract address
    assertThat(transactionReceipt.getTo()).isNull();
    assertThat(transactionReceipt.getRoot()).isNull();

    // Variables outside the control of the test :. just check existence
    assertThat(transactionReceipt.getBlockHash()).isNotBlank();
    assertThat(transactionReceipt.getTransactionHash()).isNotBlank();
    assertThat(transactionReceipt.getTransactionIndex()).isNotNull();
    assertThat(transactionReceipt.getTransactionHash()).isNotNull();

    // Block zero is the genesis, expecting anytime after then
    assertThat(transactionReceipt.getBlockNumber()).isGreaterThanOrEqualTo(BigInteger.ONE);

    // Address generation is deterministic, based on the sender address and the transaction nonce
    assertThat(transactionReceipt.getContractAddress()).isEqualTo(contractAddress);

    // Expecting successful transaction (status '0x1')
    assertThat(transactionReceipt.getStatus()).isEqualTo("0x1");

    // Address for the account that signed (and paid) for the contract deployment transaction
    assertThat(transactionReceipt.getFrom()).isEqualTo(senderAddress);

    // No logs from expected from the contract deployment
    assertThat(transactionReceipt.getLogs()).isNotNull();
    assertThat(transactionReceipt.getLogs().size()).isEqualTo(0);
    assertThat(transactionReceipt.getLogsBloom())
        .isEqualTo(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
  }
}
