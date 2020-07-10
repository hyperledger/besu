package org.hyperledger.besu.privacy.contracts.generated;

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
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.5.16.
 */
@SuppressWarnings("rawtypes")
public class DefaultOnChainPrivacyGroupManagementContract extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b50610aab806100206000396000f3fe608060405234801561001057600080fd5b50600436106100885760003560e01c8063a69df4b51161005b578063a69df4b5146101a0578063f744b089146101aa578063f83d08ba14610284578063f942ebd61461028e57610088565b80630b0235be1461008d5780630d8e6e2c1461011057806361544c911461012e57806378b903371461017e575b600080fd5b6100b9600480360360208110156100a357600080fd5b81019080803590602001909291905050506102d4565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156100fc5780820151818401526020810190506100e1565b505050509050019250505060405180910390f35b610118610340565b6040518082815260200191505060405180910390f35b6101646004803603604081101561014457600080fd5b81019080803590602001909291908035906020019092919050505061034a565b604051808215151515815260200191505060405180910390f35b6101866103c1565b604051808215151515815260200191505060405180910390f35b6101a86103d7565b005b61026a600480360360408110156101c057600080fd5b8101908080359060200190929190803590602001906401000000008111156101e757600080fd5b8201836020820111156101f957600080fd5b8035906020019184602083028401116401000000008311171561021b57600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f82011690508083019250505050505050919291929050505061040c565b604051808215151515815260200191505060405180910390f35b61028c61048d565b005b6102ba600480360360208110156102a457600080fd5b81019080803590602001909291905050506104c1565b604051808215151515815260200191505060405180910390f35b60606102df826104de565b6102e857600080fd5b600280548060200260200160405190810160405280929190818152602001828054801561033457602002820191906000526020600020905b815481526020019060010190808311610320575b50505050509050919050565b6000600154905090565b6000610355836104de565b61035e57600080fd5b6000610369836104fe565b90506103736105e1565b507fbbeb554e7225026991ae908172deed16661afb44ee3ff3d11b6e4aeec79066bf818460405180831515151581526020018281526020019250505060405180910390a18091505092915050565b60008060009054906101000a900460ff16905090565b6000809054906101000a900460ff16156103f057600080fd5b60016000806101000a81548160ff021916908315150217905550565b60008060009054906101000a900460ff161561042757600080fd5b600060028054905014156104405761043e83610681565b505b610449836104de565b61045257600080fd5b600061045e84846106f3565b905060016000806101000a81548160ff0219169083151502179055506104826105e1565b508091505092915050565b6000809054906101000a900460ff166104a557600080fd5b60008060006101000a81548160ff021916908315150217905550565b60006104cc826104de565b6104d557600080fd5b60019050919050565b600080600360008481526020019081526020016000205414159050919050565b6000806003600084815260200190815260200160002054905060008111801561052c57506002805490508111155b156105d657600280549050811461059a57600060026001600280549050038154811061055457fe5b90600052602060002001549050806002600184038154811061057257fe5b9060005260206000200181905550816003600083815260200190815260200160002081905550505b60016002818180549050039150816105b291906109d5565b506000600360008581526020019081526020016000208190555060019150506105dc565b60009150505b919050565b60006001430340416002604051602001808481526020018373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1660601b8152601401828054801561065b57602002820191906000526020600020905b815481526020019060010190808311610647575b505093505050506040516020818303038152906040528051906020012060018190555090565b600080600360008481526020019081526020016000205414156106e957600282908060018154018082558091505090600182039060005260206000200160009091929091909150556003600084815260200190815260200160002081905550600190506106ee565b600090505b919050565b6000806001905060008090505b83518110156109ca5783818151811061071557fe5b60200260200101518514156107a9577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf213600085838151811061075357fe5b60200260200101516040518083151515158152602001828152602001806020018281038252602f815260200180610a48602f9139604001935050505060405180910390a18180156107a2575060005b91506109bd565b6107c58482815181106107b857fe5b60200260200101516104de565b1561086c577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf21360008583815181106107f957fe5b60200260200101516040518083151515158152602001828152602001806020018281038252601b8152602001807f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250602001935050505060405180910390a1818015610865575060005b91506109bc565b600061088a85838151811061087d57fe5b6020026020010151610681565b90506060816108ce576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d62657200000000008152506108e8565b604051806060016040528060218152602001610a27602191395b90507fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf2138287858151811061091857fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b83811015610971578082015181840152602081019050610956565b50505050905090810190601f16801561099e5780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a18380156109b75750815b935050505b5b8080600101915050610700565b508091505092915050565b8154818355818111156109fc578183600052602060002091820191016109fb9190610a01565b5b505050565b610a2391905b80821115610a1f576000816000905550600101610a07565b5090565b9056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c79416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a7231582034b150c32b13938cbb4229e05474dc96c001f3ea93702c3df800a9b0fab58bae64736f6c63430005110032";

    public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

    public static final String FUNC_CANEXECUTE = "canExecute";

    public static final String FUNC_CANUPGRADE = "canUpgrade";

    public static final String FUNC_GETPARTICIPANTS = "getParticipants";

    public static final String FUNC_GETVERSION = "getVersion";

    public static final String FUNC_LOCK = "lock";

    public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

    public static final String FUNC_UNLOCK = "unlock";

    public static final Event PARTICIPANTADDED_EVENT = new Event("ParticipantAdded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}));
    ;

    public static final Event PARTICIPANTREMOVED_EVENT = new Event("ParticipantRemoved", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}, new TypeReference<Bytes32>() {}));
    ;

    @Deprecated
    protected DefaultOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected DefaultOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected DefaultOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected DefaultOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<ParticipantAddedEventResponse> getParticipantAddedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(PARTICIPANTADDED_EVENT, transactionReceipt);
        ArrayList<ParticipantAddedEventResponse> responses = new ArrayList<ParticipantAddedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ParticipantAddedEventResponse typedResponse = new ParticipantAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.success = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.account = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.message = (String) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<ParticipantAddedEventResponse> participantAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, ParticipantAddedEventResponse>() {
            @Override
            public ParticipantAddedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(PARTICIPANTADDED_EVENT, log);
                ParticipantAddedEventResponse typedResponse = new ParticipantAddedEventResponse();
                typedResponse.log = log;
                typedResponse.success = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.account = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.message = (String) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<ParticipantAddedEventResponse> participantAddedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PARTICIPANTADDED_EVENT));
        return participantAddedEventFlowable(filter);
    }

    public List<ParticipantRemovedEventResponse> getParticipantRemovedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(PARTICIPANTREMOVED_EVENT, transactionReceipt);
        ArrayList<ParticipantRemovedEventResponse> responses = new ArrayList<ParticipantRemovedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ParticipantRemovedEventResponse typedResponse = new ParticipantRemovedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.success = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.account = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<ParticipantRemovedEventResponse> participantRemovedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, ParticipantRemovedEventResponse>() {
            @Override
            public ParticipantRemovedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(PARTICIPANTREMOVED_EVENT, log);
                ParticipantRemovedEventResponse typedResponse = new ParticipantRemovedEventResponse();
                typedResponse.log = log;
                typedResponse.success = (Boolean) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.account = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<ParticipantRemovedEventResponse> participantRemovedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PARTICIPANTREMOVED_EVENT));
        return participantRemovedEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> addParticipants(byte[] _enclaveKey, List<byte[]> _accounts) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ADDPARTICIPANTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                        org.web3j.abi.datatypes.generated.Bytes32.class,
                        org.web3j.abi.Utils.typeMap(_accounts, org.web3j.abi.datatypes.generated.Bytes32.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> canExecute() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_CANEXECUTE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Boolean> canUpgrade(byte[] _enclaveKey) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_CANUPGRADE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<List> getParticipants(byte[] _enclaveKey) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETPARTICIPANTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Bytes32>>() {}));
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

    public RemoteFunctionCall<byte[]> getVersion() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETVERSION, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<TransactionReceipt> lock() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_LOCK, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> removeParticipant(byte[] _enclaveKey, byte[] _account) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REMOVEPARTICIPANT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey), 
                new org.web3j.abi.datatypes.generated.Bytes32(_account)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> unlock() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_UNLOCK, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static DefaultOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new DefaultOnChainPrivacyGroupManagementContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static DefaultOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new DefaultOnChainPrivacyGroupManagementContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static DefaultOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new DefaultOnChainPrivacyGroupManagementContract(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static DefaultOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new DefaultOnChainPrivacyGroupManagementContract(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(DefaultOnChainPrivacyGroupManagementContract.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(DefaultOnChainPrivacyGroupManagementContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(DefaultOnChainPrivacyGroupManagementContract.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<DefaultOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(DefaultOnChainPrivacyGroupManagementContract.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class ParticipantAddedEventResponse extends BaseEventResponse {
        public Boolean success;

        public byte[] account;

        public String message;
    }

    public static class ParticipantRemovedEventResponse extends BaseEventResponse {
        public Boolean success;

        public byte[] account;
    }
}
