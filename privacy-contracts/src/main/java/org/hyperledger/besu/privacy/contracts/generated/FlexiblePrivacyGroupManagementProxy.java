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
      "60806040523480156200001157600080fd5b5060405162001575380380620015758339818101604052810190620000379190620000e8565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550506200011a565b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000620000b08262000083565b9050919050565b620000c281620000a3565b8114620000ce57600080fd5b50565b600081519050620000e281620000b7565b92915050565b6000602082840312156200010157620001006200007e565b5b60006200011184828501620000d1565b91505092915050565b61144b806200012a6000396000f3fe608060405234801561001057600080fd5b506004361061009e5760003560e01c806378b903371161006657806378b9033714610149578063965a25ef146101675780639738968c14610197578063a69df4b5146101b5578063f83d08ba146101bf5761009e565b80630d8e6e2c146100a35780631f52a8ee146100c15780633659cfe6146100f15780635aa68ac01461010d5780635c60da1b1461012b575b600080fd5b6100ab6101c9565b6040516100b89190610a7e565b60405180910390f35b6100db60048036038101906100d69190610bf3565b610264565b6040516100e89190610c57565b60405180910390f35b61010b60048036038101906101069190610cd0565b610352565b005b610115610668565b6040516101229190610e47565b60405180910390f35b61013361070a565b6040516101409190610e78565b60405180910390f35b61015161072e565b60405161015e9190610c57565b60405180910390f35b610181600480360381019061017c9190610f79565b6107c9565b60405161018e9190610c57565b60405180910390f35b61019f610873565b6040516101ac9190610c57565b60405180910390f35b6101bd610910565b005b6101c7610999565b005b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16630d8e6e2c6040518163ffffffff1660e01b8152600401602060405180830381865afa15801561023a573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061025e9190610fee565b91505090565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff16905060008173ffffffffffffffffffffffffffffffffffffffff16631f52a8ee856040518263ffffffff1660e01b81526004016102c59190611065565b6020604051808303816000875af11580156102e4573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061030891906110b3565b90508015610348577f52213552a930e6de0c0d7df74ece31dac1306b2c7e200ceded7a4442853189b58460405161033f9190611065565b60405180910390a15b8092505050919050565b3073ffffffffffffffffffffffffffffffffffffffff166378b903376040518163ffffffff1660e01b8152600401602060405180830381865afa15801561039d573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906103c191906110b3565b610400576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016103f79061113d565b60405180910390fd5b8073ffffffffffffffffffffffffffffffffffffffff1660008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16141561048f576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610486906111f5565b60405180910390fd5b3073ffffffffffffffffffffffffffffffffffffffff16639738968c6040518163ffffffff1660e01b81526004016020604051808303816000875af11580156104dc573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061050091906110b3565b61053f576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161053690611287565b60405180910390fd5b60003073ffffffffffffffffffffffffffffffffffffffff16635aa68ac06040518163ffffffff1660e01b8152600401600060405180830381865afa15801561058c573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906105b591906113cc565b90506105c082610a22565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663965a25ef836040518263ffffffff1660e01b815260040161061f9190610e47565b6020604051808303816000875af115801561063e573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061066291906110b3565b50505050565b606060008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16635aa68ac06040518163ffffffff1660e01b8152600401600060405180830381865afa1580156106db573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f8201168201806040525081019061070491906113cc565b91505090565b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff166378b903376040518163ffffffff1660e01b8152600401602060405180830381865afa15801561079f573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906107c391906110b3565b91505090565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663965a25ef846040518263ffffffff1660e01b81526004016108289190610e47565b6020604051808303816000875af1158015610847573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061086b91906110b3565b915050919050565b60008060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16639738968c6040518163ffffffff1660e01b81526004016020604051808303816000875af11580156108e6573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061090a91906110b3565b91505090565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663a69df4b56040518163ffffffff1660e01b8152600401600060405180830381600087803b15801561097e57600080fd5b505af1158015610992573d6000803e3d6000fd5b5050505050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f83d08ba6040518163ffffffff1660e01b8152600401600060405180830381600087803b158015610a0757600080fd5b505af1158015610a1b573d6000803e3d6000fd5b5050505050565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050565b6000819050919050565b610a7881610a65565b82525050565b6000602082019050610a936000830184610a6f565b92915050565b6000604051905090565b600080fd5b600080fd5b600080fd5b600080fd5b6000601f19601f8301169050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b610b0082610ab7565b810181811067ffffffffffffffff82111715610b1f57610b1e610ac8565b5b80604052505050565b6000610b32610a99565b9050610b3e8282610af7565b919050565b600067ffffffffffffffff821115610b5e57610b5d610ac8565b5b610b6782610ab7565b9050602081019050919050565b82818337600083830152505050565b6000610b96610b9184610b43565b610b28565b905082815260208101848484011115610bb257610bb1610ab2565b5b610bbd848285610b74565b509392505050565b600082601f830112610bda57610bd9610aad565b5b8135610bea848260208601610b83565b91505092915050565b600060208284031215610c0957610c08610aa3565b5b600082013567ffffffffffffffff811115610c2757610c26610aa8565b5b610c3384828501610bc5565b91505092915050565b60008115159050919050565b610c5181610c3c565b82525050565b6000602082019050610c6c6000830184610c48565b92915050565b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b6000610c9d82610c72565b9050919050565b610cad81610c92565b8114610cb857600080fd5b50565b600081359050610cca81610ca4565b92915050565b600060208284031215610ce657610ce5610aa3565b5b6000610cf484828501610cbb565b91505092915050565b600081519050919050565b600082825260208201905092915050565b6000819050602082019050919050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610d63578082015181840152602081019050610d48565b83811115610d72576000848401525b50505050565b6000610d8382610d29565b610d8d8185610d34565b9350610d9d818560208601610d45565b610da681610ab7565b840191505092915050565b6000610dbd8383610d78565b905092915050565b6000602082019050919050565b6000610ddd82610cfd565b610de78185610d08565b935083602082028501610df985610d19565b8060005b85811015610e355784840389528151610e168582610db1565b9450610e2183610dc5565b925060208a01995050600181019050610dfd565b50829750879550505050505092915050565b60006020820190508181036000830152610e618184610dd2565b905092915050565b610e7281610c92565b82525050565b6000602082019050610e8d6000830184610e69565b92915050565b600067ffffffffffffffff821115610eae57610ead610ac8565b5b602082029050602081019050919050565b600080fd5b6000610ed7610ed284610e93565b610b28565b90508083825260208201905060208402830185811115610efa57610ef9610ebf565b5b835b81811015610f4157803567ffffffffffffffff811115610f1f57610f1e610aad565b5b808601610f2c8982610bc5565b85526020850194505050602081019050610efc565b5050509392505050565b600082601f830112610f6057610f5f610aad565b5b8135610f70848260208601610ec4565b91505092915050565b600060208284031215610f8f57610f8e610aa3565b5b600082013567ffffffffffffffff811115610fad57610fac610aa8565b5b610fb984828501610f4b565b91505092915050565b610fcb81610a65565b8114610fd657600080fd5b50565b600081519050610fe881610fc2565b92915050565b60006020828403121561100457611003610aa3565b5b600061101284828501610fd9565b91505092915050565b600082825260208201905092915050565b600061103782610d29565b611041818561101b565b9350611051818560208601610d45565b61105a81610ab7565b840191505092915050565b6000602082019050818103600083015261107f818461102c565b905092915050565b61109081610c3c565b811461109b57600080fd5b50565b6000815190506110ad81611087565b92915050565b6000602082840312156110c9576110c8610aa3565b5b60006110d78482850161109e565b91505092915050565b600082825260208201905092915050565b7f54686520636f6e7472616374206973206c6f636b65642e000000000000000000600082015250565b60006111276017836110e0565b9150611132826110f1565b602082019050919050565b600060208201905081810360008301526111568161111a565b9050919050565b7f54686520636f6e747261637420746f207570677261646520746f20686173207460008201527f6f20626520646966666572656e742066726f6d207468652063757272656e742060208201527f6d616e6167656d656e7420636f6e74726163742e000000000000000000000000604082015250565b60006111df6054836110e0565b91506111ea8261115d565b606082019050919050565b6000602082019050818103600083015261120e816111d2565b9050919050565b7f4e6f7420616c6c6f77656420746f207570677261646520746865206d616e616760008201527f656d656e7420636f6e74726163742e0000000000000000000000000000000000602082015250565b6000611271602f836110e0565b915061127c82611215565b604082019050919050565b600060208201905081810360008301526112a081611264565b9050919050565b60006112ba6112b584610b43565b610b28565b9050828152602081018484840111156112d6576112d5610ab2565b5b6112e1848285610d45565b509392505050565b600082601f8301126112fe576112fd610aad565b5b815161130e8482602086016112a7565b91505092915050565b600061132a61132584610e93565b610b28565b9050808382526020820190506020840283018581111561134d5761134c610ebf565b5b835b8181101561139457805167ffffffffffffffff81111561137257611371610aad565b5b80860161137f89826112e9565b8552602085019450505060208101905061134f565b5050509392505050565b600082601f8301126113b3576113b2610aad565b5b81516113c3848260208601611317565b91505092915050565b6000602082840312156113e2576113e1610aa3565b5b600082015167ffffffffffffffff811115611400576113ff610aa8565b5b61140c8482850161139e565b9150509291505056fea26469706673582212204124f39a2be595e1342726249c5797782cd26b3fc6fdc2430537b7a34228ada564736f6c634300080c0033";

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
