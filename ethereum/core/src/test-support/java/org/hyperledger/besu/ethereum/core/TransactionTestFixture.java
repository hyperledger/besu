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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TransactionTestFixture {

  private TransactionType transactionType = TransactionType.FRONTIER;

  private long nonce = 0;

  private Wei gasPrice = Wei.of(5000);

  private long gasLimit = 5000;

  private Optional<Address> to = Optional.empty();
  private Address sender = Address.fromHexString(String.format("%020x", 1));

  private Wei value = Wei.of(4);

  private Bytes payload = Bytes.EMPTY;

  private Optional<BigInteger> chainId = Optional.of(BigInteger.valueOf(1337));

  private Optional<Wei> maxPriorityFeePerGas = Optional.empty();
  private Optional<Wei> maxFeePerGas = Optional.empty();

  public Transaction createTransaction(final KeyPair keys) {
    final Transaction.Builder builder = Transaction.builder();
    builder
        .type(transactionType)
        .gasLimit(gasLimit)
        .gasPrice(gasPrice)
        .nonce(nonce)
        .payload(payload)
        .value(value)
        .sender(sender);

    to.ifPresent(builder::to);
    chainId.ifPresent(builder::chainId);

    maxPriorityFeePerGas.ifPresent(builder::maxPriorityFeePerGas);
    maxFeePerGas.ifPresent(builder::maxFeePerGas);

    return builder.signAndBuild(keys);
  }

  public TransactionTestFixture type(final TransactionType transactionType) {
    this.transactionType = transactionType;
    return this;
  }

  public TransactionTestFixture nonce(final long nonce) {
    this.nonce = nonce;
    return this;
  }

  public TransactionTestFixture gasPrice(final Wei gasPrice) {
    this.gasPrice = gasPrice;
    return this;
  }

  public TransactionTestFixture gasLimit(final long gasLimit) {
    this.gasLimit = gasLimit;
    return this;
  }

  public TransactionTestFixture to(final Optional<Address> to) {
    this.to = to;
    return this;
  }

  public TransactionTestFixture sender(final Address sender) {
    this.sender = sender;
    return this;
  }

  public TransactionTestFixture value(final Wei value) {
    this.value = value;
    return this;
  }

  public TransactionTestFixture payload(final Bytes payload) {
    this.payload = payload;
    return this;
  }

  public TransactionTestFixture chainId(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
    return this;
  }

  public TransactionTestFixture maxPriorityFeePerGas(final Optional<Wei> maxPriorityFeePerGas) {
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    return this;
  }

  public TransactionTestFixture maxFeePerGas(final Optional<Wei> maxFeePerGas) {
    this.maxFeePerGas = maxFeePerGas;
    return this;
  }
}
