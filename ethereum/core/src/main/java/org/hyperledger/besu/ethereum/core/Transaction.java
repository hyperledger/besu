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
import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Quantity;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** An operation submitted by an external actor to be applied to the system. */
public class Transaction implements org.hyperledger.besu.plugin.data.Transaction {

  // Used for transactions that are not tied to a specific chain
  // (e.g. does not have a chain id associated with it).
  private static final BigInteger REPLAY_UNPROTECTED_V_BASE = BigInteger.valueOf(27);
  private static final BigInteger REPLAY_UNPROTECTED_V_BASE_PLUS_1 = BigInteger.valueOf(28);

  private static final BigInteger REPLAY_PROTECTED_V_BASE = BigInteger.valueOf(35);

  // The v signature parameter starts at 36 because 1 is the first valid chainId so:
  // chainId > 1 implies that 2 * chainId + V_BASE > 36.
  private static final BigInteger REPLAY_PROTECTED_V_MIN = BigInteger.valueOf(36);

  private static final BigInteger TWO = BigInteger.valueOf(2);

  private final long nonce;

  private final Wei gasPrice;

  private final Wei gasPremium;

  private final Wei feeCap;

  private final long gasLimit;

  private final Optional<Address> to;

  private final Wei value;

  private final SECP256K1.Signature signature;

  private final Bytes payload;

  private final Optional<BigInteger> chainId;

  // Caches a "hash" of a portion of the transaction used for sender recovery.
  // Note that this hash does not include the transaction signature so it does not
  // fully identify the transaction (use the result of the {@code hash()} for that).
  // It is only used to compute said signature and recover the sender from it.
  private volatile Bytes32 hashNoSignature;

  // Caches the transaction sender.
  protected volatile Address sender;

  // Caches the hash used to uniquely identify the transaction.
  protected volatile Hash hash;

  public static Builder builder() {
    return new Builder();
  }

  public static Transaction readFrom(final RLPInput input) throws RLPException {
    if (ExperimentalEIPs.eip1559Enabled) {
      return readFromExperimental(input);
    }
    input.enterList();
    final Builder builder =
        builder()
            .nonce(input.readLongScalar())
            .gasPrice(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(input.readUInt256Scalar()))
            .payload(input.readBytes());

    final BigInteger v = input.readBigIntegerScalar();
    final byte recId;
    Optional<BigInteger> chainId = Optional.empty();
    if (v.equals(REPLAY_UNPROTECTED_V_BASE) || v.equals(REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0) {
      chainId = Optional.of(v.subtract(REPLAY_PROTECTED_V_BASE).divide(TWO));
      recId = v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }
    final BigInteger r = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);

    input.leaveList();

    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  public static Transaction readFromExperimental(final RLPInput input) throws RLPException {
    input.enterList();

    final Builder builder =
        builder()
            .nonce(input.readLongScalar())
            .gasPrice(Wei.of(input.readUInt256Scalar()))
            .gasLimit(input.readLongScalar())
            .to(input.readBytes(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(Wei.of(input.readUInt256Scalar()))
            .payload(input.readBytes());

    final Bytes maybeGasPremiumOrV = input.readBytes();
    final Bytes maybeFeeCapOrR = input.readBytes();
    final Bytes maybeVOrS = input.readBytes();
    final BigInteger v, r, s;
    // if this is the end of the list we are processing a legacy transaction
    if (input.isEndOfCurrentList()) {
      v = maybeGasPremiumOrV.toUnsignedBigInteger();
      r = maybeFeeCapOrR.toUnsignedBigInteger();
      s = maybeVOrS.toUnsignedBigInteger();
    } else {
      // otherwise this is an EIP-1559 transaction
      builder
          .gasPremium(Wei.of(maybeGasPremiumOrV.toBigInteger()))
          .feeCap(Wei.of(maybeFeeCapOrR.toBigInteger()));
      v = maybeVOrS.toBigInteger();
      r = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
      s = input.readUInt256Scalar().toBytes().toUnsignedBigInteger();
    }
    final byte recId;
    Optional<BigInteger> chainId = Optional.empty();
    if (v.equals(REPLAY_UNPROTECTED_V_BASE) || v.equals(REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0) {
      chainId = Optional.of(v.subtract(REPLAY_PROTECTED_V_BASE).divide(TWO));
      recId = v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);
    input.leaveList();
    chainId.ifPresent(builder::chainId);
    return builder.signature(signature).build();
  }

  /**
   * Instantiates a transaction instance.
   *
   * @param nonce the nonce
   * @param gasPrice the gas price
   * @param gasPremium the gas premium
   * @param feeCap the fee cap
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
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECP256K1.Signature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId) {
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.gasPremium = gasPremium;
    this.feeCap = feeCap;
    this.gasLimit = gasLimit;
    this.to = to;
    this.value = value;
    this.signature = signature;
    this.payload = payload;
    this.sender = sender;
    this.chainId = chainId;
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
   */
  public Transaction(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECP256K1.Signature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId) {
    this(nonce, gasPrice, null, null, gasLimit, to, value, signature, payload, sender, chainId);
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
  public SECP256K1.Signature getSignature() {
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
      final SECP256K1.PublicKey publicKey =
          SECP256K1.PublicKey.recoverFromSignature(getOrComputeSenderRecoveryHash(), signature)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot recover public key from " + "signature for " + this));
      sender = Address.extract(Hash.hash(publicKey.getEncodedBytes()));
    }
    return sender;
  }

  private Bytes32 getOrComputeSenderRecoveryHash() {
    if (hashNoSignature == null) {
      hashNoSignature =
          computeSenderRecoveryHash(
              nonce,
              gasPrice,
              gasPremium,
              feeCap,
              gasLimit,
              to.orElse(null),
              value,
              payload,
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
    out.startList();

    out.writeLongScalar(getNonce());
    out.writeUInt256Scalar(getGasPrice());
    out.writeLongScalar(getGasLimit());
    out.writeBytes(getTo().isPresent() ? getTo().get() : Bytes.EMPTY);
    out.writeUInt256Scalar(getValue());
    out.writeBytes(getPayload());
    if (ExperimentalEIPs.eip1559Enabled && gasPremium != null && feeCap != null) {
      out.writeUInt256Scalar(gasPremium);
      out.writeUInt256Scalar(feeCap);
    }
    writeSignature(out);

    out.endList();
  }

  private void writeSignature(final RLPOutput out) {
    out.writeBigIntegerScalar(getV());
    out.writeBigIntegerScalar(getSignature().getR());
    out.writeBigIntegerScalar(getSignature().getS());
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
    final BigInteger v;
    final BigInteger recId = BigInteger.valueOf(signature.getRecId());
    if (chainId.isEmpty()) {
      v = recId.add(REPLAY_UNPROTECTED_V_BASE);
    } else {
      v = recId.add(REPLAY_PROTECTED_V_BASE).add(TWO.multiply(chainId.get()));
    }
    return v;
  }

  /**
   * Returns the transaction hash.
   *
   * @return the transaction hash
   */
  @Override
  public Hash getHash() {
    if (hash == null) {
      final Bytes rlp = RLP.encode(this::writeTo);
      hash = Hash.hash(rlp);
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
    return Wei.of(getGasLimit()).multiply(getGasPrice());
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

  /**
   * Returns whether or not the transaction is a legacy transaction.
   *
   * @return true if legacy transaction, false otherwise
   */
  @Override
  public boolean isFrontierTransaction() {
    return getGasPrice() != null && (getGasPremium().isEmpty() && getFeeCap().isEmpty());
  }

  /**
   * Returns whether or not the transaction is an EIP-1559 transaction.
   *
   * @return true if EIP-1559 transaction, false otherwise
   */
  @Override
  public boolean isEIP1559Transaction() {
    return getGasPremium().isPresent() && getFeeCap().isPresent();
  }

  private static Bytes32 computeSenderRecoveryHash(
      final long nonce,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final long gasLimit,
      final Address to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId) {
    return keccak256(
        RLP.encode(
            out -> {
              out.startList();
              out.writeLongScalar(nonce);
              out.writeUInt256Scalar(gasPrice);
              out.writeLongScalar(gasLimit);
              out.writeBytes(to == null ? Bytes.EMPTY : to);
              out.writeUInt256Scalar(value);
              out.writeBytes(payload);
              if (ExperimentalEIPs.eip1559Enabled && gasPremium != null && feeCap != null) {
                out.writeUInt256Scalar(gasPremium);
                out.writeUInt256Scalar(feeCap);
              }
              if (chainId.isPresent()) {
                out.writeBigIntegerScalar(chainId.get());
                out.writeUInt256Scalar(UInt256.ZERO);
                out.writeUInt256Scalar(UInt256.ZERO);
              }
              out.endList();
            }));
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof Transaction)) {
      return false;
    }
    final Transaction that = (Transaction) other;
    return this.chainId.equals(that.chainId)
        && this.gasLimit == that.gasLimit
        && Objects.equals(this.gasPrice, that.gasPrice)
        && Objects.equals(this.gasPremium, that.gasPremium)
        && Objects.equals(this.feeCap, that.feeCap)
        && this.nonce == that.nonce
        && this.payload.equals(that.payload)
        && this.signature.equals(that.signature)
        && this.to.equals(that.to)
        && this.value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        nonce, gasPrice, gasPremium, feeCap, gasLimit, to, value, payload, signature, chainId);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(isContractCreation() ? "ContractCreation" : "MessageCall").append("{");
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
    sb.append("payload=").append(getPayload());
    return sb.append("}").toString();
  }

  public Optional<Address> contractAddress() {
    if (isContractCreation()) {
      return Optional.of(Address.contractAddress(getSender(), getNonce()));
    }
    return Optional.empty();
  }

  public static class Builder {

    protected long nonce = -1L;

    protected Wei gasPrice;

    protected Wei gasPremium;

    protected Wei feeCap;

    protected long gasLimit = -1L;

    protected Address to;

    protected Wei value;

    protected SECP256K1.Signature signature;

    protected Bytes payload;

    protected Address sender;

    protected Optional<BigInteger> chainId = Optional.empty();

    public Builder chainId(final BigInteger chainId) {
      this.chainId = Optional.of(chainId);
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
      this.to = to;
      return this;
    }

    public Builder payload(final Bytes payload) {
      this.payload = payload;
      return this;
    }

    public Builder sender(final Address sender) {
      this.sender = sender;
      return this;
    }

    public Builder signature(final SECP256K1.Signature signature) {
      this.signature = signature;
      return this;
    }

    public Transaction build() {
      return new Transaction(
          nonce,
          gasPrice,
          gasPremium,
          feeCap,
          gasLimit,
          Optional.ofNullable(to),
          value,
          signature,
          payload,
          sender,
          chainId);
    }

    public Transaction signAndBuild(final SECP256K1.KeyPair keys) {
      checkState(
          signature == null, "The transaction signature has already been provided to this builder");
      signature(computeSignature(keys));
      sender(Address.extract(Hash.hash(keys.getPublicKey().getEncodedBytes())));
      return build();
    }

    SECP256K1.Signature computeSignature(final SECP256K1.KeyPair keys) {
      final Bytes32 hash =
          computeSenderRecoveryHash(
              nonce, gasPrice, gasPremium, feeCap, gasLimit, to, value, payload, chainId);
      return SECP256K1.sign(hash, keys);
    }
  }
}
