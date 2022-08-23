/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.privacy.contracts.generated;

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
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
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
 * <p>Generated with web3j version 1.4.1.
 */
@SuppressWarnings("rawtypes")
public class FlexiblePrivacyGroupManagementProxy extends Contract {
  public static final String BINARY =
      "60806040523480156200001157600080fd5b506040516200166d3803806200166d8339818101604052810190620000379190620000e8565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550506200011a565b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000620000b08262000083565b9050919050565b620000c281620000a3565b8114620000ce57600080fd5b50565b600081519050620000e281620000b7565b92915050565b6000602082840312156200010157620001006200007e565b5b60006200011184828501620000d1565b91505092915050565b611543806200012a6000396000f3fe608060405234801561001057600080fd5b506004361061009e5760003560e01c806378b903371161006657806378b9033714610149578063965a25ef146101675780639738968c14610197578063a69df4b5146101b5578063f83d08ba146101bf5761009e565b80630d8e6e2c146100a35780631f52a8ee146100c15780633659cfe6146100f15780635aa68ac01461010d5780635c60da1b1461012b575b600080fd5b6100ab6101c9565b6040516100b89190610a86565b60405180910390f35b6100db60048036038101906100d69190610b1a565b610264565b6040516100e89190610b82565b60405180910390f35b61010b60048036038101906101069190610bfb565b610357565b005b61011561066d565b6040516101229190610d83565b60405180910390f35b61013361070f565b6040516101409190610db4565b60405180910390f35b610151610733565b60405161015e9190610b82565b60405180910390f35b610181600480360381019061017c9190610e25565b6107ce565b60405161018e9190610b82565b60405180910390f35b61019f61087b565b6040516101ac9190610b82565b60405180910390f35b6101bd610918565b005b6101c76109a1565b005b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16630d8e6e2c6040518163ffffffff1660e01b8152600401602060405180830381865afa15801561023a573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061025e9190610e9e565b91505090565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff16905060008173ffffffffffffffffffffffffffffffffffffffff16631f52a8ee86866040518363ffffffff1660e01b81526004016102c7929190610f18565b6020604051808303816000875af11580156102e6573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061030a9190610f68565b9050801561034c577f52213552a930e6de0c0d7df74ece31dac1306b2c7e200ceded7a4442853189b58585604051610343929190610f18565b60405180910390a15b809250505092915050565b3073ffffffffffffffffffffffffffffffffffffffff166378b903376040518163ffffffff1660e01b8152600401602060405180830381865afa1580156103a2573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906103c69190610f68565b610405576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016103fc90610ff2565b60405180910390fd5b8073ffffffffffffffffffffffffffffffffffffffff1660008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff161415610494576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161048b906110aa565b60405180910390fd5b3073ffffffffffffffffffffffffffffffffffffffff16639738968c6040518163ffffffff1660e01b81526004016020604051808303816000875af11580156104e1573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906105059190610f68565b610544576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161053b9061113c565b60405180910390fd5b60003073ffffffffffffffffffffffffffffffffffffffff16635aa68ac06040518163ffffffff1660e01b8152600401600060405180830381865afa158015610591573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906105ba919061135e565b90506105c582610a2a565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663965a25ef836040518263ffffffff1660e01b81526004016106249190610d83565b6020604051808303816000875af1158015610643573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906106679190610f68565b50505050565b606060008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16635aa68ac06040518163ffffffff1660e01b8152600401600060405180830381865afa1580156106e0573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250810190610709919061135e565b91505090565b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff166378b903376040518163ffffffff1660e01b8152600401602060405180830381865afa1580156107a4573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906107c89190610f68565b91505090565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663965a25ef85856040518363ffffffff1660e01b815260040161082f9291906114e9565b6020604051808303816000875af115801561084e573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906108729190610f68565b91505092915050565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16639738968c6040518163ffffffff1660e01b81526004016020604051808303816000875af11580156108ee573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906109129190610f68565b91505090565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663a69df4b56040518163ffffffff1660e01b8152600401600060405180830381600087803b15801561098657600080fd5b505af115801561099a573d6000803e3d6000fd5b5050505050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f83d08ba6040518163ffffffff1660e01b8152600401600060405180830381600087803b158015610a0f57600080fd5b505af1158015610a23573d6000803e3d6000fd5b5050505050565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050565b6000819050919050565b610a8081610a6d565b82525050565b6000602082019050610a9b6000830184610a77565b92915050565b6000604051905090565b600080fd5b600080fd5b600080fd5b600080fd5b600080fd5b60008083601f840112610ada57610ad9610ab5565b5b8235905067ffffffffffffffff811115610af757610af6610aba565b5b602083019150836001820283011115610b1357610b12610abf565b5b9250929050565b60008060208385031215610b3157610b30610aab565b5b600083013567ffffffffffffffff811115610b4f57610b4e610ab0565b5b610b5b85828601610ac4565b92509250509250929050565b60008115159050919050565b610b7c81610b67565b82525050565b6000602082019050610b976000830184610b73565b92915050565b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000610bc882610b9d565b9050919050565b610bd881610bbd565b8114610be357600080fd5b50565b600081359050610bf581610bcf565b92915050565b600060208284031215610c1157610c10610aab565b5b6000610c1f84828501610be6565b91505092915050565b600081519050919050565b600082825260208201905092915050565b6000819050602082019050919050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610c8e578082015181840152602081019050610c73565b83811115610c9d576000848401525b50505050565b6000601f19601f8301169050919050565b6000610cbf82610c54565b610cc98185610c5f565b9350610cd9818560208601610c70565b610ce281610ca3565b840191505092915050565b6000610cf98383610cb4565b905092915050565b6000602082019050919050565b6000610d1982610c28565b610d238185610c33565b935083602082028501610d3585610c44565b8060005b85811015610d715784840389528151610d528582610ced565b9450610d5d83610d01565b925060208a01995050600181019050610d39565b50829750879550505050505092915050565b60006020820190508181036000830152610d9d8184610d0e565b905092915050565b610dae81610bbd565b82525050565b6000602082019050610dc96000830184610da5565b92915050565b60008083601f840112610de557610de4610ab5565b5b8235905067ffffffffffffffff811115610e0257610e01610aba565b5b602083019150836020820283011115610e1e57610e1d610abf565b5b9250929050565b60008060208385031215610e3c57610e3b610aab565b5b600083013567ffffffffffffffff811115610e5a57610e59610ab0565b5b610e6685828601610dcf565b92509250509250929050565b610e7b81610a6d565b8114610e8657600080fd5b50565b600081519050610e9881610e72565b92915050565b600060208284031215610eb457610eb3610aab565b5b6000610ec284828501610e89565b91505092915050565b600082825260208201905092915050565b82818337600083830152505050565b6000610ef78385610ecb565b9350610f04838584610edc565b610f0d83610ca3565b840190509392505050565b60006020820190508181036000830152610f33818486610eeb565b90509392505050565b610f4581610b67565b8114610f5057600080fd5b50565b600081519050610f6281610f3c565b92915050565b600060208284031215610f7e57610f7d610aab565b5b6000610f8c84828501610f53565b91505092915050565b600082825260208201905092915050565b7f54686520636f6e7472616374206973206c6f636b65642e000000000000000000600082015250565b6000610fdc601783610f95565b9150610fe782610fa6565b602082019050919050565b6000602082019050818103600083015261100b81610fcf565b9050919050565b7f54686520636f6e747261637420746f207570677261646520746f20686173207460008201527f6f20626520646966666572656e742066726f6d207468652063757272656e742060208201527f6d616e6167656d656e7420636f6e74726163742e000000000000000000000000604082015250565b6000611094605483610f95565b915061109f82611012565b606082019050919050565b600060208201905081810360008301526110c381611087565b9050919050565b7f4e6f7420616c6c6f77656420746f207570677261646520746865206d616e616760008201527f656d656e7420636f6e74726163742e0000000000000000000000000000000000602082015250565b6000611126602f83610f95565b9150611131826110ca565b604082019050919050565b6000602082019050818103600083015261115581611119565b9050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b61119482610ca3565b810181811067ffffffffffffffff821117156111b3576111b261115c565b5b80604052505050565b60006111c6610aa1565b90506111d2828261118b565b919050565b600067ffffffffffffffff8211156111f2576111f161115c565b5b602082029050602081019050919050565b600080fd5b600067ffffffffffffffff8211156112235761122261115c565b5b61122c82610ca3565b9050602081019050919050565b600061124c61124784611208565b6111bc565b90508281526020810184848401111561126857611267611203565b5b611273848285610c70565b509392505050565b600082601f8301126112905761128f610ab5565b5b81516112a0848260208601611239565b91505092915050565b60006112bc6112b7846111d7565b6111bc565b905080838252602082019050602084028301858111156112df576112de610abf565b5b835b8181101561132657805167ffffffffffffffff81111561130457611303610ab5565b5b808601611311898261127b565b855260208501945050506020810190506112e1565b5050509392505050565b600082601f83011261134557611344610ab5565b5b81516113558482602086016112a9565b91505092915050565b60006020828403121561137457611373610aab565b5b600082015167ffffffffffffffff81111561139257611391610ab0565b5b61139e84828501611330565b91505092915050565b6000819050919050565b60006113bd8385610c5f565b93506113ca838584610edc565b6113d383610ca3565b840190509392505050565b60006113eb8484846113b1565b90509392505050565b600080fd5b600080fd5b600080fd5b600080833560016020038436030381126114205761141f6113fe565b5b83810192508235915060208301925067ffffffffffffffff821115611448576114476113f4565b5b60018202360384131561145e5761145d6113f9565b5b509250929050565b6000602082019050919050565b600061147f8385610c33565b935083602084028501611491846113a7565b8060005b878110156114d75784840389526114ac8284611403565b6114b78682846113de565b95506114c284611466565b935060208b019a505050600181019050611495565b50829750879450505050509392505050565b60006020820190508181036000830152611504818486611473565b9050939250505056fea2646970667358221220d7eefee8f20c9de5911bbd7081429d08304b75cc59dda600e9adae257a50bdf664736f6c634300080c0033";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_CANEXECUTE = "canExecute";

  public static final String FUNC_CANUPGRADE = "canUpgrade";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

  public static final String FUNC_GETVERSION = "getVersion";

  public static final String FUNC_IMPLEMENTATION = "implementation";

  public static final String FUNC_LOCK = "lock";

  public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

  public static final String FUNC_UNLOCK = "unlock";

  public static final String FUNC_UPGRADETO = "upgradeTo";

  public static final Event PARTICIPANTREMOVED_EVENT =
      new Event(
          "ParticipantRemoved",
          Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
  ;

  @Deprecated
  protected FlexiblePrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected FlexiblePrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected FlexiblePrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected FlexiblePrivacyGroupManagementProxy(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public List<ParticipantRemovedEventResponse> getParticipantRemovedEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(PARTICIPANTREMOVED_EVENT, transactionReceipt);
    ArrayList<ParticipantRemovedEventResponse> responses =
        new ArrayList<ParticipantRemovedEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      ParticipantRemovedEventResponse typedResponse = new ParticipantRemovedEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.publicEnclaveKey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<ParticipantRemovedEventResponse> participantRemovedEventFlowable(
      EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, ParticipantRemovedEventResponse>() {
              @Override
              public ParticipantRemovedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(PARTICIPANTREMOVED_EVENT, log);
                ParticipantRemovedEventResponse typedResponse =
                    new ParticipantRemovedEventResponse();
                typedResponse.log = log;
                typedResponse.publicEnclaveKey =
                    (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<ParticipantRemovedEventResponse> participantRemovedEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(PARTICIPANTREMOVED_EVENT));
    return participantRemovedEventFlowable(filter);
  }

  public RemoteFunctionCall<TransactionReceipt> addParticipants(List<byte[]> _publicEnclaveKeys) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_ADDPARTICIPANTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.DynamicBytes>(
                    org.web3j.abi.datatypes.DynamicBytes.class,
                    org.web3j.abi.Utils.typeMap(
                        _publicEnclaveKeys, org.web3j.abi.datatypes.DynamicBytes.class))),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<Boolean> canExecute() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_CANEXECUTE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  public RemoteFunctionCall<TransactionReceipt> canUpgrade() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_CANUPGRADE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<List> getParticipants() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GETPARTICIPANTS,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<DynamicBytes>>() {}));
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

  public RemoteFunctionCall<byte[]> getVersion() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GETVERSION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  public RemoteFunctionCall<String> implementation() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_IMPLEMENTATION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteFunctionCall<TransactionReceipt> lock() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_LOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> removeParticipant(byte[] _participant) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_REMOVEPARTICIPANT,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(_participant)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> unlock() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_UNLOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> upgradeTo(String _newImplementation) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_UPGRADETO,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _newImplementation)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static FlexiblePrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new FlexiblePrivacyGroupManagementProxy(
        contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static FlexiblePrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new FlexiblePrivacyGroupManagementProxy(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static FlexiblePrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new FlexiblePrivacyGroupManagementProxy(
        contractAddress, web3j, credentials, contractGasProvider);
  }

  public static FlexiblePrivacyGroupManagementProxy load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new FlexiblePrivacyGroupManagementProxy(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<FlexiblePrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        FlexiblePrivacyGroupManagementProxy.class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  public static RemoteCall<FlexiblePrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        FlexiblePrivacyGroupManagementProxy.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<FlexiblePrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        FlexiblePrivacyGroupManagementProxy.class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<FlexiblePrivacyGroupManagementProxy> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _implementation) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
    return deployRemoteCall(
        FlexiblePrivacyGroupManagementProxy.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  public static class ParticipantRemovedEventResponse extends BaseEventResponse {
    public byte[] publicEnclaveKey;
  }
}
