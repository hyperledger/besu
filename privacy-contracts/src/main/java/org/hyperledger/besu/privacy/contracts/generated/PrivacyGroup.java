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
import org.web3j.abi.datatypes.generated.Int256;
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
 * <p>Generated with web3j versionimport static org.junit.Assert.assertThat; 4.5.14.
 */
@SuppressWarnings("rawtypes")
public class PrivacyGroup extends Contract {
  public static final String BINARY =
      "608060405234801561001057600080fd5b50610954806100206000396000f3fe608060405234801561001057600080fd5b506004361061007d5760003560e01c806378b903371161005b57806378b9033714610173578063a69df4b514610195578063f744b0891461019f578063f83d08ba146102795761007d565b80630b0235be146100825780630d8e6e2c1461010557806361544c9114610123575b600080fd5b6100ae6004803603602081101561009857600080fd5b8101908080359060200190929190505050610283565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156100f15780820151818401526020810190506100d6565b505050509050019250505060405180910390f35b61010d6102ef565b6040518082815260200191505060405180910390f35b6101596004803603604081101561013957600080fd5b8101908080359060200190929190803590602001909291905050506102f9565b604051808215151515815260200191505060405180910390f35b61017b61031e565b604051808215151515815260200191505060405180910390f35b61019d610334565b005b61025f600480360360408110156101b557600080fd5b8101908080359060200190929190803590602001906401000000008111156101dc57600080fd5b8201836020820111156101ee57600080fd5b8035906020019184602083028401116401000000008311171561021057600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f820116905080830192505050505050509192919290505050610369565b604051808215151515815260200191505060405180910390f35b6102816103f3565b005b606061028e82610427565b61029757600080fd5b60028054806020026020016040519081016040528092919081815260200182805480156102e357602002820191906000526020600020905b8154815260200190600101908083116102cf575b50505050509050919050565b6000600154905090565b600061030483610427565b61030d57600080fd5b61031682610447565b905092915050565b60008060009054906101000a900460ff16905090565b6000809054906101000a900460ff161561034d57600080fd5b60016000806101000a81548160ff021916908315150217905550565b60008060009054906101000a900460ff161561038457600080fd5b6000600280549050141561039d5761039b8361052a565b505b6103a683610427565b6103af57600080fd5b60006103bb848461059c565b905060016000806101000a81548160ff0219169083151502179055506001600081548092919060010191905055508091505092915050565b6000809054906101000a900460ff1661040b57600080fd5b60008060006101000a81548160ff021916908315150217905550565b600080600360008481526020019081526020016000205414159050919050565b6000806003600084815260200190815260200160002054905060008111801561047557506002805490508111155b1561051f5760028054905081146104e357600060026001600280549050038154811061049d57fe5b9060005260206000200154905080600260018403815481106104bb57fe5b9060005260206000200181905550816003600083815260200190815260200160002081905550505b60016002818180549050039150816104fb919061087e565b50600060036000858152602001908152602001600020819055506001915050610525565b60009150505b919050565b600080600360008481526020019081526020016000205414156105925760028290806001815401808255809150509060018203906000526020600020016000909192909190915055600360008481526020019081526020016000208190555060019050610597565b600090505b919050565b6000806001905060008090505b8351811015610873578381815181106105be57fe5b6020026020010151851415610652577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf21360008583815181106105fc57fe5b60200260200101516040518083151515158152602001828152602001806020018281038252602f8152602001806108f1602f9139604001935050505060405180910390a181801561064b575060005b9150610866565b61066e84828151811061066157fe5b6020026020010151610427565b15610715577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf21360008583815181106106a257fe5b60200260200101516040518083151515158152602001828152602001806020018281038252601b8152602001807f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250602001935050505060405180910390a181801561070e575060005b9150610865565b600061073385838151811061072657fe5b602002602001015161052a565b9050606081610777576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250610791565b6040518060600160405280602181526020016108d0602191395b90507fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf213828785815181106107c157fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561081a5780820151818401526020810190506107ff565b50505050905090810190601f1680156108475780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a18380156108605750815b935050505b5b80806001019150506105a9565b508091505092915050565b8154818355818111156108a5578183600052602060002091820191016108a491906108aa565b5b505050565b6108cc91905b808211156108c85760008160009055506001016108b0565b5090565b9056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a723158209b8cbc4503135a6654e90319f2db6e4559f25bd3ae7c55c69c7c7c52670c67fa64736f6c634300050c0032";

  public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

  public static final String FUNC_CANEXECUTE = "canExecute";

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
    List<Contract.EventValuesWithLog> valueList =
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

  public RemoteFunctionCall<Boolean> canExecute() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_CANEXECUTE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
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

  public RemoteFunctionCall<BigInteger> getVersion() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GETVERSION,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Int256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<TransactionReceipt> lock() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_LOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
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

  public RemoteFunctionCall<TransactionReceipt> unlock() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_UNLOCK, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
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
