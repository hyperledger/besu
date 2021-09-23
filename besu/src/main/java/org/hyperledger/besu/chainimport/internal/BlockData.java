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
package org.hyperledger.besu.chainimport.internal;

import org.hyperledger.besu.chainimport.internal.TransactionData.NonceProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

@JsonIgnoreProperties("comment")
public class BlockData {

  private final Optional<Long> number;
  private final Optional<Hash> parentHash;
  private final List<TransactionData> transactionData;
  private final Optional<Address> coinbase;
  private final Optional<Bytes> extraData;

  @JsonCreator
  public BlockData(
      @JsonProperty("number") final Optional<String> number,
      @JsonProperty("parentHash") final Optional<String> parentHash,
      @JsonProperty("coinbase") final Optional<String> coinbase,
      @JsonProperty("extraData") final Optional<String> extraData,
      @JsonProperty("transactions") final List<TransactionData> transactions) {
    this.number = number.map(UInt256::fromHexString).map(UInt256::toLong);
    this.parentHash = parentHash.map(Bytes32::fromHexString).map(Hash::wrap);
    this.coinbase = coinbase.map(Address::fromHexString);
    this.extraData = extraData.map(Bytes::fromHexStringLenient);
    this.transactionData = transactions;
  }

  public Optional<Long> getNumber() {
    return number;
  }

  public Optional<Hash> getParentHash() {
    return parentHash;
  }

  public Optional<Address> getCoinbase() {
    return coinbase;
  }

  public Optional<Bytes> getExtraData() {
    return extraData;
  }

  public Stream<Transaction> streamTransactions(final WorldState worldState) {
    final NonceProvider nonceProvider = getNonceProvider(worldState);
    return transactionData.stream().map((tx) -> tx.getSignedTransaction(nonceProvider));
  }

  public NonceProvider getNonceProvider(final WorldState worldState) {
    final HashMap<Address, Long> currentNonceValues = new HashMap<>();
    return (Address address) ->
        currentNonceValues.compute(
            address,
            (addr, currentValue) -> {
              if (currentValue == null) {
                return Optional.ofNullable(worldState.get(address))
                    .map(Account::getNonce)
                    .orElse(0L);
              }
              return currentValue + 1;
            });
  }
}
