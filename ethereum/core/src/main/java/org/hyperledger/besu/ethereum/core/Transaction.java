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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.hyperledger.besu.datatypes.VersionedHash.SHA256_VERSION_ID;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Sha256Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.encoding.BlobTransactionEncoder;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.evm.AccessListEntry;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt256s;

/** An operation submitted by an external actor to be applied to the system. */
public class Transaction
    implements org.hyperledger.besu.datatypes.Transaction,
        org.hyperledger.besu.plugin.data.UnsignedPrivateMarkerTransaction {

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

  private final Optional<Wei> gasPrice;

  private final Optional<Wei> maxPriorityFeePerGas;

  private final Optional<Wei> maxFeePerGas;
  private final Optional<Wei> maxFeePerDataGas;

  private final long gasLimit;

  private final Optional<Address> to;

  private final Wei value;

  private final SECPSignature signature;

  private final Bytes payload;

  private final Optional<List<AccessListEntry>> maybeAccessList;

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
  // Caches the size in bytes of the encoded transaction.
  protected volatile int size = -1;
  private final TransactionType transactionType;

  private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
  private final Optional<List<VersionedHash>> versionedHashes;

  private final Optional<BlobsWithCommitments> blobsWithCommitments;

  public static Builder builder() {
    return new Builder();
  }

  public static Transaction readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
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
   * @param maxPriorityFeePerGas the max priority fee per gas
   * @param maxFeePerGas the max fee per gas
   * @param maxFeePerDataGas the max fee per data gas
   * @param gasLimit the gas limit
   * @param to the transaction recipient
   * @param value the value being transferred to the recipient
   * @param signature the signature
   * @param payload the payload
   * @param maybeAccessList the optional list of addresses/storage slots this transaction intends to
   *     preload
   * @param sender the transaction sender
   * @param chainId the chain id to apply the transaction to
   *     <p>The {@code to} will be an {@code Optional.empty()} for a contract creation transaction;
   *     otherwise it should contain an address.
   *     <p>The {@code chainId} must be greater than 0 to be applied to a specific chain; otherwise
   *     it will default to any chain.
   */
  public Transaction(
      final TransactionType transactionType,
      final long nonce,
      final Optional<Wei> gasPrice,
      final Optional<Wei> maxPriorityFeePerGas,
      final Optional<Wei> maxFeePerGas,
      final Optional<Wei> maxFeePerDataGas,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final Optional<List<AccessListEntry>> maybeAccessList,
      final Address sender,
      final Optional<BigInteger> chainId,
      final Optional<List<VersionedHash>> versionedHashes,
      final Optional<BlobsWithCommitments> blobsWithCommitments) {

    if (transactionType.requiresChainId()) {
      checkArgument(
          chainId.isPresent(), "Chain id must be present for transaction type %s", transactionType);
    }

    if (maybeAccessList.isPresent()) {
      checkArgument(
          transactionType.supportsAccessList(),
          "Must not specify access list for transaction not supporting it");
    }

    if (Objects.equals(transactionType, TransactionType.ACCESS_LIST)) {
      checkArgument(
          maybeAccessList.isPresent(), "Must specify access list for access list transaction");
    }

    if (versionedHashes.isPresent() || maxFeePerDataGas.isPresent()) {
      checkArgument(
          transactionType.supportsBlob(),
          "Must not specify blob versioned hashes of max fee per data gas for transaction not supporting it");
    }

    if (transactionType.supportsBlob()) {
      checkArgument(
          versionedHashes.isPresent(), "Must specify blob versioned hashes for blob transaction");
      checkArgument(
          !versionedHashes.get().isEmpty(), "Blob transaction must have at least one blob");
      checkArgument(
          maxFeePerDataGas.isPresent(), "Must specify max fee per data gas for blob transaction");
    }

    this.transactionType = transactionType;
    this.nonce = nonce;
    this.gasPrice = gasPrice;
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    this.maxFeePerGas = maxFeePerGas;
    this.maxFeePerDataGas = maxFeePerDataGas;
    this.gasLimit = gasLimit;
    this.to = to;
    this.value = value;
    this.signature = signature;
    this.payload = payload;
    this.maybeAccessList = maybeAccessList;
    this.sender = sender;
    this.chainId = chainId;
    this.versionedHashes = versionedHashes;
    this.blobsWithCommitments = blobsWithCommitments;

    if (isUpfrontGasCostTooHigh()) {
      throw new IllegalArgumentException("Upfront gas cost exceeds UInt256");
    }
  }

  public Transaction(
      final long nonce,
      final Optional<Wei> gasPrice,
      final Optional<Wei> maxPriorityFeePerGas,
      final Optional<Wei> maxFeePerGas,
      final Optional<Wei> maxFeePerDataGas,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final Address sender,
      final Optional<BigInteger> chainId,
      final Optional<List<VersionedHash>> versionedHashes,
      final Optional<BlobsWithCommitments> blobsWithCommitments) {
    this(
        TransactionType.FRONTIER,
        nonce,
        gasPrice,
        maxPriorityFeePerGas,
        maxFeePerGas,
        maxFeePerDataGas,
        gasLimit,
        to,
        value,
        signature,
        payload,
        Optional.empty(),
        sender,
        chainId,
        versionedHashes,
        blobsWithCommitments);
  }

  public Transaction(
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Address to,
      final Wei value,
      final SECPSignature signature,
      final Bytes payload,
      final Optional<BigInteger> chainId,
      final Optional<List<VersionedHash>> versionedHashes,
      final Optional<BlobsWithCommitments> blobsWithCommitments) {
    this(
        TransactionType.FRONTIER,
        nonce,
        Optional.of(gasPrice),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        gasLimit,
        Optional.of(to),
        value,
        signature,
        payload,
        Optional.empty(),
        null,
        chainId,
        versionedHashes,
        blobsWithCommitments);
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
      final Optional<BigInteger> chainId,
      final Optional<List<VersionedHash>> versionedHashes) {
    this(
        nonce,
        Optional.of(gasPrice),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        gasLimit,
        to,
        value,
        signature,
        payload,
        sender,
        chainId,
        versionedHashes,
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
      final Optional<Wei> maxFeePerDataGas,
      final Optional<List<VersionedHash>> versionedHashes,
      final Optional<BlobsWithCommitments> blobsWithCommitments) {
    this(
        nonce,
        Optional.of(gasPrice),
        Optional.empty(),
        Optional.empty(),
        maxFeePerDataGas,
        gasLimit,
        to,
        value,
        signature,
        payload,
        sender,
        chainId,
        versionedHashes,
        blobsWithCommitments);
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
  public Optional<Wei> getGasPrice() {
    return gasPrice;
  }

  /**
   * Return the transaction max priority per gas.
   *
   * @return the transaction max priority per gas
   */
  @Override
  public Optional<Wei> getMaxPriorityFeePerGas() {
    return maxPriorityFeePerGas;
  }

  /**
   * Return the transaction max fee per gas.
   *
   * @return the transaction max fee per gas
   */
  @Override
  public Optional<Wei> getMaxFeePerGas() {
    return maxFeePerGas;
  }

  /**
   * Return the transaction max fee per data gas.
   *
   * @return the transaction max fee per data gas
   */
  @Override
  public Optional<Wei> getMaxFeePerDataGas() {
    return maxFeePerDataGas;
  }

  /**
   * Boolean which indicates the transaction has associated cost data, whether gas price or 1559 fee
   * market parameters.
   *
   * @return whether cost params are present
   */
  public boolean hasCostParams() {
    return Arrays.asList(
            getGasPrice(), getMaxFeePerGas(), getMaxPriorityFeePerGas(), getMaxFeePerDataGas())
        .stream()
        .flatMap(Optional::stream)
        .map(Quantity::getAsBigInteger)
        .anyMatch(q -> q.longValue() > 0L);
  }

  public Wei getEffectivePriorityFeePerGas(final Optional<Wei> maybeBaseFee) {
    return maybeBaseFee
        .map(
            baseFee -> {
              if (getType().supports1559FeeMarket()) {
                if (baseFee.greaterOrEqualThan(getMaxFeePerGas().get())) {
                  return Wei.ZERO;
                }
                return UInt256s.min(
                    getMaxPriorityFeePerGas().get(), getMaxFeePerGas().get().subtract(baseFee));
              } else {
                if (baseFee.greaterOrEqualThan(getGasPrice().get())) {
                  return Wei.ZERO;
                }
                return getGasPrice().get().subtract(baseFee);
              }
            })
        .orElseGet(() -> getGasPrice().orElse(Wei.ZERO));
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
   * Returns the number of blobs this transaction has, or 0 if not a blob transaction type
   *
   * @return return the count
   */
  public int getBlobCount() {
    return versionedHashes.map(List::size).orElse(0);
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

  public Optional<List<AccessListEntry>> getAccessList() {
    return maybeAccessList;
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
              gasPrice.orElse(null),
              maxPriorityFeePerGas.orElse(null),
              maxFeePerGas.orElse(null),
              maxFeePerDataGas.orElse(null),
              gasLimit,
              to,
              value,
              payload,
              maybeAccessList,
              versionedHashes.orElse(null),
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

    final BigInteger recId = BigInteger.valueOf(signature.getRecId());

    if (transactionType != null && transactionType != TransactionType.FRONTIER) {
      // EIP-2718 typed transaction, return yParity:
      return recId;
    } else {
      if (chainId.isEmpty()) {
        return recId.add(REPLAY_UNPROTECTED_V_BASE);
      } else {
        return recId.add(REPLAY_PROTECTED_V_BASE).add(TWO.multiply(chainId.get()));
      }
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
      memoizeHashAndSize();
    }
    return hash;
  }

  /**
   * Returns the size in bytes of the encoded transaction.
   *
   * @return the size in bytes of the encoded transaction.
   */
  public int getSize() {
    if (size == -1) {
      memoizeHashAndSize();
    }
    return size;
  }

  private void memoizeHashAndSize() {
    final Bytes bytes = TransactionEncoder.encodeOpaqueBytes(this);
    hash = Hash.hash(bytes);

    if (transactionType.supportsBlob()) {
      if (getBlobsWithCommitments().isPresent()) {
        size = TransactionEncoder.encodeOpaqueBytes(this).size();
      }
    } else {
      final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
      TransactionEncoder.encodeForWire(transactionType, bytes, rlpOutput);
      size = rlpOutput.encodedSize();
    }
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
   * Calculates the max up-front cost for the gas the transaction can use.
   *
   * @return the max up-front cost for the gas the transaction can use.
   */
  private Wei getMaxUpfrontGasCost(final long dataGasPerBlock) {
    return getUpfrontGasCost(
        getMaxGasPrice(), getMaxFeePerDataGas().orElse(Wei.ZERO), dataGasPerBlock);
  }

  /**
   * Check if the upfront gas cost is over the max allowed
   *
   * @return true is upfront data cost overflow uint256 max value
   */
  private boolean isUpfrontGasCostTooHigh() {
    return calculateUpfrontGasCost(getMaxGasPrice(), Wei.ZERO, 0L).bitLength() > 256;
  }

  /**
   * Calculates the up-front cost for the gas and data gas the transaction can use.
   *
   * @param gasPrice the gas price to use
   * @param dataGasPrice the data gas price to use
   * @return the up-front cost for the gas the transaction can use.
   */
  public Wei getUpfrontGasCost(
      final Wei gasPrice, final Wei dataGasPrice, final long totalDataGas) {
    if (gasPrice == null || gasPrice.isZero()) {
      return Wei.ZERO;
    }

    final var cost = calculateUpfrontGasCost(gasPrice, dataGasPrice, totalDataGas);

    if (cost.bitLength() > 256) {
      return Wei.MAX_WEI;
    } else {
      return Wei.of(cost);
    }
  }

  private BigInteger calculateUpfrontGasCost(
      final Wei gasPrice, final Wei dataGasPrice, final long totalDataGas) {
    var cost =
        new BigInteger(1, Longs.toByteArray(getGasLimit())).multiply(gasPrice.getAsBigInteger());

    if (transactionType.supportsBlob()) {
      cost = cost.add(dataGasPrice.getAsBigInteger().multiply(BigInteger.valueOf(totalDataGas)));
    }

    return cost;
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
  public Wei getUpfrontCost(final long totalDataGas) {
    return getMaxUpfrontGasCost(totalDataGas).addExact(getValue());
  }

  /**
   * Return the maximum fee per gas the sender is willing to pay for this transaction.
   *
   * @return max fee per gas in wei
   */
  public Wei getMaxGasPrice() {
    return maxFeePerGas.orElseGet(
        () ->
            gasPrice.orElseThrow(
                () ->
                    new IllegalStateException(
                        "Transaction requires either gasPrice or maxFeePerGas")));
  }
  /**
   * Calculates the effectiveGasPrice of a transaction on the basis of an {@code Optional<Long>}
   * baseFee and handles unwrapping Optional fee parameters. If baseFee is present, effective gas is
   * calculated as:
   *
   * <p>min((baseFeePerGas + maxPriorityFeePerGas), maxFeePerGas)
   *
   * <p>Otherwise, return gasPrice for legacy transactions.
   *
   * @param baseFeePerGas optional baseFee from the block header, if we are post-london
   * @return the effective gas price.
   */
  public final Wei getEffectiveGasPrice(final Optional<Wei> baseFeePerGas) {
    return getEffectivePriorityFeePerGas(baseFeePerGas).addExact(baseFeePerGas.orElse(Wei.ZERO));
  }

  @Override
  public TransactionType getType() {
    return this.transactionType;
  }

  public Optional<List<VersionedHash>> getVersionedHashes() {
    return versionedHashes;
  }

  public Optional<BlobsWithCommitments> getBlobsWithCommitments() {
    return blobsWithCommitments;
  }

  /**
   * Return the list of transaction hashes extracted from the collection of Transaction passed as
   * argument
   *
   * @param transactions a collection of transactions
   * @return the list of transaction hashes
   */
  public static List<Hash> toHashList(final Collection<Transaction> transactions) {
    return transactions.stream().map(Transaction::getHash).collect(Collectors.toUnmodifiableList());
  }

  private static Bytes32 computeSenderRecoveryHash(
      final TransactionType transactionType,
      final long nonce,
      final Wei gasPrice,
      final Wei maxPriorityFeePerGas,
      final Wei maxFeePerGas,
      final Wei maxFeePerDataGas,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final Optional<List<AccessListEntry>> accessList,
      final List<VersionedHash> versionedHashes,
      final Optional<BigInteger> chainId) {
    if (transactionType.requiresChainId()) {
      checkArgument(chainId.isPresent(), "Transaction type %s requires chainId", transactionType);
    }
    final Bytes preimage;
    switch (transactionType) {
      case FRONTIER:
        preimage = frontierPreimage(nonce, gasPrice, gasLimit, to, value, payload, chainId);
        break;
      case EIP1559:
        preimage =
            eip1559Preimage(
                nonce,
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                to,
                value,
                payload,
                chainId,
                accessList);
        break;
      case BLOB:
        preimage =
            blobPreimage(
                nonce,
                maxPriorityFeePerGas,
                maxFeePerGas,
                maxFeePerDataGas,
                gasLimit,
                to,
                value,
                payload,
                chainId,
                accessList,
                versionedHashes);
        break;
      case ACCESS_LIST:
        preimage =
            accessListPreimage(
                nonce,
                gasPrice,
                gasLimit,
                to,
                value,
                payload,
                accessList.orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Developer error: the transaction should be guaranteed to have an access list here")),
                chainId);
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
      final Wei maxPriorityFeePerGas,
      final Wei maxFeePerGas,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId,
      final Optional<List<AccessListEntry>> accessList) {
    final Bytes encoded =
        RLP.encode(
            rlpOutput -> {
              rlpOutput.startList();
              eip1559PreimageFields(
                  nonce,
                  maxPriorityFeePerGas,
                  maxFeePerGas,
                  gasLimit,
                  to,
                  value,
                  payload,
                  chainId,
                  accessList,
                  rlpOutput);
              rlpOutput.endList();
            });
    return Bytes.concatenate(Bytes.of(TransactionType.EIP1559.getSerializedType()), encoded);
  }

  private static void eip1559PreimageFields(
      final long nonce,
      final Wei maxPriorityFeePerGas,
      final Wei maxFeePerGas,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId,
      final Optional<List<AccessListEntry>> accessList,
      final RLPOutput rlpOutput) {
    rlpOutput.writeBigIntegerScalar(chainId.orElseThrow());
    rlpOutput.writeLongScalar(nonce);
    rlpOutput.writeUInt256Scalar(maxPriorityFeePerGas);
    rlpOutput.writeUInt256Scalar(maxFeePerGas);
    rlpOutput.writeLongScalar(gasLimit);
    rlpOutput.writeBytes(to.map(Bytes::copy).orElse(Bytes.EMPTY));
    rlpOutput.writeUInt256Scalar(value);
    rlpOutput.writeBytes(payload);
    TransactionEncoder.writeAccessList(rlpOutput, accessList);
  }

  private static Bytes blobPreimage(
      final long nonce,
      final Wei maxPriorityFeePerGas,
      final Wei maxFeePerGas,
      final Wei maxFeePerDataGas,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final Bytes payload,
      final Optional<BigInteger> chainId,
      final Optional<List<AccessListEntry>> accessList,
      final List<VersionedHash> versionedHashes) {

    final Bytes encoded =
        RLP.encode(
            rlpOutput -> {
              rlpOutput.startList();
              eip1559PreimageFields(
                  nonce,
                  maxPriorityFeePerGas,
                  maxFeePerGas,
                  gasLimit,
                  to,
                  value,
                  payload,
                  chainId,
                  accessList,
                  rlpOutput);
              rlpOutput.writeUInt256Scalar(maxFeePerDataGas);
              BlobTransactionEncoder.writeBlobVersionedHashes(rlpOutput, versionedHashes);
              rlpOutput.endList();
            });
    return Bytes.concatenate(Bytes.of(TransactionType.BLOB.getSerializedType()), encoded);
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
        && this.gasLimit == that.gasLimit
        && Objects.equals(this.gasPrice, that.gasPrice)
        && Objects.equals(this.maxPriorityFeePerGas, that.maxPriorityFeePerGas)
        && Objects.equals(this.maxFeePerGas, that.maxFeePerGas)
        && Objects.equals(this.maxFeePerDataGas, that.maxFeePerDataGas)
        && this.nonce == that.nonce
        && Objects.equals(this.payload, that.payload)
        && Objects.equals(this.signature, that.signature)
        && Objects.equals(this.to, that.to)
        && Objects.equals(this.value, that.value)
        && Objects.equals(this.getV(), that.getV());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        nonce,
        gasPrice,
        maxPriorityFeePerGas,
        maxFeePerGas,
        maxFeePerDataGas,
        gasLimit,
        to,
        value,
        payload,
        signature,
        chainId);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(
            transactionType.supportsBlob()
                ? "Blob"
                : isContractCreation() ? "ContractCreation" : "MessageCall")
        .append("{");
    sb.append("type=").append(getType()).append(", ");
    sb.append("nonce=").append(getNonce()).append(", ");
    getGasPrice()
        .ifPresent(
            gasPrice -> sb.append("gasPrice=").append(gasPrice.toShortHexString()).append(", "));
    if (getMaxPriorityFeePerGas().isPresent() && getMaxFeePerGas().isPresent()) {
      sb.append("maxPriorityFeePerGas=")
          .append(getMaxPriorityFeePerGas().map(Wei::toShortHexString).get())
          .append(", ");
      sb.append("maxFeePerGas=")
          .append(getMaxFeePerGas().map(Wei::toShortHexString).get())
          .append(", ");
      getMaxFeePerDataGas()
          .ifPresent(
              wei -> sb.append("maxFeePerDataGas=").append(wei.toShortHexString()).append(", "));
    }
    sb.append("gasLimit=").append(getGasLimit()).append(", ");
    if (getTo().isPresent()) sb.append("to=").append(getTo().get()).append(", ");
    sb.append("value=").append(getValue()).append(", ");
    sb.append("sig=").append(getSignature()).append(", ");
    if (chainId.isPresent()) sb.append("chainId=").append(getChainId().get()).append(", ");
    sb.append("payload=").append(getPayload());
    if (transactionType.equals(TransactionType.ACCESS_LIST)) {
      sb.append(", ").append("accessList=").append(maybeAccessList);
    }
    return sb.append("}").toString();
  }

  public String toTraceLog() {
    final StringBuilder sb = new StringBuilder();
    sb.append(getHash()).append("={");
    sb.append(
            transactionType.supportsBlob()
                ? "Blob"
                : isContractCreation() ? "ContractCreation" : "MessageCall")
        .append(", ");
    sb.append(getNonce()).append(", ");
    sb.append(getSender()).append(", ");
    sb.append(getType()).append(", ");
    getGasPrice()
        .ifPresent(
            gasPrice -> sb.append("gp: ").append(gasPrice.toHumanReadableString()).append(", "));
    if (getMaxPriorityFeePerGas().isPresent() && getMaxFeePerGas().isPresent()) {
      sb.append("mf: ")
          .append(getMaxFeePerGas().map(Wei::toHumanReadableString).get())
          .append(", ");
      sb.append("pf: ")
          .append(getMaxPriorityFeePerGas().map(Wei::toHumanReadableString).get())
          .append(", ");
      getMaxFeePerDataGas()
          .ifPresent(wei -> sb.append("df: ").append(wei.toHumanReadableString()).append(", "));
    }
    sb.append("gl: ").append(getGasLimit()).append(", ");
    sb.append("v: ").append(getValue().toHumanReadableString()).append(", ");
    getTo().ifPresent(to -> sb.append(to));
    return sb.append("}").toString();
  }

  public Optional<Address> contractAddress() {
    if (isContractCreation()) {
      return Optional.of(Address.contractAddress(getSender(), getNonce()));
    }
    return Optional.empty();
  }

  public static class Builder {
    private static final Optional<List<AccessListEntry>> EMPTY_ACCESS_LIST = Optional.of(List.of());

    protected TransactionType transactionType;

    protected long nonce = -1L;

    protected Wei gasPrice;

    protected Wei maxPriorityFeePerGas;

    protected Wei maxFeePerGas;
    protected Wei maxFeePerDataGas;

    protected long gasLimit = -1L;

    protected Optional<Address> to = Optional.empty();

    protected Wei value;

    protected SECPSignature signature;

    protected Bytes payload;

    protected Optional<List<AccessListEntry>> accessList = Optional.empty();

    protected Address sender;

    protected Optional<BigInteger> chainId = Optional.empty();
    protected Optional<BigInteger> v = Optional.empty();
    protected List<VersionedHash> versionedHashes = null;
    private BlobsWithCommitments blobsWithCommitments;

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

    public Builder maxPriorityFeePerGas(final Wei maxPriorityFeePerGas) {
      this.maxPriorityFeePerGas = maxPriorityFeePerGas;
      return this;
    }

    public Builder maxFeePerGas(final Wei maxFeePerGas) {
      this.maxFeePerGas = maxFeePerGas;
      return this;
    }

    public Builder maxFeePerDataGas(final Wei maxFeePerDataGas) {
      this.maxFeePerDataGas = maxFeePerDataGas;
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
      this.accessList =
          accessList == null
              ? Optional.empty()
              : accessList.isEmpty() ? EMPTY_ACCESS_LIST : Optional.of(accessList);
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

    public Builder versionedHashes(final List<VersionedHash> versionedHashes) {
      this.versionedHashes = versionedHashes;
      return this;
    }

    public Builder guessType() {
      if (versionedHashes != null && !versionedHashes.isEmpty()) {
        transactionType = TransactionType.BLOB;
      } else if (maxPriorityFeePerGas != null || maxFeePerGas != null) {
        transactionType = TransactionType.EIP1559;
      } else if (accessList.isPresent()) {
        transactionType = TransactionType.ACCESS_LIST;
      } else {
        transactionType = TransactionType.FRONTIER;
      }
      return this;
    }

    public TransactionType getTransactionType() {
      return transactionType;
    }

    public Transaction build() {
      if (transactionType == null) guessType();
      return new Transaction(
          transactionType,
          nonce,
          Optional.ofNullable(gasPrice),
          Optional.ofNullable(maxPriorityFeePerGas),
          Optional.ofNullable(maxFeePerGas),
          Optional.ofNullable(maxFeePerDataGas),
          gasLimit,
          to,
          value,
          signature,
          payload,
          accessList,
          sender,
          chainId,
          Optional.ofNullable(versionedHashes),
          Optional.ofNullable(blobsWithCommitments));
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
                  maxPriorityFeePerGas,
                  maxFeePerGas,
                  maxFeePerDataGas,
                  gasLimit,
                  to,
                  value,
                  payload,
                  accessList,
                  versionedHashes,
                  chainId),
              keys);
    }

    public Builder kzgBlobs(
        final List<KZGCommitment> kzgCommitments,
        final List<Blob> blobs,
        final List<KZGProof> kzgProofs) {
      if (this.versionedHashes == null || this.versionedHashes.isEmpty()) {
        this.versionedHashes =
            kzgCommitments.stream()
                .map(c -> new VersionedHash(SHA256_VERSION_ID, Sha256Hash.hash(c.getData())))
                .collect(Collectors.toList());
      }
      this.blobsWithCommitments =
          new BlobsWithCommitments(kzgCommitments, blobs, kzgProofs, versionedHashes);
      return this;
    }
  }
}
