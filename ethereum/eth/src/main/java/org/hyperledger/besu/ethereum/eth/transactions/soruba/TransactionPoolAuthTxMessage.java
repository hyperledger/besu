/*
 * Copyright Soruba.
 * https://www.gnu.org/licenses/gpl-3.0.html
 * SPDX-License-Identifier: GNU GPLv3
 */
package org.hyperledger.besu.ethereum.eth.transactions.soruba;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPoolAuthTxMessage {
  private final String hash;
  private final String from;
  private final String to;
  private final String value;
  private final String input;
  private long gas;
  private String gasPrice;
  private long nonce;
  private final String rawTransaction;
  private final Optional<String> rejectedReason;

  @JsonCreator
  public TransactionPoolAuthTxMessage(
      @JsonProperty("hash") final String hash,
      @JsonProperty("from") final String from,
      @JsonProperty("to") final String to,
      @JsonProperty("value") final String value,
      @JsonProperty("input") final String input,
      @JsonProperty("gas") final long gas,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("nonce") final long nonce,
      @JsonProperty("rawTransaction") final String rawTransaction,
      @JsonProperty("rejectedReason") final Optional<String> rejectedReason) {
    this.hash = hash;
    this.from = from;
    this.to = to;
    this.value = value;
    this.input = input;
    this.gas = gas;
    this.gasPrice = gasPrice;
    this.nonce = nonce;
    this.rawTransaction = rawTransaction;
    this.rejectedReason = rejectedReason;
  }

  public String getHash() {
    return hash;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public String getValue() {
    return value;
  }

  public String getInput() {
    return input;
  }

  public long getGas() {
    return gas;
  }

  public void setGas(final long gas) {
    this.gas = gas;
  }

  public String getGasPrice() {
    return gasPrice;
  }

  public void setGasPrice(final String gasPrice) {
    this.gasPrice = gasPrice;
  }

  public long getNonce() {
    return nonce;
  }

  public void setNonce(final long nonce) {
    this.nonce = nonce;
  }

  public String getRawTransaction() {
    return this.rawTransaction;
  }

  public Optional<String> getRejectedReason() {
    return this.rejectedReason;
  }

  @Override
  public String toString() {
    return "TransactionPoolAuthTxMessage{"
        + "hash='"
        + hash
        + '\''
        + ", from='"
        + from
        + '\''
        + ", to='"
        + to
        + '\''
        + ", value='"
        + value
        + '\''
        + ", input='"
        + input
        + '\''
        + ", gas="
        + gas
        + ", gasPrice='"
        + gasPrice
        + '\''
        + ", nonce="
        + nonce
        + ", rawTransaction='"
        + rawTransaction
        + '\''
        + '}';
  }
}
