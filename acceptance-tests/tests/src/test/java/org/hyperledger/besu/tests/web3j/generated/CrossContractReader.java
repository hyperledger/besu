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
package org.hyperledger.besu.tests.web3j.generated;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
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
 * <p>Generated with web3j version 4.5.0.
 */
@SuppressWarnings("rawtypes")
public class CrossContractReader extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506104b7806100206000396000f3fe608060405234801561001057600080fd5b506004361061007d5760003560e01c806383197ef01161005b57806383197ef0146100d8578063a087a87e146100e0578063d09de08a14610118578063e689ef8a146101205761007d565b8063305155f9146100825780635374ded2146100aa578063775c300c146100d0575b600080fd5b6100a86004803603602081101561009857600080fd5b50356001600160a01b0316610146565b005b6100a8600480360360208110156100c057600080fd5b50356001600160a01b03166101a2565b6100a86101e2565b6100a8610250565b610106600480360360208110156100f657600080fd5b50356001600160a01b0316610253565b60408051918252519081900360200190f35b6100a86102c5565b6100a86004803603602081101561013657600080fd5b50356001600160a01b03166102d0565b6000819050806001600160a01b03166383197ef06040518163ffffffff1660e01b8152600401600060405180830381600087803b15801561018657600080fd5b505af115801561019a573d6000803e3d6000fd5b505050505050565b6000819050806001600160a01b031663775c300c6040518163ffffffff1660e01b8152600401600060405180830381600087803b15801561018657600080fd5b60006040516101f090610310565b604051809103906000f08015801561020c573d6000803e3d6000fd5b50604080516001600160a01b038316815290519192507f9ac6876e0aa40667ffeaa9b359b5ed924f4cdd0e029eb6e9c369e78c68f711fb919081900360200190a150565b33ff5b600080829050806001600160a01b0316633fa4f2456040518163ffffffff1660e01b815260040160206040518083038186803b15801561029257600080fd5b505afa1580156102a6573d6000803e3d6000fd5b505050506040513d60208110156102bc57600080fd5b50519392505050565b600080546001019055565b6000819050806001600160a01b031663d09de08a6040518163ffffffff1660e01b8152600401600060405180830381600087803b15801561018657600080fd5b6101658061031e8339019056fe608060405234801561001057600080fd5b50600080546001600160a01b03191633179055610133806100326000396000f3fe6080604052348015600f57600080fd5b5060043610603c5760003560e01c80633fa4f2451460415780636057361d14605957806367e404ce146075575b600080fd5b60476097565b60408051918252519081900360200190f35b607360048036036020811015606d57600080fd5b5035609d565b005b607b60ef565b604080516001600160a01b039092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a1600255600180546001600160a01b03191633179055565b6001546001600160a01b03169056fea265627a7a72305820dc1ce4d08260105d146ec5efa5274950ee9e66f81ff18994d44a40fbd33e45c064736f6c634300050a0032a265627a7a72305820d71e5a225a48fdeb043aaba4264138353b3443a28658bacec7570e108659ad2864736f6c634300050a0032";

  public static final String FUNC_REMOTEDESTROY = "remoteDestroy";

  public static final String FUNC_DEPLOYREMOTE = "deployRemote";

  public static final String FUNC_DESTROY = "destroy";

  public static final String FUNC_READ = "read";

  public static final String FUNC_INCREMENT = "increment";

  public static final String FUNC_INCREMENTREMOTE = "incrementRemote";

  public static final Event NEWEVENTEMITTER_EVENT =
      new Event(
          "NewEventEmitter", Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));

  @Deprecated
  protected CrossContractReader(
      final String contractAddress,
      final Web3j web3j,
      final Credentials credentials,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected CrossContractReader(
      final String contractAddress,
      final Web3j web3j,
      final Credentials credentials,
      final ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected CrossContractReader(
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected CrossContractReader(
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> remoteDestroy(final String crossAddress) {
    final Function function =
        new Function(
            FUNC_REMOTEDESTROY,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, crossAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> deployRemote(final String crossAddress) {
    final Function function =
        new Function(
            FUNC_DEPLOYREMOTE,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, crossAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> deploy() {
    final Function function =
        new Function(FUNC_DEPLOY, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> destroy() {
    final Function function =
        new Function(
            FUNC_DESTROY, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<BigInteger> read(final String emitter_address) {
    final Function function =
        new Function(
            FUNC_READ,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, emitter_address)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteFunctionCall<TransactionReceipt> increment() {
    final Function function =
        new Function(
            FUNC_INCREMENT, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteFunctionCall<TransactionReceipt> incrementRemote(final String crossAddress) {
    final Function function =
        new Function(
            FUNC_INCREMENTREMOTE,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, crossAddress)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public List<NewEventEmitterEventResponse> getNewEventEmitterEvents(
      final TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(NEWEVENTEMITTER_EVENT, transactionReceipt);
    ArrayList<NewEventEmitterEventResponse> responses =
        new ArrayList<NewEventEmitterEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      NewEventEmitterEventResponse typedResponse = new NewEventEmitterEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.contractAddress = (String) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<NewEventEmitterEventResponse> newEventEmitterEventFlowable(
      final EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, NewEventEmitterEventResponse>() {
              @Override
              public NewEventEmitterEventResponse apply(final Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(NEWEVENTEMITTER_EVENT, log);
                NewEventEmitterEventResponse typedResponse = new NewEventEmitterEventResponse();
                typedResponse.log = log;
                typedResponse.contractAddress =
                    (String) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<NewEventEmitterEventResponse> newEventEmitterEventFlowable(
      final DefaultBlockParameter startBlock, final DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(NEWEVENTEMITTER_EVENT));
    return newEventEmitterEventFlowable(filter);
  }

  @Deprecated
  public static CrossContractReader load(
      final String contractAddress,
      final Web3j web3j,
      final Credentials credentials,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return new CrossContractReader(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static CrossContractReader load(
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return new CrossContractReader(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static CrossContractReader load(
      final String contractAddress,
      final Web3j web3j,
      final Credentials credentials,
      final ContractGasProvider contractGasProvider) {
    return new CrossContractReader(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static CrossContractReader load(
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final ContractGasProvider contractGasProvider) {
    return new CrossContractReader(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<CrossContractReader> deploy(
      final Web3j web3j,
      final Credentials credentials,
      final ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        CrossContractReader.class, web3j, credentials, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<CrossContractReader> deploy(
      final Web3j web3j,
      final Credentials credentials,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return deployRemoteCall(
        CrossContractReader.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  public static RemoteCall<CrossContractReader> deploy(
      final Web3j web3j,
      final TransactionManager transactionManager,
      final ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        CrossContractReader.class, web3j, transactionManager, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<CrossContractReader> deploy(
      final Web3j web3j,
      final TransactionManager transactionManager,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return deployRemoteCall(
        CrossContractReader.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
  }

  public static class NewEventEmitterEventResponse extends BaseEventResponse {
    public String contractAddress;
  }
}
