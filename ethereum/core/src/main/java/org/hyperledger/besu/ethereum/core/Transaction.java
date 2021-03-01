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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** An operation submitted by an external actor to be applied to the system. */
public class Transaction implements org.hyperledger.besu.plugin.data.Transaction {

  // Used for transactions that are not tied to a specific chain
  // (e.g. does not have a chain id associated with it).
  public static final BigInteger REPLAY_UNPROTECTED_V_BASE = BigInteger.valueOf(27);
  public static final BigInteger REPLAY_UNPROTECTED_V_BASE_PLUS_1 = BigInteger.valueOf(28);

  public static final BigInteger REPLAY_PROTECTED_V_BASE = BigInteger.valueOf(35);

  public static final BigInteger GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN = BigInteger.valueOf(37);
  public static final BigInteger GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX = BigInteger.valueOf(38);

  // The v signature parameter starts at 36 because 1 is the first valid chainId so:
  // chainId > 1 implies that 2 * chainId + V_BASE > 36.
  public static final BigInteger REPLAY_PROTECTED_V_MIN = BigInteger.valueOf(36);

  public static final BigInteger TWO = BigInteger.valueOf(2);

  private final long nonce;

  private final Wei gasPrice;

  private final Wei gasPremium;

  private final Wei feeCap;

  private final long gasLimit;

  private final Optional<Address> to;

  private final Wei value;

  private final SECPSignature signature;

  private final Bytes payload;

  private final List<AccessListEntry> accessList;

  private final Optional<BigInteger> chainId;

  private final Optional<BigInteger> v;

  // Caches a "hash" of a portion of the transaction used for sender recovery.
  // Note that this hash does not include the transaction signature so it does not
  // fully identify the transaction (use the result of the {@code hash()} for that).
  // It is only used to compute said signature and recover the sender from it.
  private volatile Bytes32 hashNoSignature;

  // Caches the transaction sender.
  protected volatile Address sender;

  // Caches the hash used to uniquely identify the transaction.
  protected volatile Hash hash;
  private final TransactionType transactionType;

  private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

  public static Builder builder() {
    return new Builder();
  }

  public static Transaction readFrom(final RLPInput rlpInput) {
    return TransactionDecoder.decodeForWire(rlpInput);
  }

  /**
   * Instantiates a transaction instance.
   *
   * @param transactionType the transaction type
   * @param nonce the nonce
   * @param gasPrice the gas price
   * @param gasPremium the gas premium
   * @param feeCap the fee cap
   * @param gasLimit the gas limit
   * @param to the transaction recipient
   * @param value the value being transferred to the recipient
   * @param signature the signature
   * @param payload the payload
   * @param accessList the list of addresses/storage slots this transaction intends to preload
   * @param sender the transaction sender
   * @param chainId the chain id to apply the transaction to
   * @param v the v value. This is only passed in directly for GoQuorum private transactions
   *     (v=37|38). For all other transactions, the v value is derived from the signature. If v is
   *     provided here, the chain id must be empty.
   *     <p>The {@code to} will be an {@code Optional.empty()} for a contract creation transaction;
   *     otherwise it should contain an address.
   *     <p>The {@code chainId} must be greater than 0 to be applied to a specific chain; otherwise
   *     it will default to any chain.
   */
  public Transaction(
      final TransactionType transactionType,
      final long nonce,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final List<AccessListEntry> accessList,
      final Address sender,
      final Optional<BigInteger> chainId,
      final Optional<BigInteger> v) {
    if (v.isPresent() && chainId.isPresent()) {
      throw new IllegalStateException(
          String.format("chainId '%s' and v '%s' cannot both be provided", chainId.get(), v.get()));
    }
    this.transactionType = transactionType;
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.gasPremium = gasPremium;
    this.feeCap = feeCap;
    this.gasLimit = gasLimit;
    this.to = to;
    this.value = value;
    this.signature = signature;
    this.payload = payload;
    this.accessList = accessList;
    this.sender = sender;
    this.chainId = chainId;
    this.v = v;
  }

  public Transaction(
      final long nonce,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId,
      final Optional<BigInteger> v) {
    this(
        TransactionType.FRONTIER,
        nonce,
        gasPrice,
        gasPremium,
        feeCap,
        gasLimit,
        to,
        value,
        signature,
        payload,
        emptyList(),
        sender,
        chainId,
        v);
  }

  /**
   * Instantiates a transaction instance.
   *
   * @param nonce the nonce
   * @param gasPrice the gas price
   * @param gasLimit the gas limit
   * @param to the transaction recipient
   * @param value the value being transferred to the recipient
   * @param signature the signature
   * @param payload the payload
   * @param sender the transaction sender
   * @param chainId the chain id to apply the transaction to
   *     <p>The {@code to} will be an {@code Optional.empty()} for a contract creation transaction;
   *     otherwise it should contain an address.
   *     <p>The {@code chainId} must be greater than 0 to be applied to a specific chain; otherwise
   *     it will default to any chain.
   */
  public Transaction(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId) {
    this(
        nonce,
        gasPrice,
        null,
        null,
        gasLimit,
        to,
        value,
        signature,
        payload,
        sender,
        chainId,
        Optional.empty());
  }
  /**
   * Instantiates a transaction instance.
   *
   * @param nonce the nonce
   * @param gasPrice the gas price
   * @param gasLimit the gas limit
   * @param to the transaction recipient
   * @param value the value being transferred to the recipient
   * @param signature the signature
   * @param payload the payload
   * @param sender the transaction sender
   * @param chainId the chain id to apply the transaction to
   * @param v the v value (only passed in directly for GoQuorum private transactions)
   *     <p>The {@code to} will be an {@code Optional.empty()} for a contract creation transaction;
   *     otherwise it should contain an address.
   *     <p>The {@code chainId} must be greater than 0 to be applied to a specific chain; otherwise
   *     it will default to any chain.
   */
  public Transaction(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId,
      final Optional<BigInteger> v) {
    this(nonce, gasPrice, null, null, gasLimit, to, value, signature, payload, sender, chainId, v);
  }

  /**
   * Returns the transaction nonce.
   *
   * @return the transaction nonce
   */
  @Override
  public long getNonce() {
    return nonce;
  }

  /**
   * Return the transaction gas price.
   *
   * @return the transaction gas price
   */
  @Override
  public Wei getGasPrice() {
    return gasPrice;
  }

  /**
   * Return the transaction gas premium.
   *
   * @return the transaction gas premium
   */
  @Override
  public Optional<Quantity> getGasPremium() {
    return Optional.ofNullable(gasPremium);
  }

  /**
   * Return the transaction fee cap.
   *
   * @return the transaction fee cap
   */
  @Override
  public Optional<Quantity> getFeeCap() {
    return Optional.ofNullable(feeCap);
  }

  /**
   * Returns the transaction gas limit.
   *
   * @return the transaction gas limit
   */
  @Override
  public long getGasLimit() {
    return gasLimit;
  }

  /**
   * Returns the transaction recipient.
   *
   * <p>The {@code Optional<Address>} will be {@code Optional.empty()} if the transaction is a
   * contract creation; otherwise it will contain the message call transaction recipient.
   *
   * @return the transaction recipient if a message call; otherwise {@code Optional.empty()}
   */
  @Override
  public Optional<Address> getTo() {
    return to;
  }

  /**
   * Returns the value transferred in the transaction.
   *
   * @return the value transferred in the transaction
   */
  @Override
  public Wei getValue() {
    return value;
  }

  /**
   * Returns the signature used to sign the transaction.
   *
   * @return the signature used to sign the transaction
   */
  public SECPSignature getSignature() {
    return signature;
  }

  /**
   * Returns the transaction payload.
   *
   * @return the transaction payload
   */
  @Override
  public Bytes getPayload() {
    return payload;
  }

  /**
   * Returns the payload if this is a contract creation transaction.
   *
   * @return if present the init code
   */
  @Override
  public Optional<Bytes> getInit() {
    return getTo().isPresent() ? Optional.empty() : Optional.of(payload);
  }

  /**
   * Returns the payload if this is a message call transaction.
   *
   * @return if present the init code
   */
  @Override
  public Optional<Bytes> getData() {
    return getTo().isPresent() ? Optional.of(payload) : Optional.empty();
  }

  public List<AccessListEntry> getAccessList() {
    return accessList;
  }

  /**
   * Return the transaction chain id (if it exists)
   *
   * <p>The {@code OptionalInt} will be {@code OptionalInt.empty()} if the transaction is not tied
   * to a specific chain.
   *
   * @return the transaction chain id if it exists; otherwise {@code OptionalInt.empty()}
   */
  @Override
  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  /**
   * Returns the transaction sender.
   *
   * @return the transaction sender
   */
  @Override
  public Address getSender() {
    if (sender == null) {
      final SECPPublicKey publicKey =
          signatureAlgorithm
              .recoverPublicKeyFromSignature(getOrComputeSenderRecoveryHash(), signature)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot recover public key from signature for " + this));
      sender = Address.extract(Hash.hash(publicKey.getEncodedBytes()));
    }
    return sender;
  }

  /**
   * Returns the public key extracted from the signature.
   *
   * @return the public key
   */
  public Optional<String> getPublicKey() {
    return signatureAlgorithm
        .recoverPublicKeyFromSignature(getOrComputeSenderRecoveryHash(), signature)
        .map(SECPPublicKey::toString);
  }

  private Bytes32 getOrComputeSenderRecoveryHash() {
    if (hashNoSignature == null) {
      hashNoSignature =
          computeSenderRecoveryHash(
              transactionType,
              nonce,
              gasPrice,
              gasPremium,
              feeCap,
              gasLimit,
              to,
              value,
              payload,
              accessList,
              chainId);
    }
    return hashNoSignature;
  }

  /**
   * Writes the transaction to RLP
   *
   * @param out the output to write the transaction to
   */
  public void writeTo(final RLPOutput out) {
    TransactionEncoder.encodeForWire(this, out);
  }

  @Override
  public BigInteger getR() {
    return signature.getR();
  }

  @Override
  public BigInteger getS() {
    return signature.getS();
  }

  @Override
  public BigInteger getV() {
    if (this.v.isPresent()) {
      return this.v.get();
    }

    final BigInteger recId = BigInteger.valueOf(signature.getRecId());
    if (chainId.isEmpty()) {
      return recId.add(REPLAY_UNPROTECTED_V_BASE);
    } else {
      return recId.add(REPLAY_PROTECTED_V_BASE).add(TWO.multiply(chainId.get()));
    }
  }

  /**
   * Returns the transaction hash.
   *
   * @return the transaction hash
   */
  @Override
  public Hash getHash() {
    if (hash == null) {
      hash = Hash.hash(TransactionEncoder.encodeOpaqueBytes(this));
    }
    return hash;
  }

  /**
   * Returns whether the transaction is a contract creation
   *
   * @return {@code true} if this is a contract-creation transaction; otherwise {@code false}
   */
  public boolean isContractCreation() {
    return getTo().isEmpty();
  }

  /**
   * Calculates the up-front cost for the gas the transaction can use.
   *
   * @return the up-front cost for the gas the transaction can use.
   */
  public Wei getUpfrontGasCost() {
    return getUpfrontGasCost(getGasPrice());
  }

  /**
   * Calculates the up-front cost for the gas the transaction can use.
   *
   * @param gasPrice the gas price to use
   * @return the up-front cost for the gas the transaction can use.
   */
  public Wei getUpfrontGasCost(final Wei gasPrice) {
    return Wei.of(getGasLimit()).multiply(gasPrice);
  }

  /**
   * Calculates the up-front cost for the transaction.
   *
   * <p>The up-front cost is paid by the sender account before the transaction is executed. The
   * sender must have the amount in its account balance to execute and some of this amount may be
   * refunded after the transaction has executed.
   *
   * @return the up-front gas cost for the transaction
   */
  public Wei getUpfrontCost() {
    return getUpfrontGasCost().add(getValue());
  }

  @Override
  public TransactionType getType() {
    return this.transactionType;
  }

  /**
   * Returns whether or not the transaction is a GoQuorum private transaction. <br>
   * <br>
   * A GoQuorum private transaction has its <i>v</i> value equal to 37 or 38.
   *
   * @return true if GoQuorum private transaction, false otherwise
   */
  public boolean isGoQuorumPrivateTransaction() {
    return v.map(
            value ->
                GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MIN.equals(value)
                    || GO_QUORUM_PRIVATE_TRANSACTION_V_VALUE_MAX.equals(value))
        .orElse(false);
  }

  private static Bytes32 computeSenderRecoveryHash(
      final TransactionType transactionType,
      final long nonce,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final List<AccessListEntry> accessList,
      final Optional<BigInteger> chainId) {
    final Bytes preimage;
    switch (transactionType) {
      case FRONTIER:
        preimage = frontierPreimage(nonce, gasPrice, gasLimit, to, value, payload, chainId);
        break;
      case EIP1559:
        preimage =
            eip1559Preimage(
                nonce, gasPrice, gasPremium, feeCap, gasLimit, to, value, payload, chainId);
        break;
      case ACCESS_LIST:
        preimage =
            accessListPreimage(nonce, gasPrice, gasLimit, to, value, payload, accessList, chainId);
        break;
      default:
        throw new IllegalStateException(
            "Developer error. Didn't specify signing hash preimage computation");
    }
    return keccak256(preimage);
  }

  private static Bytes frontierPreimage(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId) {
    return RLP.encode(
        rlpOutput -> {
          rlpOutput.startList();
          rlpOutput.writeLongScalar(nonce);
          rlpOutput.writeUInt256Scalar(gasPrice);
          rlpOutput.writeLongScalar(gasLimit);
          rlpOutput.writeBytes(to.map(Bytes::copy).orElse(Bytes.EMPTY));
          rlpOutput.writeUInt256Scalar(value);
          rlpOutput.writeBytes(payload);
          if (chainId.isPresent()) {
            rlpOutput.writeBigIntegerScalar(chainId.get());
            rlpOutput.writeUInt256Scalar(UInt256.ZERO);
            rlpOutput.writeUInt256Scalar(UInt256.ZERO);
          }
          rlpOutput.endList();
        });
  }

  private static Bytes eip1559Preimage(
      final long nonce,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId) {
    return RLP.encode(
        rlpOutput -> {
          rlpOutput.startList();
          rlpOutput.writeLongScalar(nonce);
          rlpOutput.writeUInt256Scalar(gasPrice);
          rlpOutput.writeLongScalar(gasLimit);
          rlpOutput.writeBytes(to.map(Bytes::copy).orElse(Bytes.EMPTY));
          rlpOutput.writeUInt256Scalar(value);
          rlpOutput.writeBytes(payload);
          rlpOutput.writeUInt256Scalar(gasPremium);
          rlpOutput.writeUInt256Scalar(feeCap);
          if (chainId.isPresent()) {
            rlpOutput.writeBigIntegerScalar(chainId.get());
            rlpOutput.writeUInt256Scalar(UInt256.ZERO);
            rlpOutput.writeUInt256Scalar(UInt256.ZERO);
          }
          rlpOutput.endList();
        });
  }

  private static Bytes accessListPreimage(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final List<AccessListEntry> accessList,
      final Optional<BigInteger> chainId) {
    final Bytes encode =
        RLP.encode(
            rlpOutput -> {
              rlpOutput.startList();
              TransactionEncoder.encodeAccessListInner(
                  chainId, nonce, gasPrice, gasLimit, to, value, payload, accessList, rlpOutput);
              rlpOutput.endList();
            });
    return Bytes.concatenate(Bytes.of(TransactionType.ACCESS_LIST.getSerializedType()), encode);
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof Transaction)) {
      return false;
    }
    final Transaction that = (Transaction) other;
    return Objects.equals(this.chainId, that.chainId)
        && Objects.equals(this.gasLimit, that.gasLimit)
        && Objects.equals(this.gasPrice, that.gasPrice)
        && Objects.equals(this.gasPremium, that.gasPremium)
        && Objects.equals(this.feeCap, that.feeCap)
        && Objects.equals(this.nonce, that.nonce)
        && Objects.equals(this.payload, that.payload)
        && Objects.equals(this.signature, that.signature)
        && Objects.equals(this.to, that.to)
        && Objects.equals(this.value, that.value)
        && Objects.equals(this.getV(), that.getV());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        nonce, gasPrice, gasPremium, feeCap, gasLimit, to, value, payload, signature, chainId, v);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(isContractCreation() ? "ContractCreation" : "MessageCall").append("{");
    sb.append("type=").append(getType()).append(", ");
    sb.append("nonce=").append(getNonce()).append(", ");
    sb.append("gasPrice=").append(getGasPrice()).append(", ");
    if (getGasPremium().isPresent() && getFeeCap().isPresent()) {
      sb.append("gasPremium=").append(getGasPremium()).append(", ");
      sb.append("feeCap=").append(getFeeCap()).append(", ");
    }
    sb.append("gasLimit=").append(getGasLimit()).append(", ");
    if (getTo().isPresent()) sb.append("to=").append(getTo().get()).append(", ");
    sb.append("value=").append(getValue()).append(", ");
    sb.append("sig=").append(getSignature()).append(", ");
    if (chainId.isPresent()) sb.append("chainId=").append(getChainId().get()).append(", ");
    if (v.isPresent()) sb.append("v=").append(v.get()).append(", ");
    sb.append("payload=").append(getPayload()).append(", ");
    if (transactionType.equals(TransactionType.ACCESS_LIST)) {
      sb.append("accessList=").append(accessList);
    }
    return sb.append("}").toString();
  }

  public Optional<Address> contractAddress() {
    if (isContractCreation()) {
      return Optional.of(Address.contractAddress(getSender(), getNonce()));
    }
    return Optional.empty();
  }

  public static class Builder {

    protected TransactionType transactionType;

    protected long nonce = -1L;

    protected Wei gasPrice;

    protected Wei gasPremium;

    protected Wei feeCap;

    protected long gasLimit = -1L;

    protected Optional<Address> to = Optional.empty();

    protected Wei value;

    protected SECPSignature signature;

    protected Bytes payload;

    protected List<AccessListEntry> accessList = emptyList();

    protected Address sender;

    protected Optional<BigInteger> chainId = Optional.empty();

    protected Optional<BigInteger> v = Optional.empty();

    public Builder type(final TransactionType transactionType) {
      this.transactionType = transactionType;
      return this;
    }

    public Builder chainId(final BigInteger chainId) {
      this.chainId = Optional.of(chainId);
      return this;
    }

    public Builder v(final BigInteger v) {
      this.v = Optional.of(v);
      return this;
    }

    public Builder gasPrice(final Wei gasPrice) {
      this.gasPrice = gasPrice;
      return this;
    }

    public Builder gasPremium(final Wei gasPremium) {
      this.gasPremium = gasPremium;
      return this;
    }

    public Builder feeCap(final Wei feeCap) {
      this.feeCap = feeCap;
      return this;
    }

    public Builder gasLimit(final long gasLimit) {
      this.gasLimit = gasLimit;
      return this;
    }

    public Builder nonce(final long nonce) {
      this.nonce = nonce;
      return this;
    }

    public Builder value(final Wei value) {
      this.value = value;
      return this;
    }

    public Builder to(final Address to) {
      this.to = Optional.ofNullable(to);
      return this;
    }

    public Builder payload(final Bytes payload) {
      this.payload = payload;
      return this;
    }

    public Builder accessList(final List<AccessListEntry> accessList) {
      this.accessList = accessList;
      return this;
    }

    public Builder sender(final Address sender) {
      this.sender = sender;
      return this;
    }

    public Builder signature(final SECPSignature signature) {
      this.signature = signature;
      return this;
    }

    public Builder guessType() {
      if (!accessList.isEmpty()) {
        transactionType = TransactionType.ACCESS_LIST;
      } else if (gasPremium != null || feeCap != null) {
        transactionType = TransactionType.EIP1559;
      } else {
        transactionType = TransactionType.FRONTIER;
      }
      return this;
    }

    public Transaction build() {
      return new Transaction(
          transactionType,
          nonce,
          gasPrice,
          gasPremium,
          feeCap,
          gasLimit,
          to,
          value,
          signature,
          payload,
          accessList,
          sender,
          chainId,
          v);
    }

    public Transaction signAndBuild(final KeyPair keys) {
      checkState(
          signature == null, "The transaction signature has already been provided to this builder");
      signature(computeSignature(keys));
      sender(Address.extract(Hash.hash(keys.getPublicKey().getEncodedBytes())));
      return build();
    }

    SECPSignature computeSignature(final KeyPair keys) {
      return SignatureAlgorithmFactory.getInstance()
          .sign(
              computeSenderRecoveryHash(
                  transactionType,
                  nonce,
                  gasPrice,
                  gasPremium,
                  feeCap,
                  gasLimit,
                  to,
                  value,
                  payload,
                  accessList,
                  chainId),
              keys);
    }
  }
}
