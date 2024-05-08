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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The type Transaction pending result. */
@JsonPropertyOrder({
  "blockHash",
  "blockNumber",
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
  "value",
  "yParity",
  "v",
  "r",
  "s",
  "blobVersionedHashes"
})
public class TransactionPendingResult implements TransactionResult {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<AccessListEntry> accessList;

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
  private final String publicKey;
  private final String raw;
  private final String to;
  private final String type;
  private final String value;
  private final String yParity;
  private final String v;
  private final String r;
  private final String s;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<VersionedHash> versionedHashes;

  /**
   * Instantiates a new Transaction pending result.
   *
   * @param transaction the transaction
   */
  public TransactionPendingResult(final Transaction transaction) {
    final TransactionType transactionType = transaction.getType();
    this.accessList = transaction.getAccessList().orElse(null);
    this.chainId = transaction.getChainId().map(Quantity::create).orElse(null);
    this.from = transaction.getSender().toString();
    this.gas = Quantity.create(transaction.getGasLimit());
    this.maxPriorityFeePerGas =
        transaction.getMaxPriorityFeePerGas().map(Wei::toShortHexString).orElse(null);
    this.maxFeePerGas = transaction.getMaxFeePerGas().map(Wei::toShortHexString).orElse(null);
    this.maxFeePerBlobGas =
        transaction.getMaxFeePerBlobGas().map(Wei::toShortHexString).orElse(null);
    this.gasPrice = transaction.getGasPrice().map(Quantity::create).orElse(maxFeePerGas);
    this.hash = transaction.getHash().toString();
    this.input = transaction.getPayload().toString();
    this.nonce = Quantity.create(transaction.getNonce());
    this.publicKey = transaction.getPublicKey().orElse(null);
    this.raw =
        TransactionEncoder.encodeOpaqueBytes(transaction, EncodingContext.POOLED_TRANSACTION)
            .toString();
    this.to = transaction.getTo().map(Address::toHexString).orElse(null);
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
              ? Quantity.create(transaction.getV())
              : null;
    }
    this.value = Quantity.create(transaction.getValue());
    this.r = Quantity.create(transaction.getR());
    this.s = Quantity.create(transaction.getS());
    this.versionedHashes = transaction.getVersionedHashes().orElse(null);
  }

  /**
   * Gets access list.
   *
   * @return the access list
   */
  @JsonGetter(value = "accessList")
  public List<AccessListEntry> getAccessList() {
    return accessList;
  }

  /**
   * Gets chain id.
   *
   * @return the chain id
   */
  @JsonGetter(value = "chainId")
  public String getChainId() {
    return chainId;
  }

  /**
   * Gets from.
   *
   * @return the from
   */
  @JsonGetter(value = "from")
  public String getFrom() {
    return from;
  }

  /**
   * Gets gas.
   *
   * @return the gas
   */
  @JsonGetter(value = "gas")
  public String getGas() {
    return gas;
  }

  /**
   * Gets gas price.
   *
   * @return the gas price
   */
  @JsonGetter(value = "gasPrice")
  public String getGasPrice() {
    return gasPrice;
  }

  /**
   * Gets max priority fee per gas.
   *
   * @return the max priority fee per gas
   */
  @JsonGetter(value = "maxPriorityFeePerGas")
  public String getMaxPriorityFeePerGas() {
    return maxPriorityFeePerGas;
  }

  /**
   * Gets max fee per gas.
   *
   * @return the max fee per gas
   */
  @JsonGetter(value = "maxFeePerGas")
  public String getMaxFeePerGas() {
    return maxFeePerGas;
  }

  /**
   * Gets max fee per blob gas.
   *
   * @return the max fee per blob gas
   */
  @JsonGetter(value = "maxFeePerBlobGas")
  public String getMaxFeePerBlobGas() {
    return maxFeePerBlobGas;
  }

  /**
   * Gets hash.
   *
   * @return the hash
   */
  @JsonGetter(value = "hash")
  public String getHash() {
    return hash;
  }

  /**
   * Gets input.
   *
   * @return the input
   */
  @JsonGetter(value = "input")
  public String getInput() {
    return input;
  }

  /**
   * Gets nonce.
   *
   * @return the nonce
   */
  @JsonGetter(value = "nonce")
  public String getNonce() {
    return nonce;
  }

  /**
   * Gets public key.
   *
   * @return the public key
   */
  @JsonGetter(value = "publicKey")
  public String getPublicKey() {
    return publicKey;
  }

  /**
   * Gets raw.
   *
   * @return the raw
   */
  @JsonGetter(value = "raw")
  public String getRaw() {
    return raw;
  }

  /**
   * Gets to.
   *
   * @return the to
   */
  @JsonGetter(value = "to")
  public String getTo() {
    return to;
  }

  /**
   * Gets type.
   *
   * @return the type
   */
  @JsonGetter(value = "type")
  public String getType() {
    return type;
  }

  /**
   * Gets value.
   *
   * @return the value
   */
  @JsonGetter(value = "value")
  public String getValue() {
    return value;
  }

  /**
   * Gets y parity.
   *
   * @return the y parity
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter(value = "yParity")
  public String getYParity() {
    return yParity;
  }

  /**
   * Gets v.
   *
   * @return the v
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter(value = "v")
  public String getV() {
    return v;
  }

  /**
   * Gets r.
   *
   * @return the r
   */
  @JsonGetter(value = "r")
  public String getR() {
    return r;
  }

  /**
   * Gets s.
   *
   * @return the s
   */
  @JsonGetter(value = "s")
  public String getS() {
    return s;
  }

  /**
   * Gets block hash.
   *
   * @return the block hash
   */
  @JsonGetter(value = "blockHash")
  public String getBlockHash() {
    return null;
  }

  /**
   * Gets block number.
   *
   * @return the block number
   */
  @JsonGetter(value = "blockNumber")
  public String getBlockNumber() {
    return null;
  }

  /**
   * Gets transaction index.
   *
   * @return the transaction index
   */
  @JsonGetter(value = "transactionIndex")
  public String getTransactionIndex() {
    return null;
  }

  /**
   * Gets versioned hashes.
   *
   * @return the versioned hashes
   */
  @JsonGetter(value = "blobVersionedHashes")
  public List<VersionedHash> getVersionedHashes() {
    return versionedHashes;
  }
}
