package org.hyperledger.besu.tests.web3j.generated.permissioning;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
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
 * <p>Generated with web3j version 4.5.7.
 */
@SuppressWarnings("rawtypes")
public class PrivacyProxy extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506040516104853803806104858339818101604052602081101561003357600080fd5b5051600080546001600160a01b03199081163317909155600180546001600160a01b0390931692909116919091179055610413806100726000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80630b0235be146100465780633659cfe6146100b3578063f744b089146100db575b600080fd5b6100636004803603602081101561005c57600080fd5b5035610199565b60408051602080825283518183015283519192839290830191858101910280838360005b8381101561009f578181015183820152602001610087565b505050509050019250505060405180910390f35b6100d9600480360360208110156100c957600080fd5b50356001600160a01b03166102b9565b005b610185600480360360408110156100f157600080fd5b8135919081019060408101602082013564010000000081111561011357600080fd5b82018360208201111561012557600080fd5b8035906020019184602083028401116401000000008311171561014757600080fd5b9190808060200260200160405190810160405280939291908181526020018383602002808284376000920191909152509295506102f7945050505050565b604080519115158252519081900360200190f35b600154604080516305811adf60e11b81526004810184905290516060926001600160a01b0316918291630b0235be91602480820192600092909190829003018186803b1580156101e857600080fd5b505afa1580156101fc573d6000803e3d6000fd5b505050506040513d6000823e601f3d908101601f19168201604052602081101561022557600080fd5b810190808051604051939291908464010000000082111561024557600080fd5b90830190602082018581111561025a57600080fd5b825186602082028301116401000000008211171561027757600080fd5b82525081516020918201928201910280838360005b838110156102a457818101518382015260200161028c565b50505050905001604052505050915050919050565b6000546001600160a01b031633146102d057600080fd5b6001546001600160a01b03828116911614156102eb57600080fd5b6102f4816103bc565b50565b6001546040805163f744b08960e01b815260048101858152602482019283528451604483015284516000946001600160a01b031693849363f744b0899389938993919260640190602080860191028083838d5b8381101561036257818101518382015260200161034a565b505050509050019350505050602060405180830381600087803b15801561038857600080fd5b505af115801561039c573d6000803e3d6000fd5b505050506040513d60208110156103b257600080fd5b5051949350505050565b600180546001600160a01b0319166001600160a01b039290921691909117905556fea265627a7a72315820d1dcbb78ed14b21a5b50ade34bee9fc8297c7c6a94d385468e41387d65a635ea64736f6c634300050c0032";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

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

  public RemoteFunctionCall<TransactionReceipt> addParticipants(
      byte[] enclaveKey, List<byte[]> accounts) {
    final Function function =
        new Function(
            FUNC_ADDPARTICIPANTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        accounts, org.web3j.abi.datatypes.generated.Bytes32.class))),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<List> getParticipants(byte[] enclaveKey) {
    final Function function =
        new Function(
            FUNC_GETPARTICIPANTS,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey)),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Bytes32>>() {}));
    return new RemoteFunctionCall<List>(
        function,
        new Callable<List>() {
          @Override
          @SuppressWarnings("unchecked")
          public List call() throws Exception {
            List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
            return convertToNative(result);
          }
        });
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
