/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockInterface;
import org.hyperledger.besu.consensus.qbft.core.types.QbftContext;
import org.hyperledger.besu.consensus.qbft.core.types.QbftValidatorProvider;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;

import org.mockito.quality.Strictness;

public class QbftContextBuilder {

  public static <T extends QbftContext> T setupContextWithBftBlockInterface(
      final Class<T> contextClazz,
      final Collection<Address> validators,
      final QbftBlockInterface bftBlockInterface) {
    final T bftContext = mock(contextClazz, withSettings().strictness(Strictness.LENIENT));
    final QbftValidatorProvider mockValidatorProvider =
        mock(QbftValidatorProvider.class, withSettings().strictness(Strictness.LENIENT));
    when(bftContext.validatorProvider()).thenReturn(mockValidatorProvider);
    when(mockValidatorProvider.getValidatorsAfterBlock(any())).thenReturn(validators);
    when(bftContext.blockInterface()).thenReturn(bftBlockInterface);
    when(bftContext.as(any())).thenReturn(bftContext);

    return bftContext;
  }
}
