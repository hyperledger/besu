package net.consensys.pantheon.consensus.common;

import net.consensys.pantheon.ethereum.core.Address;

import java.util.Collection;

public interface ValidatorProvider {

  // Returns the current list of validators
  Collection<Address> getCurrentValidators();
}
