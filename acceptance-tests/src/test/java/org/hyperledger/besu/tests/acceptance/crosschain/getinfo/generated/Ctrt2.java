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
public class Ctrt2 extends CrosschainContract {
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060006002556100276001600160e01b0361004c16565b600560046101000a81548163ffffffff021916908363ffffffff1602179055506100a0565b60006004610058610082565b60008152610064610082565b60048082858560fa600019fa61007957600080fd5b50519392505050565b60405180602001604052806001906020820280388339509192915050565b610520806100af6000396000f3fe608060405234801561001057600080fd5b50600436106100cf5760003560e01c806379fe365f1161008c578063e669439b11610066578063e669439b14610165578063e6fdb7ea1461018b578063f41c38a114610193578063f4ac46931461019b576100cf565b806379fe365f1461014d578063a7ded53514610155578063cd568d1f1461015d576100cf565b806301605925146100d45780630a56e1a0146100ee5780633e2ae2f81461011257806346c9da561461011a578063484f579b1461013b57806355443d7014610143575b600080fd5b6100dc6101b8565b60408051918252519081900360200190f35b6100f66101be565b604080516001600160a01b039092168252519081900360200190f35b6100dc6101cd565b6101226101d3565b6040805163ffffffff9092168252519081900360200190f35b6100dc6101df565b61014b6101f4565b005b6100dc6102e2565b6100dc6102e8565b6100dc6102ee565b61014b6004803603602081101561017b57600080fd5b50356001600160a01b03166102f4565b6100f6610316565b610122610325565b61014b600480360360208110156101b157600080fd5b5035610339565b60075481565b6009546001600160a01b031681565b60045481565b60055463ffffffff1681565b60006101e961033e565b63ffffffff16905090565b6000546001546040805160048152602481019091526020810180516001600160e01b0316631494356b60e31b17905261023792916001600160a01b031690610374565b61023f610442565b60035561024a61033e565b6005805463ffffffff191663ffffffff9290921691909117905561026c610453565b60065561027761045f565b60075561028261046b565b60045561028d610477565b600855610298610483565b600980546001600160a01b0319166001600160a01b03929092169190911790556102c061048f565b600a80546001600160a01b0319166001600160a01b0392909216919091179055565b60065481565b60035481565b60085481565b600180546001600160a01b0319166001600160a01b0392909216919091179055565b600a546001600160a01b031681565b600554640100000000900463ffffffff1681565b600055565b6000600461034a6104cd565b600081526103566104cd565b60048082858560fa600019fa61036b57600080fd5b50519392505050565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156103d75781810151838201526020016103bf565b50505050905090810190601f1680156104045780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161043b57600080fd5b5050505050565b600061044e6001610497565b905090565b600061044e6002610497565b600061044e6004610497565b600061044e6005610497565b600061044e6007610497565b600061044e6003610497565b600061044e60065b600060206104a36104cd565b8381526104ae6104cd565b6020808285856078600019fa6104c357600080fd5b5051949350505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820201b3e8a3f1ad6566c45165eb59a18a46135f32f5aa7b1e577bc7e63cbdca5bd64736f6c634300050c0032";

  public static final String FUNC_CALLCTRT3 = "callCtrt3";

  public static final String FUNC_CONSTXTYPE = "consTxType";

  public static final String FUNC_COORDCHAINID = "coordChainId";

  public static final String FUNC_COORDCTRTADDR = "coordCtrtAddr";

  public static final String FUNC_FROMADDR = "fromAddr";

  public static final String FUNC_FROMCHAINID = "fromChainId";

  public static final String FUNC_MYCHAINID = "myChainId";

  public static final String FUNC_MYTXTYPE = "myTxType";

  public static final String FUNC_ORIGCHAINID = "origChainId";

  public static final String FUNC_SETCTRT3 = "setCtrt3";

  public static final String FUNC_SETCTRT3CHAINID = "setCtrt3ChainId";

  public static final String FUNC_TXID = "txId";

  public static final String FUNC_VIEWFN = "viewfn";

  @Deprecated
  protected Ctrt2(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected Ctrt2(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt3() {
    final Function function =
        new Function(
            FUNC_CALLCTRT3, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] callCtrt3_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_CALLCTRT3, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> callCtrt3_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_CALLCTRT3, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
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

  public RemoteFunctionCall<TransactionReceipt> setCtrt3(String _ctrt3Addr) {
    final Function function =
        new Function(
            FUNC_SETCTRT3,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt3Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt3_AsSignedCrosschainSubordinateTransaction(
      String _ctrt3Addr, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT3,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt3Addr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3_AsCrosschainTransaction(
      String _ctrt3Addr, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT3,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ctrt3Addr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3ChainId(BigInteger _ctrt3ChainId) {
    final Function function =
        new Function(
            FUNC_SETCTRT3CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt3ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setCtrt3ChainId_AsSignedCrosschainSubordinateTransaction(
      BigInteger _ctrt3ChainId, final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_SETCTRT3CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt3ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setCtrt3ChainId_AsCrosschainTransaction(
      BigInteger _ctrt3ChainId, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETCTRT3CHAINID,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_ctrt3ChainId)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> txId() {
    final Function function =
        new Function(
            FUNC_TXID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] txId_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_TXID,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> viewfn() {
    final Function function =
        new Function(
            FUNC_VIEWFN,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] viewfn_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_VIEWFN,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  @Deprecated
  public static Ctrt2 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new Ctrt2(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static Ctrt2 load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new Ctrt2(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
  }

  @Deprecated
  public static RemoteCall<Ctrt2> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        Ctrt2.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
}
