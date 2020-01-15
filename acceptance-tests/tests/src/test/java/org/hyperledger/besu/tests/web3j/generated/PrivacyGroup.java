package org.hyperledger.besu.tests.web3j.generated;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
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
 * <p>Generated with web3j version 4.5.9.
 */
@SuppressWarnings("rawtypes")
public class PrivacyGroup extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506107b2806100206000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80630b0235be1461004657806361544c91146100c9578063f744b08914610119575b600080fd5b6100726004803603602081101561005c57600080fd5b81019080803590602001909291905050506101f3565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156100b557808201518184015260208101905061009a565b505050509050019250505060405180910390f35b6100ff600480360360408110156100df57600080fd5b81019080803590602001909291908035906020019092919050505061024d565b604051808215151515815260200191505060405180910390f35b6101d96004803603604081101561012f57600080fd5b81019080803590602001909291908035906020019064010000000081111561015657600080fd5b82018360208201111561016857600080fd5b8035906020019184602083028401116401000000008311171561018a57600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f820116905080830192505050505050509192919290505050610272565b604051808215151515815260200191505060405180910390f35b6060600080548060200260200160405190810160405280929190818152602001828054801561024157602002820191906000526020600020905b81548152602001906001019080831161022d575b50505050509050919050565b600061025883610286565b61026157600080fd5b61026a826102a6565b905092915050565b600061027e8383610388565b905092915050565b600080600160008481526020019081526020016000205414159050919050565b600080600160008481526020019081526020016000205490506000811180156102d457506000805490508111155b1561037d57600080549050811461034157600080600160008054905003815481106102fb57fe5b90600052602060002001549050806000600184038154811061031957fe5b9060005260206000200181905550816001600083815260200190815260200160002081905550505b600160008181805490500391508161035991906106dc565b50600060016000858152602001908152602001600020819055506001915050610383565b60009150505b919050565b6000806001905060008090505b835181101561065f578381815181106103aa57fe5b602002602001015185141561043e577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf21360008583815181106103e857fe5b60200260200101516040518083151515158152602001828152602001806020018281038252602f81526020018061074f602f9139604001935050505060405180910390a1818015610437575060005b9150610652565b61045a84828151811061044d57fe5b6020026020010151610286565b15610501577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf213600085838151811061048e57fe5b60200260200101516040518083151515158152602001828152602001806020018281038252601b8152602001807f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250602001935050505060405180910390a18180156104fa575060005b9150610651565b600061051f85838151811061051257fe5b602002602001015161066a565b9050606081610563576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d626572000000000081525061057d565b60405180606001604052806021815260200161072e602191395b90507fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf213828785815181106105ad57fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b838110156106065780820151818401526020810190506105eb565b50505050905090810190601f1680156106335780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a183801561064c5750815b935050505b5b8080600101915050610395565b508091505092915050565b600080600160008481526020019081526020016000205414156106d257600082908060018154018082558091505090600182039060005260206000200160009091929091909150556001600084815260200190815260200160002081905550600190506106d7565b600090505b919050565b815481835581811115610703578183600052602060002091820191016107029190610708565b5b505050565b61072a91905b8082111561072657600081600090555060010161070e565b5090565b9056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a723158201ee441d03f41b8afc3c5aee38f40407194d8af7656db1c7191ae1c7e6bb6b32464736f6c634300050c0032";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

  public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

  public static final Event PARTICIPANTADDED_EVENT =
      new Event(
          "ParticipantAdded",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Bool>() {},
              new TypeReference<Bytes32>() {},
              new TypeReference<Utf8String>() {}));;

  @Deprecated
  protected PrivacyGroup(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected PrivacyGroup(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected PrivacyGroup(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected PrivacyGroup(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public List<ParticipantAddedEventResponse> getParticipantAddedEvents(
      TransactionReceipt transactionReceipt) {
    List<EventValuesWithLog> valueList =
        extractEventParametersWithLog(PARTICIPANTADDED_EVENT, transactionReceipt);
    ArrayList<ParticipantAddedEventResponse> responses =
        new ArrayList<ParticipantAddedEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      ParticipantAddedEventResponse typedResponse = new ParticipantAddedEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.adminAdded = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.account = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.message = (String) eventValues.getNonIndexedValues().get(2).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<ParticipantAddedEventResponse> participantAddedEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, ParticipantAddedEventResponse>() {
              @Override
              public ParticipantAddedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(PARTICIPANTADDED_EVENT, log);
                ParticipantAddedEventResponse typedResponse = new ParticipantAddedEventResponse();
                typedResponse.log = log;
                typedResponse.adminAdded =
                    (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.account =
                    (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.message =
                    (String) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<ParticipantAddedEventResponse> participantAddedEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(PARTICIPANTADDED_EVENT));
    return participantAddedEventFlowable(filter);
  }

  public RemoteFunctionCall<TransactionReceipt> addParticipants(
      byte[] _enclaveKey, List<byte[]> _accounts) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_ADDPARTICIPANTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        _accounts, org.web3j.abi.datatypes.generated.Bytes32.class))),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<List> getParticipants(byte[] _enclaveKey) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GETPARTICIPANTS,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey)),
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

  public RemoteFunctionCall<TransactionReceipt> removeParticipant(
      byte[] _enclaveKey, byte[] _account) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_REMOVEPARTICIPANT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey),
                new org.web3j.abi.datatypes.generated.Bytes32(_account)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static PrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new PrivacyGroup(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static PrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new PrivacyGroup(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static PrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new PrivacyGroup(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static PrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new PrivacyGroup(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<PrivacyGroup> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        PrivacyGroup.class, web3j, credentials, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<PrivacyGroup> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(PrivacyGroup.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  public static RemoteCall<PrivacyGroup> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        PrivacyGroup.class, web3j, transactionManager, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<PrivacyGroup> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        PrivacyGroup.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
  }

  public static class ParticipantAddedEventResponse extends BaseEventResponse {
    public Boolean adminAdded;

    public byte[] account;

    public String message;
  }
}
