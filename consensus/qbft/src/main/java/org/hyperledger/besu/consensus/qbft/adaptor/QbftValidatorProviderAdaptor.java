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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftValidatorProvider;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;

/**
 * Adaptor class to allow the {@link ValidatorProvider} to be used as a {@link
 * QbftValidatorProvider}.
 */
public class QbftValidatorProviderAdaptor implements QbftValidatorProvider {

  private final ValidatorProvider validatorProvider;

  /**
   * Create a new instance of the adaptor.
   *
   * @param validatorProvider the {@link ValidatorProvider} to adapt.
   */
  public QbftValidatorProviderAdaptor(final ValidatorProvider validatorProvider) {
    this.validatorProvider = validatorProvider;
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final QbftBlockHeader header) {
    return validatorProvider.getValidatorsAfterBlock(BlockUtil.toBesuBlockHeader(header));
  }

  @Override
  public Collection<Address> getValidatorsForBlock(final QbftBlockHeader header) {
    return validatorProvider.getValidatorsForBlock(BlockUtil.toBesuBlockHeader(header));
  }
}
