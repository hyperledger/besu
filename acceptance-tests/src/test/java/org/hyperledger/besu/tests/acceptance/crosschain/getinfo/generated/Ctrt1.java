/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.CrosschainContext;
import org.web3j.tx.CrosschainContract;
import org.web3j.tx.CrosschainTransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the org.web3j.codegen.CrosschainSolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/PegaSysEng/sidechains-web3j/tree/master/besucodegen">codegen module</a>
 * to update.
 *
 * <p>Generated with web3j version 4.6.0-SNAPSHOT.
 */
@SuppressWarnings("rawtypes")
public class Ctrt1 extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b506100226001600160e01b0361004116565b6007805463ffffffff191663ffffffff92909216919091179055610095565b6000600461004d610077565b60008152610059610077565b60048082858560fa600019fa61006e57600080fd5b50519392505050565b60405180602001604052806001906020820280388339509192915050565b610639806100a46000396000f3fe608060405234801561001057600080fd5b50600436106100cf5760003560e01c80637bf8febd1161008c578063bc7fb65111610066578063bc7fb6511461017a578063d34675aa14610182578063e6fdb7ea146101a8578063f41c38a1146101b0576100cf565b80637bf8febd146101625780639f8d7a091461016a578063a7ded53514610172576100cf565b806301605925146100d45780630a56e1a0146100ee5780633e2ae2f8146101125780633fba51621461011a57806346c9da561461013957806379fe365f1461015a575b600080fd5b6100dc6101b8565b60408051918252519081900360200190f35b6100f66101be565b604080516001600160a01b039092168252519081900360200190f35b6100dc6101cd565b6101376004803603602081101561013057600080fd5b50356101d3565b005b6101416101d8565b6040805163ffffffff9092168252519081900360200190f35b6100dc6101ec565b6101376101f2565b6100dc61032c565b6100dc610332565b6100dc610338565b6101376004803603602081101561019857600080fd5b50356001600160a01b031661033e565b6100f6610360565b61014161036f565b60065481565b6009546001600160a01b031681565b60055481565b600055565b600754640100000000900463ffffffff1681565b60035481565b6000546001546040805160048152602481019091526020810180516001600160e01b031663055443d760e41b17905261023592916001600160a01b03169061037b565b6000546001546040805160048152602481019091526020810180516001600160e01b031663484f579b60e01b17905261027892916001600160a01b031690610449565b600855610283610525565b60025561028e610536565b600355610299610542565b600980546001600160a01b0319166001600160a01b03929092169190911790556102c161054e565b600760046101000a81548163ffffffff021916908363ffffffff1602179055506102e9610584565b6004556102f4610590565b600a80546001600160a01b0319166001600160a01b039290921691909117905561031c61059c565b6005556103276105a8565b600655565b60085481565b60025481565b60045481565b600180546001600160a01b0319166001600160a01b0392909216919091179055565b600a546001600160a01b031681565b60075463ffffffff1681565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156103de5781810151838201526020016103c6565b50505050905090810190601f16801561040b5780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161044257600080fd5b5050505050565b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156104ae578181015183820152602001610496565b50505050905090810190601f1680156104db5780820380516001836020036101000a031916815260200191505b5094505050505060405160208183030381529060405290506000600482510190506105046105e6565b602080828486600b600019fa61051957600080fd5b50519695505050505050565b600061053160016105b0565b905090565b600061053160026105b0565b600061053160036105b0565b6000600461055a6105e6565b600081526105666105e6565b60048082858560fa600019fa61057b57600080fd5b50519392505050565b600061053160076105b0565b600061053160066105b0565b600061053160056105b0565b600061053160045b600060206105bc6105e6565b8381526105c76105e6565b6020808285856078600019fa6105dc57600080fd5b5051949350505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a723158200668652b15e0267fbe7af6e4490b98983e5ea4eb69d6f48f2aa4e11542726ced64736f6c634300050c0032";

  public static final String FUNC_CALLCTRT2 = "callCtrt2";

  public static final String FUNC_CONSTXTYPE = "consTxType";

  public static final String FUNC_COORDCHAINID = "coordChainId";

  public static final String FUNC_COORDCTRTADDR = "coordCtrtAddr";

  public static final String FUNC_FROMADDR = "fromAddr";

  public static final String FUNC_FROMCHAINID = "fromChainId";

  public static final String FUNC_MYCHAINID = "myChainId";

  public static final String FUNC_MYTXID = "myTxId";

  public static final String FUNC_MYTXTYPE = "myTxType";

  public static final String FUNC_ORIGCHAINID = "origChainId";

  public static final String FUNC_SETCTRT2 = "setCtrt2";

  public static final String FUNC_SETCTRT2CHAINID = "setCtrt2ChainId";

  public static final String FUNC_VIEWTXTYPE = "viewTxType";

  @Deprecated
  protected Ctrt1(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected Ctrt1(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt2() {
    final Function function =
        new Function(
            FUNC_CALLCTRT2, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] callCtrt2_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CALLCTRT2, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt2_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_CALLCTRT2, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> consTxType() {
    final Function function =
        new Function(
            FUNC_CONSTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] consTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CONSTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> coordChainId() {
    final Function function =
        new Function(
            FUNC_COORDCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] coordChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_COORDCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<String> coordCtrtAddr() {
    final Function function =
        new Function(
            FUNC_COORDCTRTADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] coordCtrtAddr_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_COORDCTRTADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<String> fromAddr() {
    final Function function =
        new Function(
            FUNC_FROMADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] fromAddr_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_FROMADDR,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> fromChainId() {
    final Function function =
        new Function(
            FUNC_FROMCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] fromChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_FROMCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> myChainId() {
    final Function function =
        new Function(
            FUNC_MYCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_MYCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> myTxId() {
    final Function function =
        new Function(
            FUNC_MYTXID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myTxId_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_MYTXID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> myTxType() {
    final Function function =
        new Function(
            FUNC_MYTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] myTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_MYTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint32>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> origChainId() {
    final Function function =
        new Function(
            FUNC_ORIGCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] origChainId_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_ORIGCHAINID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2(String _ctrt2Addr) {
    final Function function =
        new Function(
            FUNC_SETCTRT2,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt2Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt2_AsSignedCrosschainSubordinateTransaction(
      String _ctrt2Addr, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT2,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt2Addr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2_AsCrosschainTransaction(
      String _ctrt2Addr, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT2,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt2Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2ChainId(BigInteger _ctrt2ChainId) {
    final Function function =
        new Function(
            FUNC_SETCTRT2CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt2ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt2ChainId_AsSignedCrosschainSubordinateTransaction(
      BigInteger _ctrt2ChainId, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT2CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt2ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt2ChainId_AsCrosschainTransaction(
      BigInteger _ctrt2ChainId, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT2CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt2ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> viewTxType() {
    final Function function =
        new Function(
            FUNC_VIEWTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] viewTxType_AsSignedCrosschainSubordinateView(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_VIEWTXTYPE,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  @Deprecated
  public static Ctrt1 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new Ctrt1(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static Ctrt1 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new Ctrt1(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt1> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt1.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
