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
package org.hyperledger.besu.ethereum.privacy;

import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.hyperledger.besu.plugin.data.Restriction.RESTRICTED;
import static org.hyperledger.besu.plugin.data.Restriction.UNRESTRICTED;
import static org.hyperledger.besu.plugin.data.Restriction.UNSUPPORTED;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.plugin.data.Restriction;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An operation submitted by an external actor to be applied to the system. */
public class PrivateTransaction implements org.hyperledger.besu.plugin.data.PrivateTransaction {
  private static final Logger LOG = LoggerFactory.getLogger(PrivateTransaction.class);

  // Used for transactions that are not tied to a specific chain
  // (i.e. does not have a chain id associated with it).
  private static final BigInteger REPLAY_UNPROTECTED_V_BASE = BigInteger.valueOf(27);
  private static final BigInteger REPLAY_UNPROTECTED_V_BASE_PLUS_1 = BigInteger.valueOf(28);

  private static final BigInteger REPLAY_PROTECTED_V_BASE = BigInteger.valueOf(35);

  // The v signature parameter starts at 36 because 1 is the first valid chainId so:
  // chainId > 1 implies that 2 * chainId + V_BASE > 36.
  private static final BigInteger REPLAY_PROTECTED_V_MIN = BigInteger.valueOf(36);

  private static final BigInteger TWO = BigInteger.valueOf(2);

  private final long nonce;

  private final Wei gasPrice;

  private final long gasLimit;

  private final Optional<Address> to;

  private final Wei value;

  private final SECPSignature signature;

  private final Bytes payload;

  private final Optional<BigInteger> chainId;

  private final Bytes privateFrom;

  private final Optional<List<Bytes>> privateFor;

  private final Optional<Bytes> privacyGroupId;

  private final Restriction restriction;

  // Caches a "hash" of a portion of the transaction used for sender recovery.
  // Note that this hash does not include the transaction signature, so it does not
  // fully identify the transaction (use the result of the {@code hash()} for that).
  // It is only used to compute said signature and recover the sender from it.
  protected volatile Bytes32 hashNoSignature;

  // Caches the transaction sender.
  protected volatile Address sender;

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  public static Builder builder() {
    return new Builder();
  }

  public static PrivateTransaction readFrom(
      final org.hyperledger.besu.plugin.data.PrivateTransaction p) {

    final BigInteger v = p.getV();
    final byte recId;
    Optional<BigInteger> chainId = p.getChainId();
    if (v.equals(REPLAY_UNPROTECTED_V_BASE) || v.equals(REPLAY_UNPROTECTED_V_BASE_PLUS_1)) {
      recId = v.subtract(REPLAY_UNPROTECTED_V_BASE).byteValueExact();
    } else if (v.compareTo(REPLAY_PROTECTED_V_MIN) > 0) {
      chainId = Optional.of(v.subtract(REPLAY_PROTECTED_V_BASE).divide(TWO));
      recId = v.subtract(TWO.multiply(chainId.get()).add(REPLAY_PROTECTED_V_BASE)).byteValueExact();
    } else {
      throw new RuntimeException(
          String.format("An unsupported encoded `v` value of %s was found", v));
    }

    final SECPSignature signature =
        SIGNATURE_ALGORITHM.get().createSignature(p.getR(), p.getS(), recId);

    final Builder b =
        builder()
            .nonce(p.getNonce())
            .gasPrice(Wei.of(p.getGasPrice().getAsBigInteger()))
            .gasLimit(p.getGasLimit())
            .to(p.getTo().map(Address::wrap).orElse(null))
            .value(Wei.of(p.getValue().getAsBigInteger()))
            .sender(Address.wrap(p.getSender()))
            .payload(p.getPayload())
            .privateFrom(p.getPrivateFrom())
            .restriction(p.getRestriction())
            .signature(signature);

    chainId.ifPresent(b::chainId);
    p.getPrivateFor().ifPresent(b::privateFor);
    p.getPrivacyGroupId().ifPresent(b::privacyGroupId);

    return b.build();
  }

  @SuppressWarnings({"unchecked"})
  public static PrivateTransaction readFrom(final RLPInput input) throws RLPException {
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
    final BigInteger r = input.readUInt256Scalar().toUnsignedBigInteger();
    final BigInteger s = input.readUInt256Scalar().toUnsignedBigInteger();
    final SECPSignature signature = SIGNATURE_ALGORITHM.get().createSignature(r, s, recId);

    final Bytes privateFrom = input.readBytes();
    final Object privateForOrPrivacyGroupId = resolvePrivateForOrPrivacyGroupId(input.readAsRlp());
    final Restriction restriction = convertToEnum(input.readBytes());

    input.leaveList();

    chainId.ifPresent(builder::chainId);

    if (privateForOrPrivacyGroupId instanceof List) {
      return builder
          .signature(signature)
          .privateFrom(privateFrom)
          .privateFor((List<Bytes>) privateForOrPrivacyGroupId)
          .restriction(restriction)
          .build();
    } else {
      return builder
          .signature(signature)
          .privateFrom(privateFrom)
          .privacyGroupId((Bytes) privateForOrPrivacyGroupId)
          .restriction(restriction)
          .build();
    }
  }

  private static Object resolvePrivateForOrPrivacyGroupId(final RLPInput item) {
    return item.nextIsList() ? item.readList(RLPInput::readBytes) : item.readBytes();
  }

  private static Restriction convertToEnum(final Bytes readBytes) {
    if (readBytes.equals(RESTRICTED.getBytes())) {
      return RESTRICTED;
    } else if (readBytes.equals(UNRESTRICTED.getBytes())) {
      return UNRESTRICTED;
    } else {
      LOG.error("Transaction restriction '{}' not supported", readBytes.toString());
      return UNSUPPORTED;
    }
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
   * @param privacyGroupId The privacy group id of this private transaction
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
      final SECPSignature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId,
      final Bytes privateFrom,
      final Optional<List<Bytes>> privateFor,
      final Optional<Bytes> privacyGroupId,
      final Restriction restriction) {
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.gasLimit = gasLimit;
    this.to = to;
    this.value = value;
    this.signature = signature;
    this.payload = payload;
    this.sender = sender;
    this.chainId = chainId;
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.privacyGroupId = privacyGroupId;
    this.restriction = restriction;
  }

  protected PrivateTransaction(final PrivateTransaction privateTransaction) {
    this(
        privateTransaction.getNonce(),
        privateTransaction.getGasPrice(),
        privateTransaction.getGasLimit(),
        privateTransaction.getTo(),
        privateTransaction.getValue(),
        privateTransaction.getSignature(),
        privateTransaction.getPayload(),
        privateTransaction.getSender(),
        privateTransaction.getChainId(),
        privateTransaction.getPrivateFrom(),
        privateTransaction.getPrivateFor(),
        privateTransaction.getPrivacyGroupId(),
        privateTransaction.getRestriction());
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
   * Returns the enclave public key of the sender.
   *
   * @return the enclave public key of the sender.
   */
  @Override
  public Bytes getPrivateFrom() {
    return privateFrom;
  }

  /**
   * Returns the enclave public keys of the receivers.
   *
   * @return the enclave public keys of the receivers
   */
  @Override
  public Optional<List<Bytes>> getPrivateFor() {
    return privateFor;
  }

  /**
   * Returns the enclave privacy group id.
   *
   * @return the enclave privacy group id.
   */
  @Override
  public Optional<Bytes> getPrivacyGroupId() {
    return privacyGroupId;
  }

  /**
   * Returns the restriction of this private transaction.
   *
   * @return the restriction
   */
  @Override
  public Restriction getRestriction() {
    return restriction;
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
          SIGNATURE_ALGORITHM
              .get()
              .recoverPublicKeyFromSignature(getOrComputeSenderRecoveryHash(), signature)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot recover public key from signature for " + this));
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
              privacyGroupId,
              restriction.getBytes());
    }
    return hashNoSignature;
  }

  /**
   * Writes the transaction to RLP
   *
   * @param out the output to write the transaction to
   */
  public void writeTo(final RLPOutput out) {
    out.writeRLPBytes(serialize(this).encoded());
  }

  public static BytesValueRLPOutput serialize(
      final org.hyperledger.besu.plugin.data.PrivateTransaction t) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();

    out.writeLongScalar(t.getNonce());
    out.writeUInt256Scalar((Wei) t.getGasPrice());
    out.writeLongScalar(t.getGasLimit());
    out.writeBytes(t.getTo().isPresent() ? t.getTo().get() : Bytes.EMPTY);
    out.writeUInt256Scalar((Wei) t.getValue());
    out.writeBytes(t.getPayload());
    out.writeBigIntegerScalar(t.getV());
    out.writeBigIntegerScalar(t.getR());
    out.writeBigIntegerScalar(t.getS());
    out.writeBytes(t.getPrivateFrom());
    t.getPrivateFor()
        .ifPresent(privateFor -> out.writeList(privateFor, (bv, rlpO) -> rlpO.writeBytes(bv)));
    t.getPrivacyGroupId().ifPresent(out::writeBytes);
    out.writeBytes(t.getRestriction().getBytes());

    out.endList();
    return out;
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
    if (!chainId.isPresent()) {
      v = recId.add(REPLAY_UNPROTECTED_V_BASE);
    } else {
      v = recId.add(REPLAY_PROTECTED_V_BASE).add(TWO.multiply(chainId.get()));
    }
    return v;
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
    return getGasPrice().multiply(getGasLimit());
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
   * Determines the privacy group id. Either returning the value of privacyGroupId field if it
   * exists or calculating the EEA privacyGroupId from the privateFrom and privateFor fields.
   *
   * @return the privacyGroupId
   */
  public Bytes32 determinePrivacyGroupId() {
    if (getPrivacyGroupId().isPresent()) {
      return Bytes32.wrap(getPrivacyGroupId().get());
    } else {
      final List<Bytes> privateFor = getPrivateFor().orElse(Lists.newArrayList());
      return PrivacyGroupUtil.calculateEeaPrivacyGroupId(getPrivateFrom(), privateFor);
    }
  }

  private static Bytes32 computeSenderRecoveryHash(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Address to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId,
      final Bytes privateFrom,
      final Optional<List<Bytes>> privateFor,
      final Optional<Bytes> privacyGroupId,
      final Bytes restriction) {
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
              if (chainId.isPresent()) {
                out.writeBigIntegerScalar(chainId.get());
                out.writeUInt256Scalar(UInt256.ZERO);
                out.writeUInt256Scalar(UInt256.ZERO);
              }
              out.writeBytes(privateFrom);
              privateFor.ifPresent(pF -> out.writeList(pF, (bv, rlpO) -> rlpO.writeBytes(bv)));
              privacyGroupId.ifPresent(out::writeBytes);
              out.writeBytes(restriction);
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
    if (chainId.isPresent()) sb.append("chainId=").append(getChainId().get()).append(", ");
    sb.append("payload=").append(getPayload()).append(", ");
    sb.append("privateFrom=").append(getPrivateFrom()).append(", ");
    if (getPrivateFor().isPresent())
      sb.append("privateFor=")
          .append(Arrays.toString(getPrivateFor().get().toArray()))
          .append(", ");
    if (getPrivacyGroupId().isPresent())
      sb.append("privacyGroupId=").append(getPrivacyGroupId().get()).append(", ");
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

    protected SECPSignature signature;

    protected Bytes payload;

    protected Address sender;

    protected Optional<BigInteger> chainId = Optional.empty();

    protected Bytes privateFrom;

    protected Optional<List<Bytes>> privateFor = Optional.empty();

    protected Optional<Bytes> privacyGroupId = Optional.empty();

    protected Restriction restriction;

    public Builder chainId(final BigInteger chainId) {
      this.chainId = Optional.of(chainId);
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

    public Builder payload(final Bytes payload) {
      this.payload = payload;
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

    public Builder privacyGroupId(final Bytes privacyGroupId) {
      this.privacyGroupId = Optional.of(privacyGroupId);
      return this;
    }

    public Builder privateFrom(final Bytes privateFrom) {
      this.privateFrom = privateFrom;
      return this;
    }

    public Builder privateFor(final List<Bytes> privateFor) {
      this.privateFor = Optional.of(privateFor);
      return this;
    }

    public Builder restriction(final Restriction restriction) {
      this.restriction = restriction;
      return this;
    }

    public PrivateTransaction build() {
      if (privacyGroupId.isPresent() && privateFor.isPresent()) {
        throw new IllegalArgumentException(
            "Private transaction should contain either privacyGroup or privateFor, but not both");
      }
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
          privacyGroupId,
          restriction);
    }

    public PrivateTransaction signAndBuild(final KeyPair keys) {
      checkState(
          signature == null, "The transaction signature has already been provided to this builder");
      signature(computeSignature(keys));
      sender(Address.extract(Hash.hash(keys.getPublicKey().getEncodedBytes())));
      return build();
    }

    protected SECPSignature computeSignature(final KeyPair keys) {
      final Bytes32 hash =
          computeSenderRecoveryHash(
              nonce,
              gasPrice,
              gasLimit,
              to,
              value,
              payload,
              chainId,
              privateFrom,
              privateFor,
              privacyGroupId,
              restriction.getBytes());
      return SIGNATURE_ALGORITHM.get().sign(hash, keys);
    }
  }
}
