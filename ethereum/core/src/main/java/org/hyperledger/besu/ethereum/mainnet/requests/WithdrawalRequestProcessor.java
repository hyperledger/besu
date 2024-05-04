package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper;

import java.util.List;
import java.util.Optional;

public class WithdrawalRequestProcessor implements RequestProcessor {
  @Override
  public Optional<List<Request>> process(final MutableWorldState mutableWorldState) {
    return Optional.of(
        WithdrawalRequestContractHelper.popWithdrawalRequestsFromQueue(mutableWorldState).stream()
            .map(r -> (Request) r)
            .toList());
  }
}
