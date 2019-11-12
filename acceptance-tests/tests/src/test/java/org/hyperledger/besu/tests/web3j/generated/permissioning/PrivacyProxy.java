package org.hyperledger.besu.tests.web3j.generated.permissioning;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.5.5.
 */
@SuppressWarnings("rawtypes")
public class PrivacyProxy extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060405161020f38038061020f8339818101604052602081101561003357600080fd5b5051600080546001600160a01b03199081163317909155600180546001600160a01b039093169290911691909117905561019d806100726000396000f3fe6080604052600436106100345760003560e01c80633659cfe61461006f5780635c60da1b146100a45780638da5cb5b146100d5575b6001546001600160a01b03168061004a57600080fd5b60405136600082376000803683855af43d806000843e81801561006b578184f35b8184fd5b34801561007b57600080fd5b506100a26004803603602081101561009257600080fd5b50356001600160a01b03166100ea565b005b3480156100b057600080fd5b506100b9610128565b604080516001600160a01b039092168252519081900360200190f35b3480156100e157600080fd5b506100b9610137565b6000546001600160a01b0316331461010157600080fd5b6001546001600160a01b038281169116141561011c57600080fd5b61012581610146565b50565b6001546001600160a01b031681565b6000546001600160a01b031681565b600180546001600160a01b0319166001600160a01b039290921691909117905556fea265627a7a72315820644644286b07f799393a4eb560e503ec34369904a7f2e358790d7583c37a100a64736f6c634300050c0032";

  public static final String FUNC_IMPLEMENTATION = "implementation";

  public static final String FUNC_OWNER = "owner";

  public static final String FUNC_UPGRADETO = "upgradeTo";

  @Deprecated
  protected PrivacyProxy(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected PrivacyProxy(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected PrivacyProxy(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected PrivacyProxy(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<String> implementation() {
    final Function function =
        new Function(
            FUNC_IMPLEMENTATION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteFunctionCall<String> owner() {
    final Function function =
        new Function(
            FUNC_OWNER,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteFunctionCall<TransactionReceipt> upgradeTo(String _newImplementation) {
    final Function function =
        new Function(
            FUNC_UPGRADETO,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _newImplementation)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static PrivacyProxy load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new PrivacyProxy(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static PrivacyProxy load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new PrivacyProxy(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static PrivacyProxy load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new PrivacyProxy(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static PrivacyProxy load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new PrivacyProxy(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<PrivacyProxy> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        PrivacyProxy.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
  }

  public static RemoteCall<PrivacyProxy> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        PrivacyProxy.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<PrivacyProxy> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        PrivacyProxy.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<PrivacyProxy> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        PrivacyProxy.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }
}
