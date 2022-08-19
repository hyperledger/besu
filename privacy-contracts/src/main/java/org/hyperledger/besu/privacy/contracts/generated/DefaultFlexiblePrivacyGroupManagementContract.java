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
import org.web3j.abi.datatypes.DynamicBytes;
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
 * <p>Generated with web3j version 1.4.1.
 */
@SuppressWarnings("rawtypes")
public class DefaultFlexiblePrivacyGroupManagementContract extends Contract {
  public static final String BINARY =
      "608060405234801561001057600080fd5b50611719806100206000396000f3fe608060405234801561001057600080fd5b50600436106100885760003560e01c8063965a25ef1161005b578063965a25ef146101175780639738968c14610147578063a69df4b514610165578063f83d08ba1461016f57610088565b80630d8e6e2c1461008d5780631f52a8ee146100ab5780635aa68ac0146100db57806378b90337146100f9575b600080fd5b610095610179565b6040516100a29190610c18565b60405180910390f35b6100c560048036038101906100c09190610d8d565b610183565b6040516100d29190610df1565b60405180910390f35b6100e3610249565b6040516100f09190610f56565b60405180910390f35b610101610322565b60405161010e9190610df1565b60405180910390f35b610131600480360381019061012c919061105e565b610338565b60405161013e9190610df1565b60405180910390f35b61014f6104b0565b60405161015c9190610df1565b60405180910390f35b61016d610507565b005b6101776105cc565b005b6000600154905090565b60008060149054906101000a900460ff1661019d57600080fd5b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff161461022b576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161022290611104565b60405180910390fd5b60006102368361068f565b90506102406107dd565b80915050919050565b60606002805480602002602001604051908101604052809291908181526020016000905b8282101561031957838290600052602060002001805461028c90611153565b80601f01602080910402602001604051908101604052809291908181526020018280546102b890611153565b80156103055780601f106102da57610100808354040283529160200191610305565b820191906000526020600020905b8154815290600101906020018083116102e857829003601f168201915b50505050508152602001906001019061026d565b50505050905090565b60008060149054906101000a900460ff16905090565b60008060149054906101000a900460ff161561035357600080fd5b600073ffffffffffffffffffffffffffffffffffffffff1660008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1614156103e957326000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614610477576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161046e90611104565b60405180910390fd5b60006104828361081e565b90506001600060146101000a81548160ff0219169083151502179055506104a76107dd565b80915050919050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614905090565b600060149054906101000a900460ff161561052157600080fd5b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff16146105af576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016105a690611104565b60405180910390fd5b6001600060146101000a81548160ff021916908315150217905550565b600060149054906101000a900460ff166105e557600080fd5b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614610673576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161066a90611104565b60405180910390fd5b60008060146101000a81548160ff021916908315150217905550565b6000806003836040516106a291906111c1565b90815260200160405180910390205490506000811180156106c857506002805490508111155b156107d2576002805490508114610774576000600260016002805490506106ef9190611211565b81548110610700576106ff611245565b5b90600052602060002001905080600260018461071c9190611211565b8154811061072d5761072c611245565b5b9060005260206000200190805461074390611153565b61074e929190610a8f565b50816003826040516107609190611308565b908152602001604051809103902081905550505b60028054806107865761078561131f565b5b6001900381819060005260206000200160006107a29190610b1c565b905560006003846040516107b691906111c1565b90815260200160405180910390208190555060019150506107d8565b60009150505b919050565b6001436107ea9190611211565b40416002604051602001610800939291906114d9565b60405160208183030381529060405280519060200120600181905550565b6000806001905060005b83518110156109bb5761085484828151811061084757610846611245565b5b60200260200101516109c5565b156108bf577f1673b13ca99fc5f5d54f8ebc163339b3c03f5f661cec3f5dfe506fdbd2602de6600085838151811061088f5761088e611245565b5b60200260200101516040516108a59291906115ad565b60405180910390a18180156108b8575060005b91506109a8565b60006108e48583815181106108d7576108d6611245565b5b60200260200101516109f0565b9050600081610928576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250610942565b6040518060600160405280602181526020016116c3602191395b90507f1673b13ca99fc5f5d54f8ebc163339b3c03f5f661cec3f5dfe506fdbd2602de68287858151811061097957610978611245565b5b60200260200101518360405161099193929190611634565b60405180910390a18380156109a35750815b935050505b80806109b390611679565b915050610828565b5080915050919050565b6000806003836040516109d891906111c1565b90815260200160405180910390205414159050919050565b600080600383604051610a0391906111c1565b9081526020016040518091039020541415610a8557600282908060018154018082558091505060019003906000526020600020016000909190919091509080519060200190610a53929190610b5c565b50600280549050600383604051610a6a91906111c1565b90815260200160405180910390208190555060019050610a8a565b600090505b919050565b828054610a9b90611153565b90600052602060002090601f016020900481019282610abd5760008555610b0b565b82601f10610ace5780548555610b0b565b82800160010185558215610b0b57600052602060002091601f016020900482015b82811115610b0a578254825591600101919060010190610aef565b5b509050610b189190610be2565b5090565b508054610b2890611153565b6000825580601f10610b3a5750610b59565b601f016020900490600052602060002090810190610b589190610be2565b5b50565b828054610b6890611153565b90600052602060002090601f016020900481019282610b8a5760008555610bd1565b82601f10610ba357805160ff1916838001178555610bd1565b82800160010185558215610bd1579182015b82811115610bd0578251825591602001919060010190610bb5565b5b509050610bde9190610be2565b5090565b5b80821115610bfb576000816000905550600101610be3565b5090565b6000819050919050565b610c1281610bff565b82525050565b6000602082019050610c2d6000830184610c09565b92915050565b6000604051905090565b600080fd5b600080fd5b600080fd5b600080fd5b6000601f19601f8301169050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b610c9a82610c51565b810181811067ffffffffffffffff82111715610cb957610cb8610c62565b5b80604052505050565b6000610ccc610c33565b9050610cd88282610c91565b919050565b600067ffffffffffffffff821115610cf857610cf7610c62565b5b610d0182610c51565b9050602081019050919050565b82818337600083830152505050565b6000610d30610d2b84610cdd565b610cc2565b905082815260208101848484011115610d4c57610d4b610c4c565b5b610d57848285610d0e565b509392505050565b600082601f830112610d7457610d73610c47565b5b8135610d84848260208601610d1d565b91505092915050565b600060208284031215610da357610da2610c3d565b5b600082013567ffffffffffffffff811115610dc157610dc0610c42565b5b610dcd84828501610d5f565b91505092915050565b60008115159050919050565b610deb81610dd6565b82525050565b6000602082019050610e066000830184610de2565b92915050565b600081519050919050565b600082825260208201905092915050565b6000819050602082019050919050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610e72578082015181840152602081019050610e57565b83811115610e81576000848401525b50505050565b6000610e9282610e38565b610e9c8185610e43565b9350610eac818560208601610e54565b610eb581610c51565b840191505092915050565b6000610ecc8383610e87565b905092915050565b6000602082019050919050565b6000610eec82610e0c565b610ef68185610e17565b935083602082028501610f0885610e28565b8060005b85811015610f445784840389528151610f258582610ec0565b9450610f3083610ed4565b925060208a01995050600181019050610f0c565b50829750879550505050505092915050565b60006020820190508181036000830152610f708184610ee1565b905092915050565b600067ffffffffffffffff821115610f9357610f92610c62565b5b602082029050602081019050919050565b600080fd5b6000610fbc610fb784610f78565b610cc2565b90508083825260208201905060208402830185811115610fdf57610fde610fa4565b5b835b8181101561102657803567ffffffffffffffff81111561100457611003610c47565b5b8086016110118982610d5f565b85526020850194505050602081019050610fe1565b5050509392505050565b600082601f83011261104557611044610c47565b5b8135611055848260208601610fa9565b91505092915050565b60006020828403121561107457611073610c3d565b5b600082013567ffffffffffffffff81111561109257611091610c42565b5b61109e84828501611030565b91505092915050565b600082825260208201905092915050565b7f4f726967696e206e6f7420746865206f776e65722e0000000000000000000000600082015250565b60006110ee6015836110a7565b91506110f9826110b8565b602082019050919050565b6000602082019050818103600083015261111d816110e1565b9050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b6000600282049050600182168061116b57607f821691505b6020821081141561117f5761117e611124565b5b50919050565b600081905092915050565b600061119b82610e38565b6111a58185611185565b93506111b5818560208601610e54565b80840191505092915050565b60006111cd8284611190565b915081905092915050565b6000819050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b600061121c826111d8565b9150611227836111d8565b92508282101561123a576112396111e2565b5b828203905092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603260045260246000fd5b60008190508160005260206000209050919050565b6000815461129681611153565b6112a08186611185565b945060018216600081146112bb57600181146112cc576112ff565b60ff198316865281860193506112ff565b6112d585611274565b60005b838110156112f7578154818901526001820191506020810190506112d8565b838801955050505b50505092915050565b60006113148284611289565b915081905092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603160045260246000fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006113798261134e565b9050919050565b6113898161136e565b82525050565b600081549050919050565b60008190508160005260206000209050919050565b60008190508160005260206000209050919050565b600081546113d181611153565b6113db8186610e43565b945060018216600081146113f657600181146114085761143b565b60ff198316865260208601935061143b565b611411856113af565b60005b8381101561143357815481890152600182019150602081019050611414565b808801955050505b50505092915050565b600061145083836113c4565b905092915050565b6000600182019050919050565b60006114708261138f565b61147a8185610e17565b93508360208202850161148c8561139a565b8060005b858110156114c7578484038952816114a88582611444565b94506114b383611458565b925060208a01995050600181019050611490565b50829750879550505050505092915050565b60006060820190506114ee6000830186610c09565b6114fb6020830185611380565b818103604083015261150d8184611465565b9050949350505050565b600082825260208201905092915050565b600061153382610e38565b61153d8185611517565b935061154d818560208601610e54565b61155681610c51565b840191505092915050565b7f4163636f756e7420697320616c72656164792061204d656d6265720000000000600082015250565b6000611597601b836110a7565b91506115a282611561565b602082019050919050565b60006060820190506115c26000830185610de2565b81810360208301526115d48184611528565b905081810360408301526115e78161158a565b90509392505050565b600081519050919050565b6000611606826115f0565b61161081856110a7565b9350611620818560208601610e54565b61162981610c51565b840191505092915050565b60006060820190506116496000830186610de2565b818103602083015261165b8185611528565b9050818103604083015261166f81846115fb565b9050949350505050565b6000611684826111d8565b91507fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8214156116b7576116b66111e2565b5b60018201905091905056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79a26469706673582212209609f1a5cb6e2f48cc955820079a7ccc398144555e6ad4b22d2092ee447da95a64736f6c634300080c0033";

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
              new TypeReference<DynamicBytes>() {},
              new TypeReference<Utf8String>() {}));
  ;

  @Deprecated
  protected DefaultFlexiblePrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected DefaultFlexiblePrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected DefaultFlexiblePrivacyGroupManagementContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected DefaultFlexiblePrivacyGroupManagementContract(
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

  public RemoteFunctionCall<Boolean> canUpgrade() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_CANUPGRADE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
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

  @Deprecated
  public static DefaultFlexiblePrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DefaultFlexiblePrivacyGroupManagementContract(
        contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static DefaultFlexiblePrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DefaultFlexiblePrivacyGroupManagementContract(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static DefaultFlexiblePrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new DefaultFlexiblePrivacyGroupManagementContract(
        contractAddress, web3j, credentials, contractGasProvider);
  }

  public static DefaultFlexiblePrivacyGroupManagementContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new DefaultFlexiblePrivacyGroupManagementContract(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<DefaultFlexiblePrivacyGroupManagementContract> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DefaultFlexiblePrivacyGroupManagementContract.class,
        web3j,
        credentials,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<DefaultFlexiblePrivacyGroupManagementContract> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        DefaultFlexiblePrivacyGroupManagementContract.class,
        web3j,
        credentials,
        gasPrice,
        gasLimit,
        BINARY,
        "");
  }

  public static RemoteCall<DefaultFlexiblePrivacyGroupManagementContract> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DefaultFlexiblePrivacyGroupManagementContract.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<DefaultFlexiblePrivacyGroupManagementContract> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        DefaultFlexiblePrivacyGroupManagementContract.class,
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
