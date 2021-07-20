package org.hyperledger.besu.consensus.qbft.voting;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.OperationTracer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.abi.DefaultFunctionReturnDecoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;

public class ValidatorSmartContractController {

  private final Address contractAddress;
  private final TransactionSimulator transactionSimulator;
  private final Function getValidatorsFunction;

  public ValidatorSmartContractController(
      final Address contractAddress, final TransactionSimulator transactionSimulator) {
    this.contractAddress = contractAddress;
    this.transactionSimulator = transactionSimulator;

    try {
      this.getValidatorsFunction =
          FunctionEncoder.makeFunction("getValidators", List.of(), List.of(), List.of("address[]"));
    } catch (Exception e) {
      throw new RuntimeException("Error creating smart contract function", e);
    }
  }

  public Collection<Address> getValidators(final BlockHeader header) {
    final Bytes payload = Bytes.fromHexString(FunctionEncoder.encode(getValidatorsFunction));
    final CallParameter callParams =
        new CallParameter(null, contractAddress, -1, null, null, payload);
    return transactionSimulator
        .process(
            callParams,
            TransactionValidationParams.transactionSimulator(),
            OperationTracer.NO_TRACING,
            header)
        .map(this::parseResult)
        .orElse(Collections.emptyList());
  }

  @SuppressWarnings({"rawtypes", "unused", "unchecked"})
  private Collection<Address> parseResult(final TransactionSimulatorResult result) {
    switch (result.getResult().getStatus()) {
      case INVALID:
        throw new IllegalStateException("Invalid validator smart contract call");
      case FAILED:
        throw new IllegalStateException("Failed validator smart contract call");
      default:
        break;
    }

    final List<Type> decode =
        DefaultFunctionReturnDecoder.decode(
            result.getResult().getOutput().toHexString(),
            getValidatorsFunction.getOutputParameters());
    if (decode.size() == 1 && decode.get(0).getTypeAsString().equals("address[]")) {
      final List<org.web3j.abi.datatypes.Address> addresses =
          (List<org.web3j.abi.datatypes.Address>) decode.get(0).getValue();
      return addresses.stream()
          .map(a -> Address.fromHexString(a.getValue()))
          .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }
}
