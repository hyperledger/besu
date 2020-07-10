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
import org.web3j.abi.datatypes.Address;
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
public class OwnerOnChainPrivacyGroupManagementContract extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b5033600260006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550610cf6806100616000396000f3fe608060405234801561001057600080fd5b50600436106100935760003560e01c8063893d20e811610066578063893d20e8146101ab578063a69df4b5146101f5578063f744b089146101ff578063f83d08ba146102d9578063f942ebd6146102e357610093565b80630b0235be146100985780630d8e6e2c1461011b57806361544c911461013957806378b9033714610189575b600080fd5b6100c4600480360360208110156100ae57600080fd5b8101908080359060200190929190505050610329565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156101075780820151818401526020810190506100ec565b505050509050019250505060405180910390f35b610123610395565b6040518082815260200191505060405180910390f35b61016f6004803603604081101561014f57600080fd5b81019080803590602001909291908035906020019092919050505061039f565b604051808215151515815260200191505060405180910390f35b610191610470565b604051808215151515815260200191505060405180910390f35b6101b3610486565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6101fd6104b0565b005b6102bf6004803603604081101561021557600080fd5b81019080803590602001909291908035906020019064010000000081111561023c57600080fd5b82018360208201111561024e57600080fd5b8035906020019184602083028401116401000000008311171561027057600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f8201169050808301925050505050505091929192905050506104e5565b604051808215151515815260200191505060405180910390f35b6102e1610601565b005b61030f600480360360208110156102f957600080fd5b8101908080359060200190929190505050610635565b604051808215151515815260200191505060405180910390f35b6060610334826106f8565b61033d57600080fd5b600380548060200260200160405190810160405280929190818152602001828054801561038957602002820191906000526020600020905b815481526020019060010190808311610375575b50505050509050919050565b6000600154905090565b60006103aa836106f8565b6103b357600080fd5b600260009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff161461040d57600080fd5b600061041883610718565b90506104226107fb565b507fbbeb554e7225026991ae908172deed16661afb44ee3ff3d11b6e4aeec79066bf818460405180831515151581526020018281526020019250505060405180910390a18091505092915050565b60008060009054906101000a900460ff16905090565b6000600260009054906101000a900473ffffffffffffffffffffffffffffffffffffffff16905090565b6000809054906101000a900460ff16156104c957600080fd5b60016000806101000a81548160ff021916908315150217905550565b60008060009054906101000a900460ff161561050057600080fd5b6000600380549050141561055a5733600260006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055506105588361089b565b505b610563836106f8565b61056c57600080fd5b600260009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16146105c657600080fd5b60006105d2848461090d565b905060016000806101000a81548160ff0219169083151502179055506105f66107fb565b508091505092915050565b6000809054906101000a900460ff1661061957600080fd5b60008060006101000a81548160ff021916908315150217905550565b6000610640826106f8565b61064957600080fd5b600260009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16146106ef576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401808060200182810382526031815260200180610c626031913960400191505060405180910390fd5b60019050919050565b600080600460008481526020019081526020016000205414159050919050565b6000806004600084815260200190815260200160002054905060008111801561074657506003805490508111155b156107f05760038054905081146107b457600060036001600380549050038154811061076e57fe5b90600052602060002001549050806003600184038154811061078c57fe5b9060005260206000200181905550816004600083815260200190815260200160002081905550505b60016003818180549050039150816107cc9190610bef565b506000600460008581526020019081526020016000208190555060019150506107f6565b60009150505b919050565b60006001430340416003604051602001808481526020018373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1660601b8152601401828054801561087557602002820191906000526020600020905b815481526020019060010190808311610861575b505093505050506040516020818303038152906040528051906020012060018190555090565b600080600460008481526020019081526020016000205414156109035760038290806001815401808255809150509060018203906000526020600020016000909192909190915055600460008481526020019081526020016000208190555060019050610908565b600090505b919050565b6000806001905060008090505b8351811015610be45783818151811061092f57fe5b60200260200101518514156109c3577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf213600085838151811061096d57fe5b60200260200101516040518083151515158152602001828152602001806020018281038252602f815260200180610c93602f9139604001935050505060405180910390a18180156109bc575060005b9150610bd7565b6109df8482815181106109d257fe5b60200260200101516106f8565b15610a86577fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf2136000858381518110610a1357fe5b60200260200101516040518083151515158152602001828152602001806020018281038252601b8152602001807f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250602001935050505060405180910390a1818015610a7f575060005b9150610bd6565b6000610aa4858381518110610a9757fe5b602002602001015161089b565b9050606081610ae8576040518060400160405280601b81526020017f4163636f756e7420697320616c72656164792061204d656d6265720000000000815250610b02565b604051806060016040528060218152602001610c41602191395b90507fcc7365305ae5f16c463d1383713d699f43c5548bbda5537ee61373ceb9aaf21382878581518110610b3257fe5b602002602001015183604051808415151515815260200183815260200180602001828103825283818151815260200191508051906020019080838360005b83811015610b8b578082015181840152602081019050610b70565b50505050905090810190601f168015610bb85780820380516001836020036101000a031916815260200191505b5094505050505060405180910390a1838015610bd15750815b935050505b5b808060010191505061091a565b508091505092915050565b815481835581811115610c1657818360005260206000209182019101610c159190610c1b565b5b505050565b610c3d91905b80821115610c39576000816000905550600101610c21565b5090565b9056fe4d656d626572206163636f756e74206164646564207375636365737366756c6c7953656e646572206e6f7420616c6c6f77656420746f2075706772616465206d616e6167656d656e7420636f6e7472616374416464696e67206f776e206163636f756e742061732061204d656d626572206973206e6f74207065726d6974746564a265627a7a723158205a35004e487e1e474e36a0c4c9eeeecf241ab5dd9fee075574a6d642c368b0b264736f6c63430005110032";

    public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

    public static final String FUNC_CANEXECUTE = "canExecute";

    public static final String FUNC_CANUPGRADE = "canUpgrade";

    public static final String FUNC_GETOWNER = "getOwner";

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
    protected OwnerOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected OwnerOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected OwnerOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected OwnerOnChainPrivacyGroupManagementContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
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

    public RemoteFunctionCall<String> getOwner() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETOWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
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
    public static OwnerOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new OwnerOnChainPrivacyGroupManagementContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static OwnerOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new OwnerOnChainPrivacyGroupManagementContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static OwnerOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new OwnerOnChainPrivacyGroupManagementContract(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static OwnerOnChainPrivacyGroupManagementContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new OwnerOnChainPrivacyGroupManagementContract(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<OwnerOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(OwnerOnChainPrivacyGroupManagementContract.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<OwnerOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(OwnerOnChainPrivacyGroupManagementContract.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<OwnerOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(OwnerOnChainPrivacyGroupManagementContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<OwnerOnChainPrivacyGroupManagementContract> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(OwnerOnChainPrivacyGroupManagementContract.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
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
