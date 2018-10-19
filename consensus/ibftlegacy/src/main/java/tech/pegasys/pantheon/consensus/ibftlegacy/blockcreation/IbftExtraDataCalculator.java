/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibftlegacy.blockcreation;

import tech.pegasys.pantheon.consensus.common.ValidatorProvider;
import tech.pegasys.pantheon.consensus.ibftlegacy.IbftExtraData;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator.ExtraDataCalculator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.google.common.collect.Lists;

public class IbftExtraDataCalculator implements ExtraDataCalculator {

  private final ValidatorProvider validatorProvider;

  public IbftExtraDataCalculator(final ValidatorProvider validatorProvider) {
    this.validatorProvider = validatorProvider;
  }

  @Override
  public BytesValue get(final BlockHeader parent) {
    final BytesValue vanityData = BytesValue.wrap(new byte[32]);
    final IbftExtraData baseExtraData =
        new IbftExtraData(
            vanityData,
            Lists.newArrayList(),
            null,
            Lists.newArrayList(validatorProvider.getCurrentValidators()));
    return baseExtraData.encode();
  }
}
