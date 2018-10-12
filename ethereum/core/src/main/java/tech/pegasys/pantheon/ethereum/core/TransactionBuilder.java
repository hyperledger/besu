package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

/** Convenience object for building {@link Transaction}s. */
public interface TransactionBuilder {

  /** @return A {@link Transaction} populated with the accumulated state. */
  Transaction build();

  /**
   * Constructs a {@link SECP256K1.Signature} based on the accumulated state and then builds a
   * corresponding {@link Transaction}.
   *
   * @param keys The keys to construct the transaction signature with.
   * @return A {@link Transaction} populated with the accumulated state.
   */
  Transaction signAndBuild(SECP256K1.KeyPair keys);

  /**
   * Populates the {@link TransactionBuilder} based on the RLP-encoded transaction and builds a
   * {@link Transaction}.
   *
   * <p>Note: the fields from the RLP-transaction will be extracted and replace any previously
   * populated fields.
   *
   * @param in The RLP-encoded transaction.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder populateFrom(RLPInput in);

  /**
   * Sets the chain id for the {@link Transaction}.
   *
   * @param chainId The chain id.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder chainId(int chainId);

  /**
   * Sets the gas limit for the {@link Transaction}.
   *
   * @param gasLimit The gas limit.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder gasLimit(long gasLimit);

  /**
   * Sets the gas price for the {@link Transaction}.
   *
   * @param gasPrice The gas price.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder gasPrice(Wei gasPrice);

  /**
   * Sets the nonce for the {@link Transaction}.
   *
   * @param nonce The nonce.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder nonce(long nonce);

  /**
   * Sets the payload for the {@link Transaction}.
   *
   * @param payload The payload.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder payload(BytesValue payload);

  /**
   * Sets the sender of the {@link Transaction}.
   *
   * @param sender The sender.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder sender(Address sender);

  /**
   * Sets the signature of the {@link Transaction}.
   *
   * @param signature The signature.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder signature(Signature signature);

  /**
   * Sets the recipient of the {@link Transaction}.
   *
   * @param to The recipent (can be null).
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder to(Address to);

  /**
   * Sets the {@link Wei} transfer value of the {@link Transaction}.
   *
   * @param value The transfer value.
   * @return The updated {@link TransactionBuilder}.
   */
  TransactionBuilder value(Wei value);
}
