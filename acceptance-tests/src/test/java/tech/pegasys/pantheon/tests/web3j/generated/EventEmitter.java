/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.web3j.generated;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import rx.Observable;
import rx.functions.Func1;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 3.5.0.
 */
@SuppressWarnings("rawtypes")
public class EventEmitter extends Contract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060008054600160a060020a03191633179055610187806100326000396000f3006080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce1461009c575b600080fd5b34801561006757600080fd5b506100706100da565b60408051918252519081900360200190f35b34801561008e57600080fd5b5061009a6004356100e0565b005b3480156100a857600080fd5b506100b161013f565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff16905600a165627a7a72305820f958aea7922a9538be4c34980ad3171806aad2d3fedb62682cef2ca4e1f1f3120029";

  public static final String FUNC_VALUE = "value";

  public static final String FUNC_STORE = "store";

  public static final String FUNC_SENDER = "sender";

  public static final Event STORED_EVENT =
      new Event(
          "stored",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));

  protected EventEmitter(
      final String contractAddress,
      final Web3j web3j,
      final Credentials credentials,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected EventEmitter(
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public RemoteCall<BigInteger> value() {
    final Function function =
        new Function(
            FUNC_VALUE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteCall<TransactionReceipt> store(final BigInteger _amount) {
    final Function function =
        new Function(
            FUNC_STORE,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_amount)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<String> sender() {
    final Function function =
        new Function(
            FUNC_SENDER,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public static RemoteCall<EventEmitter> deploy(
      final Web3j web3j,
      final Credentials credentials,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return deployRemoteCall(EventEmitter.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  public static RemoteCall<EventEmitter> deploy(
      final Web3j web3j,
      final TransactionManager transactionManager,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return deployRemoteCall(
        EventEmitter.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
  }

  public List<StoredEventResponse> getStoredEvents(final TransactionReceipt transactionReceipt) {
    final List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(STORED_EVENT, transactionReceipt);
    final ArrayList<StoredEventResponse> responses =
        new ArrayList<StoredEventResponse>(valueList.size());
    for (final Contract.EventValuesWithLog eventValues : valueList) {
      final StoredEventResponse typedResponse = new StoredEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._to = (String) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Observable<StoredEventResponse> storedEventObservable(final EthFilter filter) {
    return web3j
        .ethLogObservable(filter)
        .map(
            new Func1<Log, StoredEventResponse>() {
              @Override
              public StoredEventResponse call(final Log log) {
                final Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(STORED_EVENT, log);
                final StoredEventResponse typedResponse = new StoredEventResponse();
                typedResponse.log = log;
                typedResponse._to = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._amount =
                    (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
              }
            });
  }

  public Observable<StoredEventResponse> storedEventObservable(
      final DefaultBlockParameter startBlock, final DefaultBlockParameter endBlock) {
    final EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(STORED_EVENT));
    return storedEventObservable(filter);
  }

  public static EventEmitter load(
      final String contractAddress,
      final Web3j web3j,
      final Credentials credentials,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return new EventEmitter(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  public static EventEmitter load(
      final String contractAddress,
      final Web3j web3j,
      final TransactionManager transactionManager,
      final BigInteger gasPrice,
      final BigInteger gasLimit) {
    return new EventEmitter(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static class StoredEventResponse {
    public Log log;

    public String _to;

    public BigInteger _amount;
  }
}
