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
package org.hyperledger.besu.ethereum.core;

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainTransaction extends Transaction {
  public enum CrosschainTransactionType {
    ORIGINATING_TRANSACTION(Constants.ORIGINATING_TRANSACTION),
    SUBORDINATE_TRANSACTION(Constants.SUBORDINATE_TRANSACTION),
    SUBORDINATE_VIEW(Constants.SUBORDINATE_VIEW),
    ORIGINATING_LOCKABLE_CONTRACT_DEPLOY(Constants.ORIGINATING_LOCKABLE_CONTRACT_DEPLOY),
    SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY(Constants.SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY),
    SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY(Constants.SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY),
    UNLOCK_COMMIT_SIGNALLING_TRANSACTION(Constants.UNLOCK_COMMIT_SIGNALLING_TRANSACTION),
    UNLOCK_IGNORE_SIGNALLING_TRANSACTION(Constants.UNLOCK_IGNORE_SIGNALLING_TRANSACTION);

    private static class Constants {
      private static final int ORIGINATING_TRANSACTION = 0;
      private static final int SUBORDINATE_TRANSACTION = 1;
      private static final int SUBORDINATE_VIEW = 2;
      private static final int ORIGINATING_LOCKABLE_CONTRACT_DEPLOY = 3;
      private static final int SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY = 4;
      private static final int SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY = 5;
      private static final int UNLOCK_COMMIT_SIGNALLING_TRANSACTION = 6;
      private static final int UNLOCK_IGNORE_SIGNALLING_TRANSACTION = 7;
    }

    public int value;

    CrosschainTransactionType(final int val) {
      this.value = val;
    }

    public static CrosschainTransactionType create(final int val) {
      switch (val) {
        case Constants.ORIGINATING_TRANSACTION:
          return ORIGINATING_TRANSACTION;
        case Constants.SUBORDINATE_TRANSACTION:
          return SUBORDINATE_TRANSACTION;
        case Constants.SUBORDINATE_VIEW:
          return SUBORDINATE_VIEW;
        case Constants.ORIGINATING_LOCKABLE_CONTRACT_DEPLOY:
          return ORIGINATING_LOCKABLE_CONTRACT_DEPLOY;
        case Constants.SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY:
          return SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY;
        case Constants.SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY:
          return SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY;
        case Constants.UNLOCK_COMMIT_SIGNALLING_TRANSACTION:
          return UNLOCK_COMMIT_SIGNALLING_TRANSACTION;
        case Constants.UNLOCK_IGNORE_SIGNALLING_TRANSACTION:
          return UNLOCK_IGNORE_SIGNALLING_TRANSACTION;
        default:
          String error = "Unknown crosschain transaction type: " + val;
          LOG.info(error);
          throw new RuntimeException(error);
      }
    }

    public boolean isOriginatingTransaction() {
      return this.value == Constants.ORIGINATING_TRANSACTION;
    }

    public boolean isSubordinateTransaction() {
      return this.value == Constants.SUBORDINATE_TRANSACTION;
    }

    public boolean isSubordinateView() {
      return this.value == Constants.SUBORDINATE_VIEW;
    }

    public boolean isOriginatingLockableContractDeploy() {
      return this.value == Constants.ORIGINATING_LOCKABLE_CONTRACT_DEPLOY;
    }

    public boolean isSubordinateLockableContractDeploy() {
      return this.value == Constants.SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY;
    }

    public boolean isSingleChainLockableContractDeploy() {
      return this.value == Constants.SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY;
    }

    public boolean isLockableContractDeploy() {
      return isOriginatingLockableContractDeploy()
          || isSubordinateLockableContractDeploy()
          || isSingleChainLockableContractDeploy();
    }

    public boolean isUnlockCommitSignallingTransaction() {
      return this.value == Constants.UNLOCK_COMMIT_SIGNALLING_TRANSACTION;
    }

    public boolean isUnlockIgnoreSignallingTransaction() {
      return this.value == Constants.UNLOCK_IGNORE_SIGNALLING_TRANSACTION;
    }

    public boolean isSignallingTransaction() {
      return isUnlockCommitSignallingTransaction() || isUnlockIgnoreSignallingTransaction();
    }
  }

  // Type of Crosschain Transaction / View.
  private final CrosschainTransactionType type;

  // Ordered list of Subordinate Transactions and Views.
  private final List<CrosschainTransaction> subordinateTransactionsAndViews;
  private Iterator<CrosschainTransaction> subordinateTransactionAndViewsIterator;

  // Signed result if a subordinate view.
  private BytesValue signedResult;

  protected static Logger LOG = LogManager.getLogger();

  public static Builder builder() {
    return new Builder();
  }

  public static CrosschainTransaction readFrom(final RLPInput input) throws RLPException {
    input.enterList();

    final Builder builder =
        ((CrosschainTransaction.Builder) // without this cast, the compiler doesn't find
                // subordinateTransactionsAndViews!
                (builder()
                    .type(input.readLongScalar())
                    .nonce(input.readLongScalar())
                    .gasPrice(input.readUInt256Scalar(Wei::wrap))
                    .gasLimit(input.readLongScalar())
                    .to(input.readBytesValue(v -> v.size() == 0 ? null : Address.wrap(v)))
                    .value(input.readUInt256Scalar(Wei::wrap))
                    .payload(input.readBytesValue())))
            .subordinateTransactionsAndViews(
                input.readList(
                    rlp -> CrosschainTransaction.readFrom(RLP.input(input.readBytesValue()))));

    // TODO: probably we want to cap the readFrom recursivity at 1 level, because each transaction
    // only has use for the
    // next level of Subordinate Transactions it carries. Any subsequent sublevel will only be
    // useful when the containing
    // ST has started being processed.

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
    final BigInteger r = BytesValues.asUnsignedBigInteger(input.readUInt256Scalar().getBytes());
    final BigInteger s = BytesValues.asUnsignedBigInteger(input.readUInt256Scalar().getBytes());
    final SECP256K1.Signature signature = SECP256K1.Signature.create(r, s, recId);
    input.leaveList();

    chainId.ifPresent(builder::chainId);
    return (CrosschainTransaction) builder.signature(signature).build();
  }

  /**
   * Instantiates a transaction instance.
   *
   * @param type The type of Crosschain Transaction.
   * @param nonce the nonce
   * @param gasPrice the gas price
   * @param gasLimit the gas limit
   * @param to the transaction recipient
   * @param value the value being transferred to the recipient
   * @param signature the signature
   * @param payload the payload
   * @param subordinateTransactionsAndViews list of transactions and views
   * @param sender the transaction sender
   * @param chainId the chain id to apply the transaction to
   *     <p>The {@code to} will be an {@code Optional.empty()} for a contract creation transaction;
   *     otherwise it should contain an address.
   *     <p>The {@code chainId} must be greater than 0 to be applied to a specific chain; otherwise
   *     it will default to any chain.
   */
  public CrosschainTransaction(
      final CrosschainTransactionType type,
      final long nonce,
      final Wei gasPrice,
      final long gasLimit,
      final Optional<Address> to,
      final Wei value,
      final SECP256K1.Signature signature,
      final BytesValue payload,
      final List<CrosschainTransaction> subordinateTransactionsAndViews,
      final Address sender,
      final Optional<BigInteger> chainId) {
    super(nonce, gasPrice, gasLimit, to, value, signature, payload, sender, chainId);
    this.type = type;
    this.subordinateTransactionsAndViews = subordinateTransactionsAndViews;
  }

  public CrosschainTransactionType getType() {
    return this.type;
  }

  public List<CrosschainTransaction> getSubordinateTransactionsAndViews() {
    return subordinateTransactionsAndViews;
  }

  public void resetSubordinateTransactionsAndViewsList() {
    this.subordinateTransactionAndViewsIterator = this.subordinateTransactionsAndViews.iterator();
  }

  /**
   * Return the next Subordinate Transaction or View.
   *
   * @return The next Crosschain Transaction or View - or null if there are no more.
   */
  public CrosschainTransaction getNextSubordinateTransactionOrView() {
    if (!this.subordinateTransactionAndViewsIterator.hasNext()) {
      return null;
    }
    return this.subordinateTransactionAndViewsIterator.next();
  }

  @Override
  public Address getSender() {
    if (sender == null) {
      final SECP256K1.PublicKey publicKey =
          SECP256K1.PublicKey.recoverFromSignature(
                  getOrComputeSenderRecoveryHashCrossChain(), signature)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot recover public key from " + "signature for " + this));
      sender = Address.extract(Hash.hash(publicKey.getEncodedBytes()));
    }
    return sender;
  }

  // Note that Subordinate View results should not be included in this hash.
  private Bytes32 getOrComputeSenderRecoveryHashCrossChain() {
    if (hashNoSignature == null) {
      hashNoSignature =
          keccak256(
              RLP.encode(
                  out -> {
                    out.startList();
                    out.writeLongScalar(this.type.value);
                    out.writeLongScalar(nonce);
                    out.writeUInt256Scalar(gasPrice);
                    out.writeLongScalar(gasLimit);
                    out.writeBytesValue(to.isPresent() ? to.get() : BytesValue.EMPTY);
                    out.writeUInt256Scalar(value);
                    out.writeBytesValue(payload);
                    out.writeList(
                        subordinateTransactionsAndViews,
                        ((crosschainTransaction, rlpOutput) -> {
                          BytesValueRLPOutput temp = new BytesValueRLPOutput();
                          crosschainTransaction.writeTo(temp);
                          rlpOutput.writeBytesValue(temp.encoded());
                        }));
                    if (chainId.isPresent()) {
                      out.writeBigIntegerScalar(chainId.get());
                      out.writeUInt256Scalar(UInt256.ZERO);
                      out.writeUInt256Scalar(UInt256.ZERO);
                    }
                    out.endList();
                  }));
    }
    return hashNoSignature;
  }

  public void addSignedResult(final BytesValue signedResult) {
    this.signedResult = signedResult;
  }

  public BytesValue getSignedResult() {
    return this.signedResult;
  }

  public BytesValue getRawResult() {
    // TODO when the result really is signed, extract the raw result and return it.
    return this.signedResult;
  }

  /**
   * Writes the transaction to RLP
   *
   * @param out the output to write the transaction to
   */
  @Override
  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeLongScalar(getType().value);
    out.writeLongScalar(getNonce());
    out.writeUInt256Scalar(getGasPrice());
    out.writeLongScalar(getGasLimit());
    out.writeBytesValue(getTo().isPresent() ? getTo().get() : BytesValue.EMPTY);
    out.writeUInt256Scalar(getValue());
    out.writeBytesValue(getPayload());
    //    out.writeBytesValue(this.signedResult != null ? this.signedResult : BytesValue.EMPTY);

    // write child transactions and views from a list
    out.writeList(
        this.subordinateTransactionsAndViews,
        (crosschainTransaction, rlp) -> {
          rlp.writeBytesValue(RLP.encode(crosschainTransaction::writeTo));
        });

    writeSignature(out);

    out.endList();
  }

  // TODO should signed result be part of equals of not???
  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof CrosschainTransaction)) {
      return false;
    }
    final CrosschainTransaction that = (CrosschainTransaction) other;
    if (this.chainId.equals(that.chainId)
        && this.type.equals(that.type)
        && this.gasLimit == that.gasLimit
        && this.gasPrice.equals(that.gasPrice)
        && this.nonce == that.nonce
        && this.payload.equals(that.payload)
        && this.signature.equals(that.signature)
        && this.to.equals(that.to)
        && this.value.equals(that.value)) {
      if (this.subordinateTransactionsAndViews.size()
          == that.subordinateTransactionsAndViews.size()) {
        for (int i = 0; i < this.subordinateTransactionsAndViews.size(); i++) {
          if (!this.subordinateTransactionsAndViews
              .get(i)
              .equals(that.subordinateTransactionsAndViews.get(i))) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(isContractCreation() ? "ContractCreation" : "MessageCall").append("{");
    sb.append("type=").append(getType()).append(", ");
    sb.append("nonce=").append(getNonce()).append(", ");
    sb.append("gasPrice=").append(getGasPrice()).append(", ");
    sb.append("gasLimit=").append(getGasLimit()).append(", ");
    if (getTo().isPresent()) sb.append("to=").append(getTo().get()).append(", ");
    sb.append("value=").append(getValue()).append(", ");
    sb.append("sig=").append(getSignature()).append(", ");
    if (chainId.isPresent())
      sb.append("chainId=").append(getChainId().get().intValue()).append(", ");
    sb.append("payload=").append(getPayload()).append(", ");
    if (this.signedResult != null)
      sb.append("signedresult=").append(getSignedResult()).append(", ");
    sb.append("subordinateTransactionsAndViews=[");
    sb.append(
        subordinateTransactionsAndViews.stream()
            .map(x -> x.toString())
            .collect(Collectors.joining(", ")));
    sb.append("]");
    return sb.append("}").toString();
  }

  public static class Builder extends Transaction.Builder {

    CrosschainTransactionType type;
    List<CrosschainTransaction> subordinateTransactionsAndViews;

    public Builder subordinateTransactionsAndViews(
        final List<CrosschainTransaction> subordinateTransactionsAndViews) {
      this.subordinateTransactionsAndViews = subordinateTransactionsAndViews;
      return this;
    }

    public Builder type(final long type) {
      this.type = CrosschainTransactionType.create((int) type);
      return this;
    }

    @Override
    public CrosschainTransaction build() {
      return new CrosschainTransaction(
          type,
          nonce,
          gasPrice,
          gasLimit,
          Optional.ofNullable(to),
          value,
          signature,
          payload,
          subordinateTransactionsAndViews,
          sender,
          chainId);
    }
  }
}
