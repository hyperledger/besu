package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;

public interface WorldViewService extends BesuService {

  Optional<Account> getAccount(final Address address);
}
