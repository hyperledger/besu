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
      "608060405234801561001057600080fd5b50611666806100206000396000f3fe608060405234801561001057600080fd5b50600436106100885760003560e01c8063965a25ef1161005b578063965a25ef146101175780639738968c14610147578063a69df4b514610165578063f83d08ba1461016f57610088565b80630d8e6e2c1461008d5780631f52a8ee146100ab5780635aa68ac0146100db57806378b90337146100f9575b600080fd5b610095610179565b6040516100a29190610c58565b60405180910390f35b6100c560048036038101906100c09190610ce2565b610183565b6040516100d29190610d4a565b60405180910390f35b6100e361024b565b6040516100f09190610ec0565b60405180910390f35b610101610324565b60405161010e9190610d4a565b60405180910390f35b610131600480360381019061012c9190610f38565b61033a565b60405161013e9190610d4a565b60405180910390f35b61014f6104b4565b60405161015c9190610d4a565b60405180910390f35b61016d61050b565b005b6101776105d0565b005b6000600154905090565b60008060149054906101000a900460ff1661019d57600080fd5b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff161461022b576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161022290610fe2565b60405180910390fd5b60006102378484610693565b90506102416107e6565b8091505092915050565b60606002805480602002602001604051908101604052809291908181526020016000905b8282101561031b57838290600052602060002001805461028e90611031565b80601f01602080910402602001604051908101604052809291908181526020018280546102ba90611031565b80156103075780601f106102dc57610100808354040283529160200191610307565b820191906000526020600020905b8154815290600101906020018083116102ea57829003601f168201915b50505050508152602001906001019061026f565b50505050905090565b60008060149054906101000a900460ff16905090565b60008060149054906101000a900460ff161561035557600080fd5b600073ffffffffffffffffffffffffffffffffffffffff1660008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1614156103eb57326000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614610479576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161047090610fe2565b60405180910390fd5b60006104858484610827565b90506001600060146101000a81548160ff0219169083151502179055506104aa6107e6565b8091505092915050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614905090565b600060149054906101000a900460ff161561052557600080fd5b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff16146105b3576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016105aa90610fe2565b60405180910390fd5b6001600060146101000a81548160ff021916908315150217905550565b600060149054906101000a900460ff166105e957600080fd5b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163273ffffffffffffffffffffffffffffffffffffffff1614610677576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161066e90610fe2565b60405180910390fd5b60008060146101000a81548160ff021916908315150217905550565b600080600384846040516106a89291906110a2565b90815260200160405180910390205490506000811180156106ce57506002805490508111155b156107da57600280549050811461077a576000600260016002805490506106f591906110f4565b8154811061070657610705611128565b5b90600052602060002001905080600260018461072291906110f4565b8154811061073357610732611128565b5b9060005260206000200190805461074990611031565b610754929190610acf565b508160038260405161076691906111eb565b908152602001604051809103902081905550505b600280548061078c5761078b611202565b5b6001900381819060005260206000200160006107a89190610b5c565b90556000600385856040516107be9291906110a2565b90815260200160405180910390208190555060019150506107e0565b60009150505b92915050565b6001436107f391906110f4565b40416002604051602001610809939291906113bc565b60405160208183030381529060405280519060200120600181905550565b6000806001905060005b848490508110156109f05761086985858381811061085257610851611128565b5b90506020028101906108649190611409565b6109fb565b156108df577f1673b13ca99fc5f5d54f8ebc163339b3c03f5f661cec3f5dfe506fdbd2602de660008686848181106108a4576108a3611128565b5b90506020028101906108b69190611409565b6040516108c5939291906114f6565b60405180910390a18180156108d8575060005b91506109dd565b600061090e8686848181106108f7576108f6611128565b5b90506020028101906109099190611409565b610a29565b9050600081610952576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d626572000000000081525061096c565b604051806060016040528060218152602001611610602191395b90507f1673b13ca99fc5f5d54f8ebc163339b3c03f5f661cec3f5dfe506fdbd2602de6828888868181106109a3576109a2611128565b5b90506020028101906109b59190611409565b846040516109c6949392919061157f565b60405180910390a18380156109d85750815b935050505b80806109e8906115c6565b915050610831565b508091505092915050565b60008060038484604051610a109291906110a2565b9081526020016040518091039020541415905092915050565b60008060038484604051610a3e9291906110a2565b9081526020016040518091039020541415610ac4576002838390918060018154018082558091505060019003906000526020600020016000909192909192909192909192509190610a90929190610b9c565b5060028054905060038484604051610aa99291906110a2565b90815260200160405180910390208190555060019050610ac9565b600090505b92915050565b828054610adb90611031565b90600052602060002090601f016020900481019282610afd5760008555610b4b565b82601f10610b0e5780548555610b4b565b82800160010185558215610b4b57600052602060002091601f016020900482015b82811115610b4a578254825591600101919060010190610b2f565b5b509050610b589190610c22565b5090565b508054610b6890611031565b6000825580601f10610b7a5750610b99565b601f016020900490600052602060002090810190610b989190610c22565b5b50565b828054610ba890611031565b90600052602060002090601f016020900481019282610bca5760008555610c11565b82601f10610be357803560ff1916838001178555610c11565b82800160010185558215610c11579182015b82811115610c10578235825591602001919060010190610bf5565b5b509050610c1e9190610c22565b5090565b5b80821115610c3b576000816000905550600101610c23565b5090565b6000819050919050565b610c5281610c3f565b82525050565b6000602082019050610c6d6000830184610c49565b92915050565b600080fd5b600080fd5b600080fd5b600080fd5b600080fd5b60008083601f840112610ca257610ca1610c7d565b5b8235905067ffffffffffffffff811115610cbf57610cbe610c82565b5b602083019150836001820283011115610cdb57610cda610c87565b5b9250929050565b60008060208385031215610cf957610cf8610c73565b5b600083013567ffffffffffffffff811115610d1757610d16610c78565b5b610d2385828601610c8c565b92509250509250929050565b60008115159050919050565b610d4481610d2f565b82525050565b6000602082019050610d5f6000830184610d3b565b92915050565b600081519050919050565b600082825260208201905092915050565b6000819050602082019050919050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610dcb578082015181840152602081019050610db0565b83811115610dda576000848401525b50505050565b6000601f19601f8301169050919050565b6000610dfc82610d91565b610e068185610d9c565b9350610e16818560208601610dad565b610e1f81610de0565b840191505092915050565b6000610e368383610df1565b905092915050565b6000602082019050919050565b6000610e5682610d65565b610e608185610d70565b935083602082028501610e7285610d81565b8060005b85811015610eae5784840389528151610e8f8582610e2a565b9450610e9a83610e3e565b925060208a01995050600181019050610e76565b50829750879550505050505092915050565b60006020820190508181036000830152610eda8184610e4b565b905092915050565b60008083601f840112610ef857610ef7610c7d565b5b8235905067ffffffffffffffff811115610f1557610f14610c82565b5b602083019150836020820283011115610f3157610f30610c87565b5b9250929050565b60008060208385031215610f4f57610f4e610c73565b5b600083013567ffffffffffffffff811115610f6d57610f6c610c78565b5b610f7985828601610ee2565b92509250509250929050565b600082825260208201905092915050565b7f4f726967696e206e6f7420746865206f776e65722e0000000000000000000000600082015250565b6000610fcc601583610f85565b9150610fd782610f96565b602082019050919050565b60006020820190508181036000830152610ffb81610fbf565b9050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052602260045260246000fd5b6000600282049050600182168061104957607f821691505b6020821081141561105d5761105c611002565b5b50919050565b600081905092915050565b82818337600083830152505050565b60006110898385611063565b935061109683858461106e565b82840190509392505050565b60006110af82848661107d565b91508190509392505050565b6000819050919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b60006110ff826110bb565b915061110a836110bb565b92508282101561111d5761111c6110c5565b5b828203905092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603260045260246000fd5b60008190508160005260206000209050919050565b6000815461117981611031565b6111838186611063565b9450600182166000811461119e57600181146111af576111e2565b60ff198316865281860193506111e2565b6111b885611157565b60005b838110156111da578154818901526001820191506020810190506111bb565b838801955050505b50505092915050565b60006111f7828461116c565b915081905092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052603160045260246000fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b600061125c82611231565b9050919050565b61126c81611251565b82525050565b600081549050919050565b60008190508160005260206000209050919050565b60008190508160005260206000209050919050565b600081546112b481611031565b6112be8186610d9c565b945060018216600081146112d957600181146112eb5761131e565b60ff198316865260208601935061131e565b6112f485611292565b60005b83811015611316578154818901526001820191506020810190506112f7565b808801955050505b50505092915050565b600061133383836112a7565b905092915050565b6000600182019050919050565b600061135382611272565b61135d8185610d70565b93508360208202850161136f8561127d565b8060005b858110156113aa5784840389528161138b8582611327565b94506113968361133b565b925060208a01995050600181019050611373565b50829750879550505050505092915050565b60006060820190506113d16000830186610c49565b6113de6020830185611263565b81810360408301526113f08184611348565b9050949350505050565b600080fd5b600080fd5b600080fd5b60008083356001602003843603038112611426576114256113fa565b5b80840192508235915067ffffffffffffffff821115611448576114476113ff565b5b60208301925060018202360383131561146457611463611404565b5b509250929050565b600082825260208201905092915050565b6000611489838561146c565b935061149683858461106e565b61149f83610de0565b840190509392505050565b7f4163636f756e7420697320616c72656164792061204d656d6265720000000000600082015250565b60006114e0601b83610f85565b91506114eb826114aa565b602082019050919050565b600060608201905061150b6000830186610d3b565b818103602083015261151e81848661147d565b90508181036040830152611531816114d3565b9050949350505050565b600081519050919050565b60006115518261153b565b61155b8185610f85565b935061156b818560208601610dad565b61157481610de0565b840191505092915050565b60006060820190506115946000830187610d3b565b81810360208301526115a781858761147d565b905081810360408301526115bb8184611546565b905095945050505050565b60006115d1826110bb565b91507fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff821415611604576116036110c5565b5b60018201905091905056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79a2646970667358221220d3a7456a07fdf8421729e71e971f6d0cb06adecd41665ab8a87c08377118ad1664736f6c634300080c0033";

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
