package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.datatypes.RequestType;

import com.google.common.collect.ImmutableMap;

public class MainnetRequestsValidator {
  public static RequestValidator pragueRequestsValidator() {
    final ImmutableMap<RequestType, RequestValidator> validators =
        ImmutableMap.of(RequestType.WITHDRAWAL, new WithdrawalRequestValidator());
    return new RequestsDelegateValidator(validators);
  }

  public static RequestProcessor pragueRequestsProcessors() {
    ImmutableMap<RequestType, RequestProcessor> processors =
        ImmutableMap.of(RequestType.WITHDRAWAL, new WithdrawalRequestProcessor());
    return new RequestDelegateProcessor(processors);
  }
}
