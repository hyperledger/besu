package net.consensys.pantheon.consensus.ibft.blockcreation;

import net.consensys.pantheon.consensus.common.ValidatorProvider;
import net.consensys.pantheon.consensus.ibft.IbftExtraData;
import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockCreator.ExtraDataCalculator;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.bytes.BytesValue;

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
