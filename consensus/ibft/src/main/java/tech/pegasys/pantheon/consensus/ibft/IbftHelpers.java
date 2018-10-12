package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.ethereum.core.Hash;

public class IbftHelpers {

  public static final Hash EXPECTED_MIX_HASH =
      Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365");

  public static int calculateRequiredValidatorQuorum(final int validatorCount) {
    final int F = (validatorCount - 1) / 3;
    return (2 * F) + 1;
  }
}
