package org.hyperledger.besu.services;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.services.WorldViewService;

import java.util.Optional;

public class WorldViewServiceImpl implements WorldViewService {

  private final WorldView worldView;

  public WorldViewServiceImpl(final WorldView worldView) {
    this.worldView = worldView;
  }

  @Override
  public Optional<Account> getAccount(final Address address) {
    return Optional.ofNullable(worldView.get(address));
  }
}
