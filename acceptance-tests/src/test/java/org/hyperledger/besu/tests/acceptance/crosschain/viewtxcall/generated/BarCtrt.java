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
package org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
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
public class BarCtrt extends CrosschainContract {
<<<<<<< Updated upstream
  private static final String BINARY =
      "608060405234801561001057600080fd5b5060006002556103ef806100256000396000f3fe608060405234801561001057600080fd5b50600436106100625760003560e01c806360d104281461006757806384a13fba14610095578063890eba68146100b95780639de16f74146100d3578063fc344e99146100db578063febb0f7e146100e3575b600080fd5b6100936004803603604081101561007d57600080fd5b50803590602001356001600160a01b03166100eb565b005b61009d610112565b604080516001600160a01b039092168252519081900360200190f35b6100c1610121565b60408051918252519081900360200190f35b610093610127565b61009361016e565b6100936101b3565b600091909155600180546001600160a01b0319166001600160a01b03909216919091179055565b6001546001600160a01b031681565b60025481565b6000546001546040805160048152602481019091526020810180516001600160e01b031662230c3f60e61b17905261016992916001600160a01b0316906101f2565b600255565b6000546001546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b1790526101b192916001600160a01b0316906102ce565b565b6000546001546040805160048152602481019091526020810180516001600160e01b03166318530aaf60e31b17905261016992916001600160a01b0316905b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561025757818101518382015260200161023f565b50505050905090810190601f1680156102845780820380516001836020036101000a031916815260200191505b5094505050505060405160208183030381529060405290506000600482510190506102ad61039c565b602080828486600b600019fa6102c257600080fd5b50519695505050505050565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b83811015610331578181015183820152602001610319565b50505050905090810190601f16801561035e5780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161039557600080fd5b5050505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a723158202e5c58835b144139db455c5dc9c86f9e75226ad73fd69771ee8f38ec811fbd0364736f6c634300050c0032";

  public static final String FUNC_BAR = "bar";

  public static final String FUNC_BARUPDATESTATE = "barUpdateState";

  public static final String FUNC_FLAG = "flag";

  public static final String FUNC_FOOCTRT = "fooCtrt";

  public static final String FUNC_PUREBAR = "pureBar";

  public static final String FUNC_SETPROPERTIES = "setProperties";

  @Deprecated
  protected BarCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  protected BarCtrt(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public RemoteFunctionCall<TransactionReceipt> bar() {
    final Function function =
        new Function(FUNC_BAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] bar_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(FUNC_BAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> bar_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(FUNC_BAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barUpdateState() {
    final Function function =
        new Function(
            FUNC_BARUPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] barUpdateState_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_BARUPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> barUpdateState_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_BARUPDATESTATE, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<BigInteger> flag() {
    final Function function =
        new Function(
            FUNC_FLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public byte[] flag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FLAG,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<String> fooCtrt() {
    final Function function =
        new Function(
            FUNC_FOOCTRT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public byte[] fooCtrt_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_FOOCTRT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return createSignedSubordinateView(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> pureBar() {
    final Function function =
        new Function(
            FUNC_PUREBAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] pureBar_AsSignedCrosschainSubordinateTransaction(
      final CrosschainContext crosschainContext) throws IOException {
    final Function function =
        new Function(
            FUNC_PUREBAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> pureBar_AsCrosschainTransaction(
      final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_PUREBAR, Arrays.<Type>asList(), Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setProperties(
      BigInteger _calleeId, String _fooCtrtAaddr) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_calleeId),
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public byte[] setProperties_AsSignedCrosschainSubordinateTransaction(
      BigInteger _calleeId, String _fooCtrtAaddr, final CrosschainContext crosschainContext)
      throws IOException {
    final Function function =
        new Function(
            FUNC_SETPROPERTIES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_calleeId),
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)),
            Collections.<TypeReference<?>>emptyList());
    return createSignedSubordinateTransaction(function, crosschainContext);
  }

  public RemoteFunctionCall<TransactionReceipt> setProperties_AsCrosschainTransaction(
      BigInteger _calleeId, String _fooCtrtAaddr, final CrosschainContext crosschainContext) {
    final Function function =
        new Function(
            FUNC_SETPROPERTIES,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.generated.Uint256(_calleeId),
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallCrosschainTransaction(function, crosschainContext);
  }

  @Deprecated
  public static BarCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new BarCtrt(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
  }

  public static BarCtrt load(
      String contractAddress,
      Besu besu,
      CrosschainTransactionManager crosschainTransactionManager,
      ContractGasProvider contractGasProvider) {
    return new BarCtrt(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
  }

  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        BarCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    CrosschainContext crosschainContext = null;
    return deployLockableContractRemoteCall(
        BarCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }

  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        BarCtrt.class,
        besu,
        transactionManager,
        contractGasProvider,
        BINARY,
        "",
        crosschainContext);
  }

  @Deprecated
  public static RemoteCall<BarCtrt> deployLockable(
      Besu besu,
      CrosschainTransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      final CrosschainContext crosschainContext) {
    return deployLockableContractRemoteCall(
        BarCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
  }
=======
    private static final String BINARY = "608060405234801561001057600080fd5b5060006002556104db806100256000396000f3fe608060405234801561001057600080fd5b506004361061009e5760003560e01c80639de16f74116100665780639de16f741461011f578063b15b10da14610127578063be7003d51461012f578063fc344e9914610137578063febb0f7e1461013f5761009e565b8063484f579b146100a357806360d10428146100bd57806384a13fba146100eb578063890eba681461010f578063966aaf8014610117575b600080fd5b6100ab610147565b60408051918252519081900360200190f35b6100e9600480360360408110156100d357600080fd5b50803590602001356001600160a01b031661014c565b005b6100f3610173565b604080516001600160a01b039092168252519081900360200190f35b6100ab610182565b6100e9610188565b6100e96101d0565b6100ab610212565b6100e9610217565b6100e961025a565b6100e961029f565b600190565b600091909155600180546001600160a01b0319166001600160a01b03909216919091179055565b6001546001600160a01b031681565b60025481565b6000546001546040805160048152602481019091526020810180516001600160e01b0316636880d21b60e01b1790526101cb92916001600160a01b0316906102de565b600255565b6000546001546040805160048152602481019091526020810180516001600160e01b031662230c3f60e61b1790526101cb92916001600160a01b0316906102de565b600290565b6000546001546040805160048152602481019091526020810180516001600160e01b0316637c5ab21360e01b1790526101cb92916001600160a01b0316906102de565b6000546001546040805160048152602481019091526020810180516001600160e01b0316631d8557d760e01b17905261029d92916001600160a01b0316906103ba565b565b6000546001546040805160048152602481019091526020810180516001600160e01b03166318530aaf60e31b1790526101cb92916001600160a01b0316905b6000606084848460405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561034357818101518382015260200161032b565b50505050905090810190601f1680156103705780820380516001836020036101000a031916815260200191505b509450505050506040516020818303038152906040529050600060048251019050610399610488565b602080828486600b600019fa6103ae57600080fd5b50519695505050505050565b606083838360405160200180848152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b8381101561041d578181015183820152602001610405565b50505050905090810190601f16801561044a5780820380516001836020036101000a031916815260200191505b50945050505050604051602081830303815290604052905060006004825101905060008082846000600a600019f161048157600080fd5b5050505050565b6040518060200160405280600190602082028038833950919291505056fea265627a7a72315820684000f37fd8b8f7b330f02300284820ab1ffe3b70ff4477ed8d5a6ec50a528c64736f6c634300050c0032";

    public static final String FUNC_BAR = "bar";

    public static final String FUNC_BARUPDATESTATE = "barUpdateState";

    public static final String FUNC_BARVP = "barvp";

    public static final String FUNC_BARVV = "barvv";

    public static final String FUNC_FLAG = "flag";

    public static final String FUNC_FOOCTRT = "fooCtrt";

    public static final String FUNC_PUREBAR = "pureBar";

    public static final String FUNC_PUREFN = "purefn";

    public static final String FUNC_SETPROPERTIES = "setProperties";

    public static final String FUNC_VIEWFN = "viewfn";

    @Deprecated
    protected BarCtrt(String contractAddress, Besu besu, CrosschainTransactionManager crosschainTransactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
    }

    protected BarCtrt(String contractAddress, Besu besu, CrosschainTransactionManager crosschainTransactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, besu, crosschainTransactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<TransactionReceipt> bar() {
        final Function function = new Function(
                FUNC_BAR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public byte[] bar_AsSignedCrosschainSubordinateTransaction(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(
                FUNC_BAR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return createSignedSubordinateTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> bar_AsCrosschainTransaction(final CrosschainContext crosschainContext) {
        final Function function = new Function(
                FUNC_BAR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallCrosschainTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> barUpdateState() {
        final Function function = new Function(
                FUNC_BARUPDATESTATE, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public byte[] barUpdateState_AsSignedCrosschainSubordinateTransaction(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(
                FUNC_BARUPDATESTATE, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return createSignedSubordinateTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> barUpdateState_AsCrosschainTransaction(final CrosschainContext crosschainContext) {
        final Function function = new Function(
                FUNC_BARUPDATESTATE, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallCrosschainTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> barvp() {
        final Function function = new Function(
                FUNC_BARVP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public byte[] barvp_AsSignedCrosschainSubordinateTransaction(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(
                FUNC_BARVP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return createSignedSubordinateTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> barvp_AsCrosschainTransaction(final CrosschainContext crosschainContext) {
        final Function function = new Function(
                FUNC_BARVP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallCrosschainTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> barvv() {
        final Function function = new Function(
                FUNC_BARVV, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public byte[] barvv_AsSignedCrosschainSubordinateTransaction(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(
                FUNC_BARVV, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return createSignedSubordinateTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> barvv_AsCrosschainTransaction(final CrosschainContext crosschainContext) {
        final Function function = new Function(
                FUNC_BARVV, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallCrosschainTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<BigInteger> flag() {
        final Function function = new Function(FUNC_FLAG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public byte[] flag_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(FUNC_FLAG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return createSignedSubordinateView(function, crosschainContext);
    }

    public RemoteFunctionCall<String> fooCtrt() {
        final Function function = new Function(FUNC_FOOCTRT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public byte[] fooCtrt_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(FUNC_FOOCTRT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return createSignedSubordinateView(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> pureBar() {
        final Function function = new Function(
                FUNC_PUREBAR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public byte[] pureBar_AsSignedCrosschainSubordinateTransaction(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(
                FUNC_PUREBAR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return createSignedSubordinateTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> pureBar_AsCrosschainTransaction(final CrosschainContext crosschainContext) {
        final Function function = new Function(
                FUNC_PUREBAR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallCrosschainTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<BigInteger> purefn() {
        final Function function = new Function(FUNC_PUREFN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public byte[] purefn_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(FUNC_PUREFN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return createSignedSubordinateView(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> setProperties(BigInteger _fooChainId, String _fooCtrtAaddr) {
        final Function function = new Function(
                FUNC_SETPROPERTIES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_fooChainId), 
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public byte[] setProperties_AsSignedCrosschainSubordinateTransaction(BigInteger _fooChainId, String _fooCtrtAaddr, final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(
                FUNC_SETPROPERTIES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_fooChainId), 
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)), 
                Collections.<TypeReference<?>>emptyList());
        return createSignedSubordinateTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<TransactionReceipt> setProperties_AsCrosschainTransaction(BigInteger _fooChainId, String _fooCtrtAaddr, final CrosschainContext crosschainContext) {
        final Function function = new Function(
                FUNC_SETPROPERTIES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_fooChainId), 
                new org.web3j.abi.datatypes.Address(160, _fooCtrtAaddr)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallCrosschainTransaction(function, crosschainContext);
    }

    public RemoteFunctionCall<BigInteger> viewfn() {
        final Function function = new Function(FUNC_VIEWFN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public byte[] viewfn_AsSignedCrosschainSubordinateView(final CrosschainContext crosschainContext) throws IOException {
        final Function function = new Function(FUNC_VIEWFN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return createSignedSubordinateView(function, crosschainContext);
    }

    @Deprecated
    public static BarCtrt load(String contractAddress, Besu besu, CrosschainTransactionManager crosschainTransactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new BarCtrt(contractAddress, besu, crosschainTransactionManager, gasPrice, gasLimit);
    }

    public static BarCtrt load(String contractAddress, Besu besu, CrosschainTransactionManager crosschainTransactionManager, ContractGasProvider contractGasProvider) {
        return new BarCtrt(contractAddress, besu, crosschainTransactionManager, contractGasProvider);
    }

    public static RemoteCall<BarCtrt> deployLockable(Besu besu, CrosschainTransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        CrosschainContext crosschainContext = null;
        return deployLockableContractRemoteCall(BarCtrt.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
    }

    @Deprecated
    public static RemoteCall<BarCtrt> deployLockable(Besu besu, CrosschainTransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        CrosschainContext crosschainContext = null;
        return deployLockableContractRemoteCall(BarCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
    }

    public static RemoteCall<BarCtrt> deployLockable(Besu besu, CrosschainTransactionManager transactionManager, ContractGasProvider contractGasProvider, final CrosschainContext crosschainContext) {
        return deployLockableContractRemoteCall(BarCtrt.class, besu, transactionManager, contractGasProvider, BINARY, "", crosschainContext);
    }

    @Deprecated
    public static RemoteCall<BarCtrt> deployLockable(Besu besu, CrosschainTransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, final CrosschainContext crosschainContext) {
        return deployLockableContractRemoteCall(BarCtrt.class, besu, transactionManager, gasPrice, gasLimit, BINARY, "", crosschainContext);
    }
>>>>>>> Stashed changes
}
