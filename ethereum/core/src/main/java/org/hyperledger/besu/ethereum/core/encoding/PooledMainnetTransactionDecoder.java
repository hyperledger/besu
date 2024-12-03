/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.encoding;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.TransactionType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

public class PooledMainnetTransactionDecoder extends MainnetTransactionDecoder {

  private static final ImmutableMap<TransactionType, MainnetTransactionDecoder.Decoder>
      POOLED_TRANSACTION_DECODERS =
          ImmutableMap.of(
              TransactionType.ACCESS_LIST,
              AccessListTransactionDecoder::decode,
              TransactionType.EIP1559,
              EIP1559TransactionDecoder::decode,
              TransactionType.BLOB,
              BlobPooledTransactionDecoder::decode,
              TransactionType.DELEGATE_CODE,
              CodeDelegationTransactionDecoder::decode);

  /**
   * Gets the decoder for a given transaction type
   *
   * @param transactionType the transaction type
   * @return the decoder
   */
  @VisibleForTesting
  @Override
  protected MainnetTransactionDecoder.Decoder getDecoder(final TransactionType transactionType) {
    return checkNotNull(
        POOLED_TRANSACTION_DECODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated decoding logic",
        transactionType);
  }
}
