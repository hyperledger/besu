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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

public abstract class AbstractTestTransactionSelectorPlugin implements BesuPlugin {
  private ServiceManager serviceManager;

  private final int pluginNum;
  private final int preMultiple;
  private final int postMultiple;

  public AbstractTestTransactionSelectorPlugin(
      final int pluginNum, final int preMultiple, final int postMultiple) {
    this.pluginNum = pluginNum;
    this.preMultiple = preMultiple;
    this.postMultiple = postMultiple;
  }

  @Override
  public void register(final ServiceManager serviceManager) {
    this.serviceManager = serviceManager;
    serviceManager
        .getService(PicoCLIOptions.class)
        .orElseThrow()
        .addPicoCLIOptions("tx-selector" + pluginNum, this);
  }

  protected abstract boolean isEnabled();

  @Override
  public void beforeExternalServices() {
    if (isEnabled()) {
      serviceManager
          .getService(TransactionSelectionService.class)
          .orElseThrow()
          .registerPluginTransactionSelectorFactory(
              new PluginTransactionSelectorFactory() {
                @Override
                public PluginTransactionSelector create(
                    final SelectorsStateManager selectorsStateManager) {
                  return new PluginTransactionSelector() {

                    @Override
                    public TransactionSelectionResult evaluateTransactionPreProcessing(
                        final TransactionEvaluationContext evaluationContext) {
                      if (evaluationContext
                                  .getPendingTransaction()
                                  .getTransaction()
                                  .getValue()
                                  .getAsBigInteger()
                                  .longValue()
                              % preMultiple
                          == 0) {
                        return TransactionSelectionResult.invalid(
                            "value multiple of " + preMultiple);
                      }
                      return SELECTED;
                    }

                    @Override
                    public TransactionSelectionResult evaluateTransactionPostProcessing(
                        final TransactionEvaluationContext evaluationContext,
                        final TransactionProcessingResult processingResult) {
                      if (evaluationContext
                                  .getPendingTransaction()
                                  .getTransaction()
                                  .getValue()
                                  .getAsBigInteger()
                                  .longValue()
                              % postMultiple
                          == 0) {
                        return TransactionSelectionResult.invalid(
                            "value multiple of " + postMultiple);
                      }
                      return SELECTED;
                    }
                  };
                }
              });
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
