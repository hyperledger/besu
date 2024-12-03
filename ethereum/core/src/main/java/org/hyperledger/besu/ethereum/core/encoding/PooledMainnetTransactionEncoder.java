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

public class PooledMainnetTransactionEncoder extends MainnetTransactionEncoder {

  private static final ImmutableMap<TransactionType, Encoder> POOLED_TRANSACTION_ENCODERS =
      ImmutableMap.of(
          TransactionType.ACCESS_LIST,
          AccessListTransactionEncoder::encode,
          TransactionType.EIP1559,
          EIP1559TransactionEncoder::encode,
          TransactionType.BLOB,
          BlobPooledTransactionEncoder::encode,
          TransactionType.DELEGATE_CODE,
          CodeDelegationEncoder::encode);

  @VisibleForTesting
  @Override
  protected Encoder getEncoder(final TransactionType transactionType) {
    return checkNotNull(
        POOLED_TRANSACTION_ENCODERS.get(transactionType),
        "Developer Error. A supported transaction type %s has no associated encoding logic",
        transactionType);
  }
}
