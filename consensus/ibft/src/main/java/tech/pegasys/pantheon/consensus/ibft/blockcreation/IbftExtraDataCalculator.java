package tech.pegasys.pantheon.consensus.ibft.blockcreation;

import tech.pegasys.pantheon.consensus.common.ValidatorProvider;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
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
