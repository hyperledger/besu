/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core.transaction;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.encoding.TransactionRLPDecoder;
import org.hyperledger.besu.ethereum.core.encoding.TransactionRLPEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Quantity;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.crypto.Hash.keccak256;

public class EIP1559Transaction implements org.hyperledger.besu.plugin.data.EIP1559Transaction {

  // Used for transactions that are not tied to a specific chain
  // (e.g. does not have a chain id associated with it).
  public static final BigInteger REPLAY_UNPROTECTED_V_BASE = BigInteger.valueOf(27);
  public static final BigInteger REPLAY_UNPROTECTED_V_BASE_PLUS_1 = BigInteger.valueOf(28);

  public static final BigInteger REPLAY_PROTECTED_V_BASE = BigInteger.valueOf(35);

  // The v signature parameter starts at 36 because 1 is the first valid chainId so:
  // chainId > 1 implies that 2 * chainId + V_BASE > 36.
  public static final BigInteger REPLAY_PROTECTED_V_MIN = BigInteger.valueOf(36);

  public static final BigInteger TWO = BigInteger.valueOf(2);

  private final long nonce;

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

  public static EIP1559Transaction.Builder builder() {
    return new EIP1559Transaction.Builder();
  }

  public static EIP1559Transaction readFrom(final RLPInput input) throws RLPException {
    return (EIP1559Transaction) TransactionRLPDecoder.decodeTransaction(input);
  }

  /**
   * Instantiates a transaction instance.
   *
   * @param nonce the nonce
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
  public EIP1559Transaction(
      final long nonce,
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
   * Returns the transaction nonce.
   *
   * @return the transaction nonce
   */
  @Override
  public long getNonce() {
    return nonce;
  }

  @Override
  public Quantity getGasPremium() {
    return gasPremium;
  }

  @Override
  public Quantity getFeeCap() {
    return feeCap;
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
  public Optional<Bytes> getInit() {
    return getTo().isPresent() ? Optional.empty() : Optional.of(payload);
  }

  /**
   * Returns the payload if this is a message call transaction.
   *
   * @return if present the init code
   */
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
              nonce, gasPremium, feeCap, gasLimit, to.orElse(null), value, payload, chainId);
    }
    return hashNoSignature;
  }

  /**
   * Writes the transaction to RLP
   *
   * @param out the output to write the transaction to
   */
  public void writeTo(final RLPOutput out) {
    TransactionRLPEncoder.encode(this, out);
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
   * @param gasPrice the gas price to use
   * @return the up-front cost for the gas the transaction can use.
   */
  public Wei getUpfrontGasCost(final Wei gasPrice) {
    return Wei.of(getGasLimit()).multiply(gasPrice);
  }

  private static Bytes32 computeSenderRecoveryHash(
      final long nonce,
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
              out.writeLongScalar(gasLimit);
              out.writeBytes(to == null ? Bytes.EMPTY : to);
              out.writeUInt256Scalar(value);
              out.writeBytes(payload);
              out.writeUInt256Scalar(gasPremium);
              out.writeUInt256Scalar(feeCap);
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
    if (!(other instanceof EIP1559Transaction)) {
      return false;
    }
    final EIP1559Transaction that = (EIP1559Transaction) other;
    return this.chainId.equals(that.chainId)
        && this.gasLimit == that.gasLimit
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
        nonce, gasPremium, feeCap, gasLimit, to, value, payload, signature, chainId);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(isContractCreation() ? "ContractCreation" : "MessageCall").append("{");
    sb.append("nonce=").append(getNonce()).append(", ");
    sb.append("gasPremium=").append(getGasPremium()).append(", ");
    sb.append("feeCap=").append(getFeeCap()).append(", ");
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

  @Override
  public TransactionType getType() {
    return TransactionType.EIP1559;
  }

  public static class Builder {

    protected long nonce = -1L;

    protected Wei gasPremium;

    protected Wei feeCap;

    protected long gasLimit = -1L;

    protected Address to;

    protected Wei value;

    protected SECP256K1.Signature signature;

    protected Bytes payload;

    protected Address sender;

    protected Optional<BigInteger> chainId = Optional.empty();

    public EIP1559Transaction.Builder chainId(final BigInteger chainId) {
      this.chainId = Optional.of(chainId);
      return this;
    }

    public EIP1559Transaction.Builder gasPremium(final Wei gasPremium) {
      this.gasPremium = gasPremium;
      return this;
    }

    public EIP1559Transaction.Builder feeCap(final Wei feeCap) {
      this.feeCap = feeCap;
      return this;
    }

    public EIP1559Transaction.Builder gasLimit(final long gasLimit) {
      this.gasLimit = gasLimit;
      return this;
    }

    public EIP1559Transaction.Builder nonce(final long nonce) {
      this.nonce = nonce;
      return this;
    }

    public EIP1559Transaction.Builder value(final Wei value) {
      this.value = value;
      return this;
    }

    public EIP1559Transaction.Builder to(final Address to) {
      this.to = to;
      return this;
    }

    public EIP1559Transaction.Builder payload(final Bytes payload) {
      this.payload = payload;
      return this;
    }

    public EIP1559Transaction.Builder sender(final Address sender) {
      this.sender = sender;
      return this;
    }

    public EIP1559Transaction.Builder signature(final SECP256K1.Signature signature) {
      this.signature = signature;
      return this;
    }

    public EIP1559Transaction build() {
      return new EIP1559Transaction(
          nonce,
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

    public EIP1559Transaction signAndBuild(final SECP256K1.KeyPair keys) {
      checkState(
          signature == null, "The transaction signature has already been provided to this builder");
      signature(computeSignature(keys));
      sender(Address.extract(Hash.hash(keys.getPublicKey().getEncodedBytes())));
      return build();
    }

    SECP256K1.Signature computeSignature(final SECP256K1.KeyPair keys) {
      final Bytes32 hash =
          computeSenderRecoveryHash(
              nonce, gasPremium, feeCap, gasLimit, to, value, payload, chainId);
      return SECP256K1.sign(hash, keys);
    }
  }
}
