package org.hyperledger.besu.tests.web3j.generated.permissioning;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
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
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.5.6.
 */
@SuppressWarnings("rawtypes")
public class PrivacyGroup extends Contract {
    private static final String BINARY = "608060405234801561001057600080fd5b50610023336001600160e01b0361003b16565b50600080546001600160a01b031916331790556100c0565b6001600160a01b0381166000908152600260205260408120546100b75750600180548082018083557fb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf690910180546001600160a01b0319166001600160a01b0385169081179091556000908152600260205260409020556100bb565b5060005b919050565b6106b7806100cf6000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80635aa68ac0146100465780635b4ccc9d1461009e57806397107d6d14610155575b600080fd5b61004e61017d565b60408051602080825283518183015283519192839290830191858101910280838360005b8381101561008a578181015183820152602001610072565b505050509050019250505060405180910390f35b610141600480360360208110156100b457600080fd5b8101906020810181356401000000008111156100cf57600080fd5b8201836020820111156100e157600080fd5b8035906020019184602083028401116401000000008311171561010357600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600092019190915250929550610209945050505050565b604080519115158252519081900360200190f35b61017b6004803603602081101561016b57600080fd5b50356001600160a01b031661053d565b005b6003546060906001600160a01b0316331461019757600080fd5b6101a032610591565b6101a957600080fd5b60018054806020026020016040519081016040528092919081815260200182805480156101ff57602002820191906000526020600020905b81546001600160a01b031681526001909101906020018083116101e1575b5050505050905090565b6003546000906001600160a01b0316331461022357600080fd5b61022c32610591565b61023557600080fd5b600160005b83518110156105345783818151811061024f57fe5b60200260200101516001600160a01b0316336001600160a01b03161415610307577f349a5eb45b2e6e37ce4d053fbdb8474c5fb0eb89ce2d06f37158df3170fbf854600085838151811061029f57fe5b60200260200101516040518083151515158152602001826001600160a01b03166001600160a01b03168152602001806020018281038252602f815260200180610654602f9139604001935050505060405180910390a1818015610300575060005b915061052c565b61032384828151811061031657fe5b6020026020010151610591565b156103cd577f349a5eb45b2e6e37ce4d053fbdb8474c5fb0eb89ce2d06f37158df3170fbf854600085838151811061035757fe5b6020908102919091018101516040805193151584526001600160a01b03909116918301919091526060828201819052601b908301527f4163636f756e7420697320616c72656164792061204d656d62657200000000006080830152519081900360a00190a181801561030057506000915061052c565b60006103eb8583815181106103de57fe5b60200260200101516105ae565b905060608161042f576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250610449565b604051806060016040528060218152602001610633602191395b90507f349a5eb45b2e6e37ce4d053fbdb8474c5fb0eb89ce2d06f37158df3170fbf8548287858151811061047957fe5b6020026020010151836040518084151515158152602001836001600160a01b03166001600160a01b0316815260200180602001828103825283818151815260200191508051906020019080838360005b838110156104e15781810151838201526020016104c9565b50505050905090810190601f16801561050e5780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a18380156105275750815b935050505b60010161023a565b5090505b919050565b6000546001600160a01b0316331461055457600080fd5b6003546001600160a01b038281169116141561056f57600080fd5b600380546001600160a01b0319166001600160a01b0392909216919091179055565b6001600160a01b0316600090815260026020526040902054151590565b6001600160a01b03811660009081526002602052604081205461062a5750600180548082018083557fb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf690910180546001600160a01b0319166001600160a01b038516908117909155600090815260026020526040902055610538565b50600091905056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a7231582053cb2f736ef6023a4f8f02f5c8aac37f0cb7daee29c7dbf528c619cdb71b52a764736f6c634300050c0032";

    public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

    public static final String FUNC_GETPARTICIPANTS = "getParticipants";

    public static final String FUNC_SETPROXY = "setProxy";

    public static final Event MEMBERADDED_EVENT = new Event("MemberAdded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}, new TypeReference<Address>() {}, new TypeReference<Utf8String>() {}));
    ;

    @Deprecated
    protected PrivacyGroup(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected PrivacyGroup(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected PrivacyGroup(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected PrivacyGroup(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<MemberAddedEventResponse> getMemberAddedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(MEMBERADDED_EVENT, transactionReceipt);
        ArrayList<MemberAddedEventResponse> responses = new ArrayList<MemberAddedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            MemberAddedEventResponse typedResponse = new MemberAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.adminAdded = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.account = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.message = (String) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<MemberAddedEventResponse> memberAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, MemberAddedEventResponse>() {
            @Override
            public MemberAddedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(MEMBERADDED_EVENT, log);
                MemberAddedEventResponse typedResponse = new MemberAddedEventResponse();
                typedResponse.log = log;
                typedResponse.adminAdded = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.account = (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.message = (String) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<MemberAddedEventResponse> memberAddedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(MEMBERADDED_EVENT));
        return memberAddedEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> addParticipants(List<String> accounts) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ADDPARTICIPANTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<List> getParticipants() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETPARTICIPANTS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Address>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<TransactionReceipt> setProxy(String proxy) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_SETPROXY, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, proxy)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static PrivacyGroup load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new PrivacyGroup(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static PrivacyGroup load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new PrivacyGroup(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static PrivacyGroup load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new PrivacyGroup(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static PrivacyGroup load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new PrivacyGroup(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<PrivacyGroup> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(PrivacyGroup.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<PrivacyGroup> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(PrivacyGroup.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<PrivacyGroup> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(PrivacyGroup.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<PrivacyGroup> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(PrivacyGroup.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class MemberAddedEventResponse extends BaseEventResponse {
        public Boolean adminAdded;

        public String account;

        public String message;
    }
}
