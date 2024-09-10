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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

@JsonPropertyOrder({
  "accessList",
  "authorizationList",
  "blockHash",
  "blockNumber",
  "chainId",
  "from",
  "gas",
  "gasPrice",
  "maxPriorityFeePerGas",
  "maxFeePerGas",
  "maxFeePerBlobGas",
  "hash",
  "input",
  "nonce",
  "to",
  "transactionIndex",
  "type",
  "value",
  "yParity",
  "v",
  "r",
  "s",
  "blobVersionedHashes"
})
public class TransactionCompleteResult implements TransactionResult {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<AccessListEntry> accessList;

  private final String blockHash;
  private final String blockNumber;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String chainId;

  private final String from;
  private final String gas;
  private final String gasPrice;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String maxPriorityFeePerGas;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String maxFeePerGas;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String maxFeePerBlobGas;

  private final String hash;
  private final String input;
  private final String nonce;
  private final String to;
  private final String transactionIndex;
  private final String type;
  private final String value;
  private final String yParity;
  private final String v;
  private final String r;
  private final String s;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<VersionedHash> versionedHashes;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<CodeDelegation> authorizationList;

  public TransactionCompleteResult(final TransactionWithMetadata tx) {
    final Transaction transaction = tx.getTransaction();
    final TransactionType transactionType = transaction.getType();
    this.accessList = transaction.getAccessList().orElse(null);
    this.blockHash = tx.getBlockHash().get().toString();
    this.blockNumber = Quantity.create(tx.getBlockNumber().get());
    this.chainId = transaction.getChainId().map(Quantity::create).orElse(null);
    this.from = transaction.getSender().toString();
    this.gas = Quantity.create(transaction.getGasLimit());
    this.maxPriorityFeePerGas =
        tx.getTransaction().getMaxPriorityFeePerGas().map(Wei::toShortHexString).orElse(null);
    this.maxFeePerGas =
        tx.getTransaction().getMaxFeePerGas().map(Wei::toShortHexString).orElse(null);
    this.maxFeePerBlobGas =
        transaction.getMaxFeePerBlobGas().map(Wei::toShortHexString).orElse(null);
    this.gasPrice =
        Quantity.create(
            transaction
                .getGasPrice()
                .orElseGet(() -> transaction.getEffectiveGasPrice(tx.getBaseFee())));
    this.hash = transaction.getHash().toString();
    this.input = transaction.getPayload().toString();
    this.nonce = Quantity.create(transaction.getNonce());
    this.to = transaction.getTo().map(Bytes::toHexString).orElse(null);
    this.transactionIndex = Quantity.create(tx.getTransactionIndex().get());
    if (transactionType == TransactionType.FRONTIER) {
      this.type = Quantity.create(0);
      this.yParity = null;
      this.v = Quantity.create(transaction.getV());
    } else {
      this.type = Quantity.create(transactionType.getSerializedType());
      this.yParity = Quantity.create(transaction.getYParity());
      this.v =
          (transactionType == TransactionType.ACCESS_LIST
                      || transactionType == TransactionType.EIP1559)
                  || transactionType == TransactionType.DELEGATE_CODE
              ? Quantity.create(transaction.getYParity())
              : null;
    }
    this.value = Quantity.create(transaction.getValue());
    this.r = Quantity.create(transaction.getR());
    this.s = Quantity.create(transaction.getS());
    this.versionedHashes = transaction.getVersionedHashes().orElse(null);
    this.authorizationList = transaction.getCodeDelegationList().orElse(null);
  }

  @JsonGetter(value = "accessList")
  public List<AccessListEntry> getAccessList() {
    return accessList;
  }

  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return blockHash;
  }

  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return blockNumber;
  }

  @JsonGetter(value = "chainId")
  public String getChainId() {
    return chainId;
  }

  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  @JsonGetter(value = "gas")
  public String getGas() {
    return gas;
  }

  @JsonGetter(value = "maxPriorityFeePerGas")
  public String getMaxPriorityFeePerGas() {
    return maxPriorityFeePerGas;
  }

  @JsonGetter(value = "maxFeePerGas")
  public String getMaxFeePerGas() {
    return maxFeePerGas;
  }

  @JsonGetter(value = "maxFeePerBlobGas")
  public String getMaxFeePerBlobGas() {
    return maxFeePerBlobGas;
  }

  @JsonGetter(value = "gasPrice")
  public String getGasPrice() {
    return gasPrice;
  }

  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  @JsonGetter(value = "input")
  public String getInput() {
    return input;
  }

  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return transactionIndex;
  }

  @JsonGetter(value = "type")
  public String getType() {
    return type;
  }

  @JsonGetter(value = "value")
  public String getValue() {
    return value;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter(value = "yParity")
  public String getYParity() {
    return yParity;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter(value = "v")
  public String getV() {
    return v;
  }

  @JsonGetter(value = "r")
  public String getR() {
    return r;
  }

  @JsonGetter(value = "s")
  public String getS() {
    return s;
  }

  @JsonGetter(value = "blobVersionedHashes")
  public List<VersionedHash> getVersionedHashes() {
    return versionedHashes;
  }

  @JsonGetter(value = "authorizationList")
  public List<CodeDelegation> getAuthorizationList() {
    return authorizationList;
  }
}
