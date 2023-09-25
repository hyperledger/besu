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
package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BlockTransactionSelectorFactory {
  private final List<TransactionSelectorFactory> transactionSelectorFactory;

  public BlockTransactionSelectorFactory(
      final List<TransactionSelectorFactory> transactionSelectorFactory) {
    this.transactionSelectorFactory = transactionSelectorFactory;
  }

  /**
   * Creates a list of AbstractTransactionSelector instances based on the provided context.
   *
   * @param context The block selection context.
   * @return A list of AbstractTransactionSelector instances.
   */
  public List<AbstractTransactionSelector> createTransactionSelectors(
      final BlockSelectionContext context) {
    List<AbstractTransactionSelector> selectors = new ArrayList<>();
    selectors.add(new BlockSizeTransactionSelector(context));
    selectors.add(new PriceTransactionSelector(context));
    selectors.add(new BlobPriceTransactionSelector(context));
    selectors.add(new ProcessingResultTransactionSelector(context));
    return selectors;
  }

  public List<TransactionSelector> createPluginTransactionSelectors() {
    return transactionSelectorFactory.stream()
        .map(TransactionSelectorFactory::create)
        .collect(Collectors.toList());
  }
}
