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
package org.hyperledger.besu.services;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.txselection.BlockTransactionSelectionService;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

/** The Transaction Selection service implementation. */
public class TransactionSelectionServiceImpl implements TransactionSelectionService {

  /** Default Constructor. */
  public TransactionSelectionServiceImpl() {}

  private PluginTransactionSelectorFactory factory = PluginTransactionSelectorFactory.NO_OP_FACTORY;

  @Override
  public PluginTransactionSelector createPluginTransactionSelector(
      final SelectorsStateManager selectorsStateManager) {
    return factory.create(selectorsStateManager);
  }

  @Override
  public void selectPendingTransactions(
      final BlockTransactionSelectionService selectionService,
      final ProcessableBlockHeader pendingBlockHeader) {
    factory.selectPendingTransactions(selectionService, pendingBlockHeader);
  }

  @Override
  public void registerPluginTransactionSelectorFactory(
      final PluginTransactionSelectorFactory pluginTransactionSelectorFactory) {
    checkState(
        factory == PluginTransactionSelectorFactory.NO_OP_FACTORY,
        "PluginTransactionSelectorFactory was already registered");
    factory = pluginTransactionSelectorFactory;
  }
}
