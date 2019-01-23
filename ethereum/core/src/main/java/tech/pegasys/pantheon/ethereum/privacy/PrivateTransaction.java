/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.privacy;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.pantheon.crypto.Hash.keccak256;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** An operation submitted by an external actor to be applied to the system. */
public class PrivateTransaction {

  // Used for transactions that are not tied to a specific chain
  // (e.g. does not have a chain id associated with it).
  private static final int REPLAY_UNPROTECTED_V_BASE = 27;

  private static final int REPLAY_PROTECTED_V_BASE = 35;

  // The v signature parameter starts at 36 because 1 is the first valid chainId so:
  // chainId > 1 implies that 2 * chainId + V_BASE > 36.
  private static final int REPLAY_PROTECTED_V_MIN = 36;

  private final long nonce;

  private final Wei gasPrice;

  private final long gasLimit;

  private final Optional<Address> to;

  private final Wei value;

  private final SECP256K1.Signature signature;

  private final BytesValue payload;

  private final OptionalInt chainId;

  private final BytesValue privateFrom;

  private final List<BytesValue> privateFor;

  private final BytesValue restriction;

  // Caches a "hash" of a portion of the transaction used for sender recovery.
  // Note that this hash does not include the transaction signature so it does not
  // fully identify the transaction (use the result of the {@code hash()} for that).
  // It is only used to compute said signature and recover the sender from it.
  protected volatile Bytes32 hashNoSignature;

  // Caches the transaction sender.
  protected volatile Address sender;

  // Caches the hash used to uniquely identify the transaction.
  protected volatile Hash hash;

  public static Builder builder() {
    return new Builder();
  }

  public static PrivateTransaction readFrom(final RLPInput input) throws RLPException {
    input.enterList();

    final Builder builder =
        builder()
            .nonce(input.readLongScalar())
            .gasPrice(input.readUInt256Scalar(Wei::wrap))
            .gasLimit(input.readLongScalar())
            .to(input.readBytesValue(v -> v.size() == 0 ? null : Address.wrap(v)))
            .value(input.readUInt256Scalar(Wei::wrap))
            .payload(input.readBytesValue());

    final int v = input.readIntScalar();
    final byte recId;
    int chainId = -1;
    if (v == REPLAY_UNPROTECTED_V_BASE || v == REPLAY_UNPROTECTED_V_BASE + 1) {
      recId = (byte) (v - REPLAY_UNPROTECTED_V_BASE);
    } else if (v > REPLAY_PROTECTED_V_MIN) {
      chainId = (v - REPLAY_PROTECTED_V_BASE) / 2;
      recId = (byte) (v - (2 * chainId + REPLAY_PROTECTED_V_BASE));
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }
    final BigInteger r = BytesValues.asUnsignedBigInteger(input.readUInt256Scalar().getBytes());
    final BigInteger s = BytesValues.asUnsignedBigInteger(input.readUInt256Scalar().getBytes());
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);
    final BytesValue privateFrom = input.readBytesValue();
    final List<BytesValue> privateFor = input.readList(RLPInput::readBytesValue);
    final BytesValue restriction = input.readBytesValue();

    input.leaveList();

    return builder
        .chainId(chainId)
        .signature(signature)
        .privateFrom(privateFrom)
        .privateFor(privateFor)
        .restriction(restriction)
        .build();
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
   * @param privateFrom The public key of the sender of this private transaction
   * @param privateFor An array of the public keys of the intended recipients of this private
   *     transaction
   * @param restriction the restriction of this private transaction
   */
  protected PrivateTransaction(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECP256K1.Signature signature,
      final BytesValue payload,
      final Address sender,
      final int chainId,
      final BytesValue privateFrom,
      final List<BytesValue> privateFor,
      final BytesValue restriction) {
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.gasLimit = gasLimit;
    this.to = to;
    this.value = value;
    this.signature = signature;
    this.payload = payload;
    this.sender = sender;
    this.chainId = chainId > 0 ? OptionalInt.of(chainId) : OptionalInt.empty();
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.restriction = restriction;
  }

  /**
   * Returns the transaction nonce.
   *
   * @return the transaction nonce
   */
  public long getNonce() {
    return nonce;
  }

  /**
   * Return the transaction gas price.
   *
   * @return the transaction gas price
   */
  public Wei getGasPrice() {
    return gasPrice;
  }

  /**
   * Returns the transaction gas limit.
   *
   * @return the transaction gas limit
   */
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
  public Optional<Address> getTo() {
    return to;
  }

  /**
   * Returns the value transferred in the transaction.
   *
   * @return the value transferred in the transaction
   */
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
  public BytesValue getPayload() {
    return payload;
  }

  /**
   * Return the transaction chain id (if it exists)
   *
   * <p>The {@code OptionalInt} will be {@code OptionalInt.empty()} if the transaction is not tied
   * to a specific chain.
   *
   * @return the transaction chain id if it exists; otherwise {@code OptionalInt.empty()}
   */
  public OptionalInt getChainId() {
    return chainId;
  }

  /**
   * Returns the enclave public key of the sender.
   *
   * @return the enclave public key of the sender.
   */
  public BytesValue getPrivateFrom() {
    return privateFrom;
  }

  /**
   * Returns the enclave public keys of the receivers.
   *
   * @return the enclave public keys of the receivers
   */
  public List<BytesValue> getPrivateFor() {
    return privateFor;
  }

  /**
   * Returns the restriction of this private transaction.
   *
   * @return the restriction
   */
  public BytesValue getRestriction() {
    return restriction;
  }

  /**
   * Returns the transaction sender.
   *
   * @return the transaction sender
   */
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
              gasLimit,
              to.orElse(null),
              value,
              payload,
              chainId,
              privateFrom,
              privateFor,
              restriction);
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
    out.writeBytesValue(getTo().isPresent() ? getTo().get() : BytesValue.EMPTY);
    out.writeUInt256Scalar(getValue());
    out.writeBytesValue(getPayload());
    writeSignature(out);
    out.writeBytesValue(getPrivateFrom());
    out.writeList(getPrivateFor(), (bv, rlpO) -> rlpO.writeBytesValue(bv));
    out.writeBytesValue(getRestriction());

    out.endList();
  }

  private void writeSignature(final RLPOutput out) {
    out.writeIntScalar(getV());
    out.writeBigIntegerScalar(getSignature().getR());
    out.writeBigIntegerScalar(getSignature().getS());
  }

  public BigInteger getR() {
    return signature.getR();
  }

  public BigInteger getS() {
    return signature.getS();
  }

  public int getV() {
    final int v;
    if (!chainId.isPresent()) {
      v = signature.getRecId() + REPLAY_UNPROTECTED_V_BASE;
    } else {
      v = (getSignature().getRecId() + REPLAY_PROTECTED_V_BASE + 2 * chainId.getAsInt());
    }
    return v;
  }

  /**
   * Returns the transaction hash.
   *
   * @return the transaction hash
   */
  public Hash hash() {
    if (hash == null) {
      final BytesValue rlp = RLP.encode(this::writeTo);
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
    return !getTo().isPresent();
  }

  /**
   * Calculates the up-front cost for the gas the transaction can use.
   *
   * @return the up-front cost for the gas the transaction can use.
   */
  public Wei getUpfrontGasCost() {
    return Wei.of(getGasLimit()).times(getGasPrice());
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
    return getUpfrontGasCost().plus(getValue());
  }

  private static Bytes32 computeSenderRecoveryHash(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Address to,
      final Wei value,
      final BytesValue payload,
      final OptionalInt chainId,
      final BytesValue privateFrom,
      final List<BytesValue> privateFor,
      final BytesValue restriction) {
    return keccak256(
        RLP.encode(
            out -> {
              out.startList();
              out.writeLongScalar(nonce);
              out.writeUInt256Scalar(gasPrice);
              out.writeLongScalar(gasLimit);
              out.writeBytesValue(to == null ? BytesValue.EMPTY : to);
              out.writeUInt256Scalar(value);
              out.writeBytesValue(payload);
              if (chainId.isPresent()) {
                out.writeIntScalar(chainId.getAsInt());
                out.writeUInt256Scalar(UInt256.ZERO);
                out.writeUInt256Scalar(UInt256.ZERO);
              }
              out.writeBytesValue(privateFrom);
              out.writeList(privateFor, (bv, rlpO) -> rlpO.writeBytesValue(bv));
              out.writeBytesValue(restriction);
              out.endList();
            }));
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof PrivateTransaction)) {
      return false;
    }
    final PrivateTransaction that = (PrivateTransaction) other;
    return this.chainId.equals(that.chainId)
        && this.gasLimit == that.gasLimit
        && this.gasPrice.equals(that.gasPrice)
        && this.nonce == that.nonce
        && this.payload.equals(that.payload)
        && this.signature.equals(that.signature)
        && this.to.equals(that.to)
        && this.value.equals(that.value)
        && this.privateFor.equals(that.privateFor)
        && this.privateFrom.equals(that.privateFrom)
        && this.restriction.equals(that.restriction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        nonce,
        gasPrice,
        gasLimit,
        to,
        value,
        payload,
        signature,
        chainId,
        privateFor,
        privateFrom,
        restriction);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(isContractCreation() ? "ContractCreation" : "MessageCall").append("{");
    sb.append("nonce=").append(getNonce()).append(", ");
    sb.append("gasPrice=").append(getGasPrice()).append(", ");
    sb.append("gasLimit=").append(getGasLimit()).append(", ");
    if (getTo().isPresent()) sb.append("to=").append(getTo().get()).append(", ");
    sb.append("value=").append(getValue()).append(", ");
    sb.append("sig=").append(getSignature()).append(", ");
    if (chainId.isPresent()) sb.append("chainId=").append(getChainId().getAsInt()).append(", ");
    sb.append("payload=").append(getPayload());
    sb.append("privateFrom=").append(getPrivateFrom());
    sb.append("privateFor=").append(Arrays.toString(getPrivateFor().toArray()));
    sb.append("restriction=").append(getRestriction());
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

    protected long gasLimit = -1L;

    protected Address to;

    protected Wei value;

    protected SECP256K1.Signature signature;

    protected BytesValue payload;

    protected Address sender;

    protected int chainId = -1;

    protected BytesValue privateFrom;

    protected List<BytesValue> privateFor;

    protected BytesValue restriction;

    public Builder chainId(final int chainId) {
      this.chainId = chainId;
      return this;
    }

    public Builder gasPrice(final Wei gasPrice) {
      this.gasPrice = gasPrice;
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

    public Builder payload(final BytesValue payload) {
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

    public Builder privateFrom(final BytesValue privateFrom) {
      this.privateFrom = privateFrom;
      return this;
    }

    public Builder privateFor(final List<BytesValue> privateFor) {
      this.privateFor = privateFor;
      return this;
    }

    public Builder restriction(final BytesValue restriction) {
      this.restriction = restriction;
      return this;
    }

    public PrivateTransaction build() {
      return new PrivateTransaction(
          nonce,
          gasPrice,
          gasLimit,
          Optional.ofNullable(to),
          value,
          signature,
          payload,
          sender,
          chainId,
          privateFrom,
          privateFor,
          restriction);
    }

    public PrivateTransaction signAndBuild(final SECP256K1.KeyPair keys) {
      checkState(
          signature == null, "The transaction signature has already been provided to this builder");
      signature(computeSignature(keys));
      sender(Address.extract(Hash.hash(keys.getPublicKey().getEncodedBytes())));
      return build();
    }

    protected SECP256K1.Signature computeSignature(final SECP256K1.KeyPair keys) {
      final OptionalInt optionalChainId =
          chainId > 0 ? OptionalInt.of(chainId) : OptionalInt.empty();
      final Bytes32 hash =
          computeSenderRecoveryHash(
              nonce,
              gasPrice,
              gasLimit,
              to,
              value,
              payload,
              optionalChainId,
              privateFrom,
              privateFor,
              restriction);
      return SECP256K1.sign(hash, keys);
    }
  }
}
