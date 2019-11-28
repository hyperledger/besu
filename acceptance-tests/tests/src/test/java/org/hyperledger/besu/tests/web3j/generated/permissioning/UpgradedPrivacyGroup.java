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
import org.web3j.abi.FunctionEncoder;
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
 * <p>Generated with web3j version 4.5.7.
 */
@SuppressWarnings("rawtypes")
public class UpgradedPrivacyGroup extends Contract {
  private static final String BINARY =
      "60806040523480156200001157600080fd5b5060405162000ec638038062000ec6833981810160405260808110156200003757600080fd5b8151602083018051604051929492938301929190846401000000008211156200005f57600080fd5b9083019060208201858111156200007557600080fd5b82518660208202830111640100000000821117156200009357600080fd5b82525081516020918201928201910280838360005b83811015620000c2578181015183820152602001620000a8565b5050505090500160405260200180516040519392919084640100000000821115620000ec57600080fd5b9083019060208201858111156200010257600080fd5b82516401000000008111828201881017156200011d57600080fd5b82525081516020918201929091019080838360005b838110156200014c57818101518382015260200162000132565b50505050905090810190601f1680156200017a5780820380516001836020036101000a031916815260200191505b50604052602001805160405193929190846401000000008211156200019e57600080fd5b908301906020820185811115620001b457600080fd5b8251640100000000811182820188101715620001cf57600080fd5b82525081516020918201929091019080838360005b83811015620001fe578181015183820152602001620001e4565b50505050905090810190601f1680156200022c5780820380516001836020036101000a031916815260200191505b5060405250505062000244846200029660201b60201c565b5060028490556200025f84846001600160e01b036200030016565b50815162000275906000906020850190620005cb565b5080516200028b906001906020840190620005cb565b505050505062000670565b600081815260046020526040812054620002f75750600380546001818101928390557fc2575a0e9e593c00f959f8c92f12db2869c3395a3b0502d05e2516446f71f85b909101839055600083815260046020526040902091909155620002fb565b5060005b919050565b60006001815b8351811015620005af578381815181106200031d57fe5b6020026020010151851415620003a55760008051602062000ea683398151915260008583815181106200034c57fe5b60200260200101516040518083151515158152602001828152602001806020018281038252602f81526020018062000e77602f9139604001935050505060405180910390a18180156200039d575060005b9150620005a6565b620003ca848281518110620003b657fe5b6020026020010151620005b760201b60201c565b15620004595760008051602062000ea68339815191526000858381518110620003ef57fe5b602090810291909101810151604080519315158452918301526060828201819052601b908301527f4163636f756e7420697320616c72656164792061204d656d62657200000000006080830152519081900360a00190a18180156200039d575060009150620005a6565b6000620004808583815181106200046c57fe5b60200260200101516200029660201b60201c565b9050606081620004c6576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250620004e1565b60405180606001604052806021815260200162000e56602191395b905060008051602062000ea6833981519152828785815181106200050157fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b83811015620005595781810151838201526020016200053f565b50505050905090810190601f168015620005875780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a1838015620005a15750815b935050505b60010162000306565b509392505050565b600090815260046020526040902054151590565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f106200060e57805160ff19168380011785556200063e565b828001600101855582156200063e579182015b828111156200063e57825182559160200191906001019062000621565b506200064c92915062000650565b5090565b6200066d91905b808211156200064c576000815560010162000657565b90565b6107d680620006806000396000f3fe608060405234801561001057600080fd5b506004361061004c5760003560e01c80630b0235be1461005157806361544c91146100be578063e4d696e0146100f5578063f744b08914610123575b600080fd5b61006e6004803603602081101561006757600080fd5b50356101cd565b60408051602080825283518183015283519192839290830191858101910280838360005b838110156100aa578181015183820152602001610092565b505050509050019250505060405180910390f35b6100e1600480360360408110156100d457600080fd5b5080359060200135610252565b604080519115158252519081900360200190f35b6101216004803603604081101561010b57600080fd5b50803590602001356001600160a01b031661029e565b005b6100e16004803603604081101561013957600080fd5b8135919081019060408101602082013564010000000081111561015b57600080fd5b82018360208201111561016d57600080fd5b8035906020019184602083028401116401000000008311171561018f57600080fd5b9190808060200260200160405190810160405280939291908181526020018383602002808284376000920191909152509295506102ea945050505050565b6005546060906001600160a01b031633146101e757600080fd5b6101f082610327565b6101f957600080fd5b600380548060200260200160405190810160405280929190818152602001828054801561024557602002820191906000526020600020905b815481526020019060010190808311610231575b505050505090505b919050565b600061025d83610327565b61026657600080fd5b61026f82610327565b156102945761027d8261033b565b506000818152600460205260408120556001610298565b5060005b92915050565b60025482146102ac57600080fd5b6005546001600160a01b03828116911614156102c757600080fd5b600580546001600160a01b0319166001600160a01b039290921691909117905550565b6005546000906001600160a01b0316331461030457600080fd5b61030d83610327565b61031657600080fd5b6103208383610355565b9392505050565b600090815260046020526040902054151590565b600061034682610619565b905061035181610643565b5050565b60006001815b83518110156106115783818151811061037057fe5b6020026020010151851415610404577f3ad4a6804001c76d4aeb1ce3d1fa2b6f0507236afbb5143c82561641b34e7b5b60008583815181106103ae57fe5b60200260200101516040518083151515158152602001828152602001806020018281038252602f815260200180610773602f9139604001935050505060405180910390a18180156103fd575060005b9150610609565b61042084828151811061041357fe5b6020026020010151610327565b156104bc577f3ad4a6804001c76d4aeb1ce3d1fa2b6f0507236afbb5143c82561641b34e7b5b600085838151811061045457fe5b602090810291909101810151604080519315158452918301526060828201819052601b908301527f4163636f756e7420697320616c72656164792061204d656d62657200000000006080830152519081900360a00190a18180156103fd575060009150610609565b60006104da8583815181106104cd57fe5b60200260200101516106a0565b905060608161051e576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250610538565b604051806060016040528060218152602001610752602191395b90507f3ad4a6804001c76d4aeb1ce3d1fa2b6f0507236afbb5143c82561641b34e7b5b8287858151811061056857fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b838110156105be5781810151838201526020016105a6565b50505050905090810190601f1680156105eb5780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a18380156106045750815b935050505b60010161035b565b509392505050565b6000805b826003828154811061062b57fe5b9060005260206000200154146102985760010161061d565b6003546000190181101561068d576003816001018154811061066157fe5b90600052602060002001546003828154811061067957fe5b600091825260209091200155600101610643565b6003805490610351906000198301610707565b6000818152600460205260408120546106ff5750600380546001818101928390557fc2575a0e9e593c00f959f8c92f12db2869c3395a3b0502d05e2516446f71f85b90910183905560008381526004602052604090209190915561024d565b506000919050565b81548183558181111561072b5760008381526020902061072b918101908301610730565b505050565b61074e91905b8082111561074a5760008155600101610736565b5090565b9056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a723158201230a17405f387d5bb3b05ff50a4ae0b6b4a6c78d0599558544f90ed4c6a113664736f6c634300050c00324d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d69747465643ad4a6804001c76d4aeb1ce3d1fa2b6f0507236afbb5143c82561641b34e7b5b";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

  public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

  public static final String FUNC_SETPROXY = "setProxy";

  public static final Event MEMBERADDED_EVENT =
      new Event(
          "MemberAdded",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Bool>() {},
              new TypeReference<Bytes32>() {},
              new TypeReference<Utf8String>() {}));;

  @Deprecated
  protected UpgradedPrivacyGroup(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected UpgradedPrivacyGroup(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected UpgradedPrivacyGroup(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected UpgradedPrivacyGroup(
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
      typedResponse.account = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
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
                    (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
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

  public RemoteFunctionCall<TransactionReceipt> addParticipants(
      byte[] enclaveKey, List<byte[]> accounts) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
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
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
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

  public RemoteFunctionCall<TransactionReceipt> removeParticipant(
      byte[] enclaveKey, byte[] member) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_REMOVEPARTICIPANT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.generated.Bytes32(member)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> setProxy(byte[] enclaveKey, String proxy) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_SETPROXY,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.Address(160, proxy)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static UpgradedPrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new UpgradedPrivacyGroup(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static UpgradedPrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new UpgradedPrivacyGroup(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static UpgradedPrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new UpgradedPrivacyGroup(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static UpgradedPrivacyGroup load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new UpgradedPrivacyGroup(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<UpgradedPrivacyGroup> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      byte[] enclaveKey,
      List<byte[]> members,
      String _name,
      String _description) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        members, org.web3j.abi.datatypes.generated.Bytes32.class)),
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_description)));
    return deployRemoteCall(
        UpgradedPrivacyGroup.class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  public static RemoteCall<UpgradedPrivacyGroup> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      byte[] enclaveKey,
      List<byte[]> members,
      String _name,
      String _description) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        members, org.web3j.abi.datatypes.generated.Bytes32.class)),
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_description)));
    return deployRemoteCall(
        UpgradedPrivacyGroup.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<UpgradedPrivacyGroup> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      byte[] enclaveKey,
      List<byte[]> members,
      String _name,
      String _description) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        members, org.web3j.abi.datatypes.generated.Bytes32.class)),
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_description)));
    return deployRemoteCall(
        UpgradedPrivacyGroup.class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<UpgradedPrivacyGroup> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      byte[] enclaveKey,
      List<byte[]> members,
      String _name,
      String _description) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        members, org.web3j.abi.datatypes.generated.Bytes32.class)),
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_description)));
    return deployRemoteCall(
        UpgradedPrivacyGroup.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  public static class MemberAddedEventResponse extends BaseEventResponse {
    public Boolean adminAdded;

    public byte[] account;

    public String message;
  }
}
