/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;

/** A utility class for body validation tasks. Implemented utilising BodyValidation */
public class BodyValidator {

  /**
   * Generates the transaction root for a list of transactions
   *
   * @param transactions the transactions
   * @return the transaction root
   */
  public Hash transactionsRoot(final List<Transaction> transactions) {
    return BodyValidation.transactionsRoot(transactions);
  }

  /**
   * Generates the withdrawals root for a list of withdrawals
   *
   * @param withdrawals the transactions
   * @return the transaction root
   */
  public Hash withdrawalsRoot(final List<Withdrawal> withdrawals) {
    return BodyValidation.withdrawalsRoot(withdrawals);
  }

  /**
   * Generates the requests root for a list of requests
   *
   * @param requests list of request
   * @return the requests root
   */
  public Hash requestsRoot(final List<Request> requests) {
    return BodyValidation.requestsRoot(requests);
  }

  /**
   * Generates the receipt root for a list of receipts
   *
   * @param receipts the receipts
   * @return the receipt root
   */
  public Hash receiptsRoot(final List<TransactionReceipt> receipts) {
    return BodyValidation.receiptsRoot(receipts);
  }

  /**
   * Generates the ommers hash for a list of ommer block headers
   *
   * @param ommers the ommer block headers
   * @return the ommers hash
   */
  public Hash ommersHash(final List<BlockHeader> ommers) {
    return BodyValidation.ommersHash(ommers);
  }

  /**
   * Generates the logs bloom filter for a list of transaction receipts
   *
   * @param receipts the transaction receipts
   * @return the logs bloom filter
   */
  public LogsBloomFilter logsBloom(final List<TransactionReceipt> receipts) {
    return BodyValidation.logsBloom(receipts);
  }
}
