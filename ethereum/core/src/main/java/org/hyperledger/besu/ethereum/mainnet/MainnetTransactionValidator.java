/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.SHA256Digest;

/**
 * Validates a transaction based on Frontier protocol runtime requirements.
 *
 * <p>The {@link MainnetTransactionValidator} performs the intrinsic gas cost check on the given
 * {@link Transaction}.
 */
public class MainnetTransactionValidator implements TransactionValidator {

  private final byte BLOB_COMMITMENT_VERSION_KZG = 0x01;

  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;
  private final FeeMarket feeMarket;

  private final boolean disallowSignatureMalleability;

  private final Optional<BigInteger> chainId;

  private final Set<TransactionType> acceptedTransactionTypes;

  private final int maxInitcodeSize;

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final FeeMarket feeMarket,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final int maxInitcodeSize) {
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
    this.feeMarket = feeMarket;
    this.disallowSignatureMalleability = checkSignatureMalleability;
    this.chainId = chainId;
    this.acceptedTransactionTypes = acceptedTransactionTypes;
    this.maxInitcodeSize = maxInitcodeSize;
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction,
      final Optional<Wei> baseFee,
      final TransactionValidationParams transactionValidationParams) {
    final ValidationResult<TransactionInvalidReason> signatureResult =
        validateTransactionSignature(transaction);
    if (!signatureResult.isValid()) {
      return signatureResult;
    }

    if (transaction.getType().supportsBlob() && transaction.getBlobsWithCommitments().isPresent()) {
      final ValidationResult<TransactionInvalidReason> blobsResult =
          validateTransactionsBlobs(transaction);
      if (!blobsResult.isValid()) {
        return blobsResult;
      }
    }

    final TransactionType transactionType = transaction.getType();
    if (!acceptedTransactionTypes.contains(transactionType)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          String.format(
              "Transaction type %s is invalid, accepted transaction types are %s",
              transactionType, acceptedTransactionTypes));
    }

    if (transaction.getNonce() == MAX_NONCE) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_OVERFLOW, "Nonce must be less than 2^64-1");
    }

    if (transaction.isContractCreation() && transaction.getPayload().size() > maxInitcodeSize) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INITCODE_TOO_LARGE,
          String.format(
              "Initcode size of %d exceeds maximum size of %s",
              transaction.getPayload().size(), maxInitcodeSize));
    }

    return validateCostAndFee(transaction, baseFee, transactionValidationParams);
  }

  private ValidationResult<TransactionInvalidReason> validateCostAndFee(
      final Transaction transaction,
      final Optional<Wei> maybeBaseFee,
      final TransactionValidationParams transactionValidationParams) {

    if (maybeBaseFee.isPresent()) {
      final Wei price = feeMarket.getTransactionPriceCalculator().price(transaction, maybeBaseFee);
      if (!transactionValidationParams.isAllowMaxFeeGasBelowBaseFee()
          && price.compareTo(maybeBaseFee.orElseThrow()) < 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.GAS_PRICE_BELOW_CURRENT_BASE_FEE,
            "gasPrice is less than the current BaseFee");
      }

      // assert transaction.max_fee_per_gas >= transaction.max_priority_fee_per_gas
      if (transaction.getType().supports1559FeeMarket()
          && transaction
                  .getMaxPriorityFeePerGas()
                  .get()
                  .getAsBigInteger()
                  .compareTo(transaction.getMaxFeePerGas().get().getAsBigInteger())
              > 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS,
            "max priority fee per gas cannot be greater than max fee per gas");
      }
    }

    if (transaction.getType().supportsBlob()) {
      final long txTotalDataGas = gasCalculator.dataGasCost(transaction.getBlobCount());
      if (txTotalDataGas > gasLimitCalculator.currentDataGasLimit()) {
        return ValidationResult.invalid(
            TransactionInvalidReason.TOTAL_DATA_GAS_TOO_HIGH,
            String.format(
                "total data gas %d exceeds max data gas per block %d",
                txTotalDataGas, gasLimitCalculator.currentDataGasLimit()));
      }
    }

    if (transaction.getType().supportsBlob()) {
      final long txTotalDataGas = gasCalculator.dataGasCost(transaction.getBlobCount());
      if (txTotalDataGas > gasLimitCalculator.currentDataGasLimit()) {
        return ValidationResult.invalid(
            TransactionInvalidReason.TOTAL_DATA_GAS_TOO_HIGH,
            String.format(
                "total data gas %d exceeds max data gas per block %d",
                txTotalDataGas, gasLimitCalculator.currentDataGasLimit()));
      }
    }

    final long intrinsicGasCost =
        gasCalculator.transactionIntrinsicGasCost(
                transaction.getPayload(), transaction.isContractCreation())
            + (transaction.getAccessList().map(gasCalculator::accessListGasCost).orElse(0L));
    if (Long.compareUnsigned(intrinsicGasCost, transaction.getGasLimit()) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
          String.format(
              "intrinsic gas cost %s exceeds gas limit %s",
              intrinsicGasCost, transaction.getGasLimit()));
    }

    return ValidationResult.valid();
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction,
      final Account sender,
      final TransactionValidationParams validationParams) {
    Wei senderBalance = Account.DEFAULT_BALANCE;
    long senderNonce = Account.DEFAULT_NONCE;
    Hash codeHash = Hash.EMPTY;

    if (sender != null) {
      senderBalance = sender.getBalance();
      senderNonce = sender.getNonce();
      if (sender.getCodeHash() != null) codeHash = sender.getCodeHash();
    }

    final Wei upfrontCost =
        transaction.getUpfrontCost(gasCalculator.dataGasCost(transaction.getBlobCount()));
    if (upfrontCost.compareTo(senderBalance) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
          String.format(
              "transaction up-front cost %s exceeds transaction sender account balance %s",
              upfrontCost.toQuantityHexString(), senderBalance.toQuantityHexString()));
    }

    if (transaction.getNonce() < senderNonce) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_TOO_LOW,
          String.format(
              "transaction nonce %s below sender account nonce %s",
              transaction.getNonce(), senderNonce));
    }

    if (!validationParams.isAllowFutureNonce() && senderNonce != transaction.getNonce()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_TOO_HIGH,
          String.format(
              "transaction nonce %s does not match sender account nonce %s.",
              transaction.getNonce(), senderNonce));
    }

    if (!validationParams.isAllowContractAddressAsSender() && !codeHash.equals(Hash.EMPTY)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format(
              "Sender %s has deployed code and so is not authorized to send transactions",
              transaction.getSender()));
    }

    return ValidationResult.valid();
  }

  private ValidationResult<TransactionInvalidReason> validateTransactionSignature(
      final Transaction transaction) {
    if (chainId.isPresent()
        && (transaction.getChainId().isPresent() && !transaction.getChainId().equals(chainId))) {
      return ValidationResult.invalid(
          TransactionInvalidReason.WRONG_CHAIN_ID,
          String.format(
              "transaction was meant for chain id %s and not this chain id %s",
              transaction.getChainId().get(), chainId.get()));
    }

    if (chainId.isEmpty() && transaction.getChainId().isPresent()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
          "replay protected signatures is not supported");
    }

    final SECPSignature signature = transaction.getSignature();
    final BigInteger halfCurveOrder = SignatureAlgorithmFactory.getInstance().getHalfCurveOrder();
    if (disallowSignatureMalleability && signature.getS().compareTo(halfCurveOrder) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          String.format(
              "Signature s value should be less than %s, but got %s",
              halfCurveOrder, signature.getS()));
    }

    // org.bouncycastle.math.ec.ECCurve.AbstractFp.decompressPoint throws an
    // IllegalArgumentException for "Invalid point compression" for bad signatures.
    try {
      transaction.getSender();
    } catch (final IllegalArgumentException e) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          "sender could not be extracted from transaction signature");
    }
    return ValidationResult.valid();
  }

  public ValidationResult<TransactionInvalidReason> validateTransactionsBlobs(
      final Transaction transaction) {

    if (transaction.getType().supportsBlob() && transaction.getTo().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          "transaction blob transactions cannot have a to address");
    }

    if (transaction.getBlobsWithCommitments().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs are empty, cannot verify without blobs");
    }

    BlobsWithCommitments blobsWithCommitments = transaction.getBlobsWithCommitments().get();

    if (blobsWithCommitments.getBlobs().size() != blobsWithCommitments.getKzgCommitments().size()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs and commitments are not the same size");
    }

    if (transaction.getVersionedHashes().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction versioned hashes are empty, cannot verify without versioned hashes");
    }
    final List<VersionedHash> versionedHashes = transaction.getVersionedHashes().get();

    for (int i = 0; i < versionedHashes.size(); i++) {
      final KZGCommitment commitment = blobsWithCommitments.getKzgCommitments().get(i);
      final VersionedHash versionedHash = versionedHashes.get(i);

      if (versionedHash.getVersionId() != BLOB_COMMITMENT_VERSION_KZG) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS,
            "transaction blobs commitment version is not supported. Expected "
                + BLOB_COMMITMENT_VERSION_KZG
                + ", found "
                + versionedHash.getVersionId());
      }

      final VersionedHash calculatedVersionedHash = hashCommitment(commitment);
      if (!calculatedVersionedHash.equals(versionedHash)) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS,
            "transaction blobs commitment hash does not match commitment");
      }
    }

    final Bytes blobs =
        blobsWithCommitments.getBlobs().stream()
            .map(Blob::getData)
            .reduce(Bytes::concatenate)
            .orElseThrow();

    final Bytes kzgCommitments =
        blobsWithCommitments.getKzgCommitments().stream()
            .map(KZGCommitment::getData)
            .reduce(Bytes::concatenate)
            .orElseThrow();

    final Bytes kzgProofs =
        blobsWithCommitments.getKzgProofs().stream()
            .map(KZGProof::getData)
            .reduce(Bytes::concatenate)
            .orElseThrow();

    final boolean kzgVerification =
        CKZG4844JNI.verifyBlobKzgProofBatch(
            blobs.toArrayUnsafe(),
            kzgCommitments.toArrayUnsafe(),
            kzgProofs.toArrayUnsafe(),
            blobsWithCommitments.getBlobs().size());

    if (!kzgVerification) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs kzg proof verification failed");
    }

    return ValidationResult.valid();
  }

  /*
  private VersionedHash hashCommitment(final Bytes32 commitment) {
    return new VersionedHash(
        VersionedHash.SHA256_VERSION_ID, Sha256Hash.hash(commitment));
  }

   */

  private VersionedHash hashCommitment(final KZGCommitment commitment) {
    final SHA256Digest digest = new SHA256Digest();
    digest.update(commitment.getData().toArrayUnsafe(), 0, commitment.getData().size());

    final byte[] dig = new byte[digest.getDigestSize()];

    digest.doFinal(dig, 0);

    dig[0] = BLOB_COMMITMENT_VERSION_KZG;
    return new VersionedHash(Bytes32.wrap(dig));
  }
}
