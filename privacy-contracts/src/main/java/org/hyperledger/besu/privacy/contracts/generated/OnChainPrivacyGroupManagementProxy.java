package org.hyperledger.besu.privacy.contracts.generated;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
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
public class OnChainPrivacyGroupManagementProxy extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b50604051610e25380380610e258339818101604052602081101561003357600080fd5b8101908080519060200190929190505050806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050610d91806100946000396000f3fe608060405234801561001057600080fd5b506004361061009e5760003560e01c8063a07051b611610066578063a07051b614610200578063a69df4b51461024e578063f744b08914610258578063f83d08ba14610332578063f942ebd61461033c5761009e565b80630b0235be146100a35780630d8e6e2c146101265780635c60da1b1461014457806361544c911461018e57806378b90337146101de575b600080fd5b6100cf600480360360208110156100b957600080fd5b8101908080359060200190929190505050610382565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156101125780820151818401526020810190506100f7565b505050509050019250505060405180910390f35b61012e6104d8565b6040518082815260200191505060405180910390f35b61014c610586565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6101c4600480360360408110156101a457600080fd5b8101908080359060200190929190803590602001909291905050506105ab565b604051808215151515815260200191505060405180910390f35b6101e6610671565b604051808215151515815260200191505060405180910390f35b61024c6004803603604081101561021657600080fd5b8101908080359060200190929190803573ffffffffffffffffffffffffffffffffffffffff16906020019092919050505061071f565b005b610256610a45565b005b6103186004803603604081101561026e57600080fd5b81019080803590602001909291908035906020019064010000000081111561029557600080fd5b8201836020820111156102a757600080fd5b803590602001918460208302840111640100000000831117156102c957600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f820116905080830192505050505050509192919290505050610ace565b604051808215151515815260200191505060405180910390f35b61033a610bd5565b005b6103686004803603602081101561035257600080fd5b8101908080359060200190929190505050610c5e565b604051808215151515815260200191505060405180910390f35b606060008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16630b0235be846040518263ffffffff1660e01b81526004018082815260200191505060006040518083038186803b1580156103fb57600080fd5b505afa15801561040f573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250602081101561043957600080fd5b810190808051604051939291908464010000000082111561045957600080fd5b8382019150602082018581111561046f57600080fd5b825186602082028301116401000000008211171561048c57600080fd5b8083526020830192505050908051906020019060200280838360005b838110156104c35780820151818401526020810190506104a8565b50505050905001604052505050915050919050565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16630d8e6e2c6040518163ffffffff1660e01b815260040160206040518083038186803b15801561054557600080fd5b505afa158015610559573d6000803e3d6000fd5b505050506040513d602081101561056f57600080fd5b810190808051906020019092919050505091505090565b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff166361544c9185856040518363ffffffff1660e01b81526004018083815260200182815260200192505050602060405180830381600087803b15801561062d57600080fd5b505af1158015610641573d6000803e3d6000fd5b505050506040513d602081101561065757600080fd5b810190808051906020019092919050505091505092915050565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff166378b903376040518163ffffffff1660e01b815260040160206040518083038186803b1580156106de57600080fd5b505afa1580156106f2573d6000803e3d6000fd5b505050506040513d602081101561070857600080fd5b810190808051906020019092919050505091505090565b8073ffffffffffffffffffffffffffffffffffffffff166000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16141561077957600080fd5b3073ffffffffffffffffffffffffffffffffffffffff1663f942ebd6836040518263ffffffff1660e01b81526004018082815260200191505060206040518083038186803b1580156107ca57600080fd5b505afa1580156107de573d6000803e3d6000fd5b505050506040513d60208110156107f457600080fd5b810190808051906020019092919050505061080e57600080fd5b60603073ffffffffffffffffffffffffffffffffffffffff16630b0235be846040518263ffffffff1660e01b81526004018082815260200191505060006040518083038186803b15801561086157600080fd5b505afa158015610875573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250602081101561089f57600080fd5b81019080805160405193929190846401000000008211156108bf57600080fd5b838201915060208201858111156108d557600080fd5b82518660208202830111640100000000821117156108f257600080fd5b8083526020830192505050908051906020019060200280838360005b8381101561092957808201518184015260208101905061090e565b50505050905001604052505050905061094182610d19565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f744b08985846040518363ffffffff1660e01b81526004018083815260200180602001828103825283818151815260200191508051906020019060200280838360005b838110156109dd5780820151818401526020810190506109c2565b505050509050019350505050602060405180830381600087803b158015610a0357600080fd5b505af1158015610a17573d6000803e3d6000fd5b505050506040513d6020811015610a2d57600080fd5b81019080805190602001909291905050505050505050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663a69df4b56040518163ffffffff1660e01b8152600401600060405180830381600087803b158015610ab357600080fd5b505af1158015610ac7573d6000803e3d6000fd5b5050505050565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f744b08985856040518363ffffffff1660e01b81526004018083815260200180602001828103825283818151815260200191508051906020019060200280838360005b83811015610b6b578082015181840152602081019050610b50565b505050509050019350505050602060405180830381600087803b158015610b9157600080fd5b505af1158015610ba5573d6000803e3d6000fd5b505050506040513d6020811015610bbb57600080fd5b810190808051906020019092919050505091505092915050565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f83d08ba6040518163ffffffff1660e01b8152600401600060405180830381600087803b158015610c4357600080fd5b505af1158015610c57573d6000803e3d6000fd5b5050505050565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f942ebd6846040518263ffffffff1660e01b81526004018082815260200191505060206040518083038186803b158015610cd657600080fd5b505afa158015610cea573d6000803e3d6000fd5b505050506040513d6020811015610d0057600080fd5b8101908080519060200190929190505050915050919050565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505056fea265627a7a7231582057d895787fb9a28a444499bc3c2ce8668e8c5f0ccd7701428a2219339e1c034064736f6c63430005110032";

    public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

    public static final String FUNC_CANEXECUTE = "canExecute";

    public static final String FUNC_CANUPGRADE = "canUpgrade";

    public static final String FUNC_GETPARTICIPANTS = "getParticipants";

    public static final String FUNC_GETVERSION = "getVersion";

    public static final String FUNC_IMPLEMENTATION = "implementation";

    public static final String FUNC_LOCK = "lock";

    public static final String FUNC_REMOVEPARTICIPANT = "removeParticipant";

    public static final String FUNC_UNLOCK = "unlock";

    public static final String FUNC_UPGRADETO = "upgradeTo";

    @Deprecated
    protected OnChainPrivacyGroupManagementProxy(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected OnChainPrivacyGroupManagementProxy(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected OnChainPrivacyGroupManagementProxy(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected OnChainPrivacyGroupManagementProxy(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<TransactionReceipt> addParticipants(byte[] enclaveKey, List<byte[]> participants) {
        final Function function = new Function(
                FUNC_ADDPARTICIPANTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                        org.web3j.abi.datatypes.generated.Bytes32.class,
                        org.web3j.abi.Utils.typeMap(participants, org.web3j.abi.datatypes.generated.Bytes32.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> canExecute() {
        final Function function = new Function(FUNC_CANEXECUTE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Boolean> canUpgrade(byte[] _enclaveKey) {
        final Function function = new Function(FUNC_CANUPGRADE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<List> getParticipants(byte[] enclaveKey) {
        final Function function = new Function(FUNC_GETPARTICIPANTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey)), 
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
        final Function function = new Function(FUNC_GETVERSION, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<String> implementation() {
        final Function function = new Function(FUNC_IMPLEMENTATION, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> lock() {
        final Function function = new Function(
                FUNC_LOCK, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> removeParticipant(byte[] enclaveKey, byte[] account) {
        final Function function = new Function(
                FUNC_REMOVEPARTICIPANT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(enclaveKey), 
                new org.web3j.abi.datatypes.generated.Bytes32(account)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> unlock() {
        final Function function = new Function(
                FUNC_UNLOCK, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> upgradeTo(byte[] _enclaveKey, String _newImplementation) {
        final Function function = new Function(
                FUNC_UPGRADETO, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(_enclaveKey), 
                new org.web3j.abi.datatypes.Address(160, _newImplementation)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static OnChainPrivacyGroupManagementProxy load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new OnChainPrivacyGroupManagementProxy(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static OnChainPrivacyGroupManagementProxy load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new OnChainPrivacyGroupManagementProxy(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static OnChainPrivacyGroupManagementProxy load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new OnChainPrivacyGroupManagementProxy(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static OnChainPrivacyGroupManagementProxy load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new OnChainPrivacyGroupManagementProxy(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(OnChainPrivacyGroupManagementProxy.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(OnChainPrivacyGroupManagementProxy.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(OnChainPrivacyGroupManagementProxy.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<OnChainPrivacyGroupManagementProxy> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(OnChainPrivacyGroupManagementProxy.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
    }
}
