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
package org.hyperledger.besu.consensus.common.bft;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;

import org.mockito.quality.Strictness;

public class BftContextBuilder {

  public static BftContext setupContextWithValidators(final Collection<Address> validators) {
    final BftContext bftContext =
        mock(BftContext.class, withSettings().strictness(Strictness.LENIENT));
    final ValidatorProvider mockValidatorProvider =
        mock(ValidatorProvider.class, withSettings().strictness(Strictness.LENIENT));
    final BftBlockInterface mockBftBlockInterface =
        mock(BftBlockInterface.class, withSettings().strictness(Strictness.LENIENT));
    when(bftContext.getValidatorProvider()).thenReturn(mockValidatorProvider);
    when(mockValidatorProvider.getValidatorsAfterBlock(any())).thenReturn(validators);
    when(bftContext.getBlockInterface()).thenReturn(mockBftBlockInterface);
    when(bftContext.as(any())).thenReturn(bftContext);
    return bftContext;
  }

  public static BftContext setupContextWithBftExtraData(
      final Collection<Address> validators, final BftExtraData bftExtraData) {
    return setupContextWithBftExtraData(BftContext.class, validators, bftExtraData);
  }

  public static <T extends BftContext> T setupContextWithBftExtraData(
      final Class<T> contextClazz,
      final Collection<Address> validators,
      final BftExtraData bftExtraData) {
    final T bftContext = mock(contextClazz, withSettings().strictness(Strictness.LENIENT));
    final ValidatorProvider mockValidatorProvider =
        mock(ValidatorProvider.class, withSettings().strictness(Strictness.LENIENT));
    final BftBlockInterface mockBftBlockInterface =
        mock(BftBlockInterface.class, withSettings().strictness(Strictness.LENIENT));
    when(bftContext.getValidatorProvider()).thenReturn(mockValidatorProvider);
    when(mockValidatorProvider.getValidatorsAfterBlock(any())).thenReturn(validators);
    when(bftContext.getBlockInterface()).thenReturn(mockBftBlockInterface);
    when(mockBftBlockInterface.getExtraData(any())).thenReturn(bftExtraData);
    when(bftContext.as(any())).thenReturn(bftContext);
    return bftContext;
  }

  public static BftContext setupContextWithBftExtraDataEncoder(
      final Collection<Address> validators, final BftExtraDataCodec bftExtraDataCodec) {
    return setupContextWithBftExtraDataEncoder(BftContext.class, validators, bftExtraDataCodec);
  }

  public static <T extends BftContext> T setupContextWithBftExtraDataEncoder(
      final Class<T> contextClazz,
      final Collection<Address> validators,
      final BftExtraDataCodec bftExtraDataCodec) {
    return setupContextWithBftBlockInterface(
        contextClazz, validators, new BftBlockInterface(bftExtraDataCodec));
  }

  public static <T extends BftContext> T setupContextWithBftBlockInterface(
      final Class<T> contextClazz,
      final Collection<Address> validators,
      final BftBlockInterface bftBlockInterface) {
    final T bftContext = mock(contextClazz, withSettings().strictness(Strictness.LENIENT));
    final ValidatorProvider mockValidatorProvider =
        mock(ValidatorProvider.class, withSettings().strictness(Strictness.LENIENT));
    when(bftContext.getValidatorProvider()).thenReturn(mockValidatorProvider);
    when(mockValidatorProvider.getValidatorsAfterBlock(any())).thenReturn(validators);
    when(bftContext.getBlockInterface()).thenReturn(bftBlockInterface);
    when(bftContext.as(any())).thenReturn(bftContext);

    return bftContext;
  }
}
