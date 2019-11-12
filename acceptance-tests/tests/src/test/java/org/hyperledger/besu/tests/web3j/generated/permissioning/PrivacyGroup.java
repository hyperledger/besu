package org.hyperledger.besu.tests.web3j.generated.permissioning;

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
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
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
 * <p>Generated with web3j version 4.5.5.
 */
@SuppressWarnings("rawtypes")
public class PrivacyGroup extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5061066b806100206000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80635aa68ac0146100465780635b4ccc9d1461009e5780637ebd1b3014610155575b600080fd5b61004e61018e565b60408051602080825283518183015283519192839290830191858101910280838360005b8381101561008a578181015183820152602001610072565b505050509050019250505060405180910390f35b610141600480360360208110156100b457600080fd5b8101906020810181356401000000008111156100cf57600080fd5b8201836020820111156100e157600080fd5b8035906020019184602083028401116401000000008311171561010357600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600092019190915250929550610202945050505050565b604080519115158252519081900360200190f35b6101726004803603602081101561016b57600080fd5b503561051e565b604080516001600160a01b039092168252519081900360200190f35b606061019933610545565b6101a257600080fd5b60008054806020026020016040519081016040528092919081815260200182805480156101f857602002820191906000526020600020905b81546001600160a01b031681526001909101906020018083116101da575b5050505050905090565b600061020d33610545565b61021657600080fd5b600160005b83518110156105155783818151811061023057fe5b60200260200101516001600160a01b0316336001600160a01b031614156102e8577f349a5eb45b2e6e37ce4d053fbdb8474c5fb0eb89ce2d06f37158df3170fbf854600085838151811061028057fe5b60200260200101516040518083151515158152602001826001600160a01b03166001600160a01b03168152602001806020018281038252602f815260200180610608602f9139604001935050505060405180910390a18180156102e1575060005b915061050d565b6103048482815181106102f757fe5b6020026020010151610545565b156103ae577f349a5eb45b2e6e37ce4d053fbdb8474c5fb0eb89ce2d06f37158df3170fbf854600085838151811061033857fe5b6020908102919091018101516040805193151584526001600160a01b03909116918301919091526060828201819052601b908301527f4163636f756e7420697320616c72656164792061204d656d62657200000000006080830152519081900360a00190a18180156102e157506000915061050d565b60006103cc8583815181106103bf57fe5b6020026020010151610562565b9050606081610410576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d626572000000000081525061042a565b6040518060600160405280602181526020016105e7602191395b90507f349a5eb45b2e6e37ce4d053fbdb8474c5fb0eb89ce2d06f37158df3170fbf8548287858151811061045a57fe5b6020026020010151836040518084151515158152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156104c25781810151838201526020016104aa565b50505050905090810190601f1680156104ef5780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a18380156105085750815b935050505b60010161021b565b5090505b919050565b6000818154811061052b57fe5b6000918252602090912001546001600160a01b0316905081565b6001600160a01b0316600090815260016020526040902054151590565b6001600160a01b0381166000908152600160205260408120546105de57506000805460018082018084557f290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e56390920180546001600160a01b0319166001600160a01b03861690811790915583526020819052604090922055610519565b50600091905056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a723158202755cea6d0922da0c4b963e5ec4205e654ea2d5b4f86d21dbaa0f031e2c832c664736f6c634300050c0032";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

  public static final String FUNC_WHITELIST = "whitelist";

  public static final Event MEMBERADDED_EVENT =
      new Event(
          "MemberAdded",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Bool>() {},
              new TypeReference<Address>() {},
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

  public List<MemberAddedEventResponse> getMemberAddedEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(MEMBERADDED_EVENT, transactionReceipt);
    ArrayList<MemberAddedEventResponse> responses =
        new ArrayList<MemberAddedEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      MemberAddedEventResponse typedResponse = new MemberAddedEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.adminAdded = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.account = (String) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.message = (String) eventValues.getNonIndexedValues().get(2).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<MemberAddedEventResponse> memberAddedEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, MemberAddedEventResponse>() {
              @Override
              public MemberAddedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(MEMBERADDED_EVENT, log);
                MemberAddedEventResponse typedResponse = new MemberAddedEventResponse();
                typedResponse.log = log;
                typedResponse.adminAdded =
                    (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.account =
                    (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.message =
                    (String) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<MemberAddedEventResponse> memberAddedEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(MEMBERADDED_EVENT));
    return memberAddedEventFlowable(filter);
  }

  public RemoteFunctionCall<TransactionReceipt> addParticipants(List<String> accounts) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_ADDPARTICIPANTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                    org.web3j.abi.datatypes.Address.class,
                    org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class))),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<List> getParticipants() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GETPARTICIPANTS,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Address>>() {}));
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

  public RemoteFunctionCall<String> whitelist(BigInteger param0) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_WHITELIST,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(param0)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
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

  public static class MemberAddedEventResponse extends BaseEventResponse {
    public Boolean adminAdded;

    public String account;

    public String message;
  }
}
