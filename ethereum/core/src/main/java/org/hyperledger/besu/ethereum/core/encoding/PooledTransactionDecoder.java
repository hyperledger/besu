package org.hyperledger.besu.ethereum.core.encoding;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.hyperledger.besu.datatypes.TransactionType;

public class PooledTransactionDecoder extends TransactionDecoder{

  private static final ImmutableMap<TransactionType, TransactionDecoder.Decoder> POOLED_TRANSACTION_DECODERS =
    ImmutableMap.of(
      TransactionType.ACCESS_LIST,
      AccessListTransactionDecoder::decode,
      TransactionType.EIP1559,
      EIP1559TransactionDecoder::decode,
      TransactionType.BLOB,
      BlobPooledTransactionDecoder::decode ,
      TransactionType.DELEGATE_CODE,
      CodeDelegationTransactionDecoder::decode);

  /**
   * Gets the decoder for a given transaction type
   *
   * @param transactionType the transaction type
   * @return the decoder
   */
@VisibleForTesting
  protected static TransactionDecoder.Decoder getDecoder(
    final TransactionType transactionType) {
    return checkNotNull(
      POOLED_TRANSACTION_DECODERS.get(transactionType),
      "Developer Error. A supported transaction type %s has no associated decoding logic",
      transactionType);
  }
}
