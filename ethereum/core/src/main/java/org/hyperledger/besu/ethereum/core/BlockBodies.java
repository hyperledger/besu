/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core;

import java.util.Collections;
import java.util.List;

public class BlockBodies {

  private BlockBodies() {}

  public static BlockBody empty() {
    return new BlockBody(Collections.emptyList(), Collections.emptyList());
  }

  public static BlockBody of(
      final List<Transaction> transactions, final List<BlockHeader> ommerHeaders) {
    return new BlockBody(transactions, ommerHeaders);
  }

  public static BlockBody ofTransactions(final List<Transaction> transactionList) {
    return new BlockBody(transactionList, Collections.emptyList());
  }

  public static BlockBody of(final Transaction transaction) {
    return ofTransactions(Collections.singletonList(transaction));
  }

  public static BlockBody of(final Transaction transaction, final Transaction transaction1) {
    return ofTransactions(List.of(transaction, transaction1));
  }

  public static BlockBody ofOmmers(final List<BlockHeader> ommerHeaders) {
    return new BlockBody(Collections.emptyList(), ommerHeaders);
  }

  public static BlockBody of(final BlockHeader ommerHeader) {
    return ofOmmers(Collections.singletonList(ommerHeader));
  }
}
