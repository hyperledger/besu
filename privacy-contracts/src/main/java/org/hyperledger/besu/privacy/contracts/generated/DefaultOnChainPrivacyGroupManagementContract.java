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
 * <p>Generated with web3j version 4.5.16.
 */
@SuppressWarnings("rawtypes")
public class DefaultOnChainPrivacyGroupManagementContract extends Contract {
  public static final String BINARY =
      "608060405234801561001057600080fd5b50610cdc806100206000396000f3fe608060405234801561001057600080fd5b50600436106100885760003560e01c8063a69df4b51161005b578063a69df4b51461014e578063b4926e2514610158578063f83d08ba14610228578063fd0177971461023257610088565b80630d8e6e2c1461008d5780635aa68ac0146100ab57806378b903371461010a5780639738968c1461012c575b600080fd5b610095610278565b6040518082815260200191505060405180910390f35b6100b3610282565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156100f65780820151818401526020810190506100db565b505050509050019250505060405180910390f35b6101126102da565b604051808215151515815260200191505060405180910390f35b6101346102f0565b604051808215151515815260200191505060405180910390f35b610156610347565b005b61020e6004803603602081101561016e57600080fd5b810190808035906020019064010000000081111561018b57600080fd5b82018360208201111561019d57600080fd5b803590602001918460208302840111640100000000831117156101bf57600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f820116905080830192505050505050509192919290505050610440565b604051808215151515815260200191505060405180910390f35b6102306105ee565b005b61025e6004803603602081101561024857600080fd5b81019080803590602001909291905050506106e5565b604051808215151515815260200191505060405180910390f35b6000600154905090565b606060028054806020026020016040519081016040528092919081815260200182805480156102d057602002820191906000526020600020905b8154815260200190600101908083116102bc575b5050505050905090565b60008060149054906101000a900460ff16905090565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614905090565b600060149054906101000a900460ff161561036157600080fd5b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614610423576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825260158152602001807f4f726967696e206e6f7420746865206f776e65722e000000000000000000000081525060200191505060405180910390fd5b6001600060146101000a81548160ff021916908315150217905550565b60008060149054906101000a900460ff161561045b57600080fd5b600073ffffffffffffffffffffffffffffffffffffffff166000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1614156104f257326000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff16146105b4576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825260158152602001807f4f726967696e206e6f7420746865206f776e65722e000000000000000000000081525060200191505060405180910390fd5b60006105bf836107e0565b90506001600060146101000a81548160ff0219169083151502179055506105e4610a20565b5080915050919050565b600060149054906101000a900460ff1661060757600080fd5b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff16146106c9576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825260158152602001807f4f726967696e206e6f7420746865206f776e65722e000000000000000000000081525060200191505060405180910390fd5b60008060146101000a81548160ff021916908315150217905550565b60008060149054906101000a900460ff166106ff57600080fd5b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff16146107c1576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825260158152602001807f4f726967696e206e6f7420746865206f776e65722e000000000000000000000081525060200191505060405180910390fd5b60006107cc83610ac0565b90506107d6610a20565b5080915050919050565b6000806001905060008090505b8351811015610a165761081284828151811061080557fe5b6020026020010151610ba3565b156108b9577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf213600085838151811061084657fe5b60200260200101516040518083151515158152602001828152602001806020018281038252601b8152602001807f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250602001935050505060405180910390a18180156108b2575060005b9150610a09565b60006108d78583815181106108ca57fe5b6020026020010151610bc3565b905060608161091b576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250610935565b604051806060016040528060218152602001610c87602191395b90507fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf2138287858151811061096557fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b838110156109be5780820151818401526020810190506109a3565b50505050905090810190601f1680156109eb5780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a1838015610a045750815b935050505b80806001019150506107ed565b5080915050919050565b60006001430340416002604051602001808481526020018373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1660601b81526014018280548015610a9a57602002820191906000526020600020905b815481526020019060010190808311610a86575b505093505050506040516020818303038152906040528051906020012060018190555090565b60008060036000848152602001908152602001600020549050600081118015610aee57506002805490508111155b15610b98576002805490508114610b5c576000600260016002805490500381548110610b1657fe5b906000526020600020015490508060026001840381548110610b3457fe5b9060005260206000200181905550816003600083815260200190815260200160002081905550505b6001600281818054905003915081610b749190610c35565b50600060036000858152602001908152602001600020819055506001915050610b9e565b60009150505b919050565b600080600360008481526020019081526020016000205414159050919050565b60008060036000848152602001908152602001600020541415610c2b5760028290806001815401808255809150509060018203906000526020600020016000909192909190915055600360008481526020019081526020016000208190555060019050610c30565b600090505b919050565b815481835581811115610c5c57818360005260206000209182019101610c5b9190610c61565b5b505050565b610c8391905b80821115610c7f576000816000905550600101610c67565b5090565b9056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79a265627a7a723158203cf86d2cc3e5535fa971d254a61356128e1d65ba27e6a2ed9b8f71c27f3d5ae364736f6c63430005110032";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_CANEXECUTE = "canExecute";

  public static final String FUNC_CANUPGRADE = "canUpgrade";

  public static final String FUNC_GETPARTICIPANTS = "getParticipants";

  public static final String FUNC_GETVERSION = "getVersion";

  public static final String FUNC_LOCK = "lock";

  public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

  public static final String FUNC_UNLOCK = "unlock";

  public static final Event PARTICIPANTADDED_EVENT =
      new Event(
          "ParticipantAdded",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Bool>() {},
              new TypeReference<Bytes32>() {},
              new TypeReference<Utf8String>() {}));;

  @Deprecated
  protected DefaultOnChainPrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected DefaultOnChainPrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected DefaultOnChainPrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected DefaultOnChainPrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public List<ParticipantAddedEventResponse> getParticipantAddedEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(PARTICIPANTADDED_EVENT, transactionReceipt);
    ArrayList<ParticipantAddedEventResponse> responses =
        new ArrayList<ParticipantAddedEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      ParticipantAddedEventResponse typedResponse = new ParticipantAddedEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.success = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.publicEnclaveKey = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
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
                typedResponse.success =
                    (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.publicEnclaveKey =
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

  public RemoteFunctionCall<TransactionReceipt> addParticipants(List<byte[]> _publicEnclaveKeys) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_ADDPARTICIPANTS,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                    org.web3j.abi.datatypes.generated.Bytes32.class,
                    org.web3j.abi.Utils.typeMap(
                        _publicEnclaveKeys, org.web3j.abi.datatypes.generated.Bytes32.class))),
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

  public RemoteFunctionCall<byte[]> getVersion() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GETVERSION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  public RemoteFunctionCall<TransactionReceipt> lock() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_LOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> removeParticipant(byte[] _member) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_REMOVEPARTICIPANT,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_member)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> unlock() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_UNLOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  @Deprecated
  public static DefaultOnChainPrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DefaultOnChainPrivacyGroupManagementContract(
        contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static DefaultOnChainPrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DefaultOnChainPrivacyGroupManagementContract(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static DefaultOnChainPrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new DefaultOnChainPrivacyGroupManagementContract(
        contractAddress, web3j, credentials, contractGasProvider);
  }

  public static DefaultOnChainPrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new DefaultOnChainPrivacyGroupManagementContract(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DefaultOnChainPrivacyGroupManagementContract.class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        DefaultOnChainPrivacyGroupManagementContract.class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        "");
  }

  public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DefaultOnChainPrivacyGroupManagementContract.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        DefaultOnChainPrivacyGroupManagementContract.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "");
  }

  public static class ParticipantAddedEventResponse extends BaseEventResponse {
    public Boolean success;

    public byte[] publicEnclaveKey;

    public String message;
  }
}
