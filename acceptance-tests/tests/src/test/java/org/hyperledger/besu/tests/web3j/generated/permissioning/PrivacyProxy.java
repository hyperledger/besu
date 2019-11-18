package org.hyperledger.besu.tests.web3j.generated.permissioning;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
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
 * <p>Generated with web3j version 4.5.6.
 */
@SuppressWarnings("rawtypes")
public class PrivacyProxy extends Contract {
    private static final String BINARY = "608060405234801561001057600080fd5b506040516104583803806104588339818101604052602081101561003357600080fd5b5051600080546001600160a01b03199081163317909155600180546001600160a01b03909316929091169190911790556103e6806100726000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80633659cfe6146100465780635aa68ac01461006e5780635b4ccc9d146100c6575b600080fd5b61006c6004803603602081101561005c57600080fd5b50356001600160a01b031661017d565b005b6100766101bb565b60408051602080825283518183015283519192839290830191858101910280838360005b838110156100b257818101518382015260200161009a565b505050509050019250505060405180910390f35b610169600480360360208110156100dc57600080fd5b8101906020810181356401000000008111156100f757600080fd5b82018360208201111561010957600080fd5b8035906020019184602083028401116401000000008311171561012b57600080fd5b9190808060200260200160405190810160405280939291908181526020018383602002808284376000920191909152509295506102d2945050505050565b604080519115158252519081900360200190f35b6000546001600160a01b0316331461019457600080fd5b6001546001600160a01b03828116911614156101af57600080fd5b6101b88161038f565b50565b6001546040805163016a9a2b60e61b815290516060926001600160a01b0316918291635aa68ac091600480820192600092909190829003018186803b15801561020357600080fd5b505afa158015610217573d6000803e3d6000fd5b505050506040513d6000823e601f3d908101601f19168201604052602081101561024057600080fd5b810190808051604051939291908464010000000082111561026057600080fd5b90830190602082018581111561027557600080fd5b825186602082028301116401000000008211171561029257600080fd5b82525081516020918201928201910280838360005b838110156102bf5781810151838201526020016102a7565b5050505090500160405250505091505090565b600154604051635b4ccc9d60e01b81526020600482018181528451602484015284516000946001600160a01b0316938493635b4ccc9d9388939092839260449091019181860191028083838c5b8381101561033757818101518382015260200161031f565b5050505090500192505050602060405180830381600087803b15801561035c57600080fd5b505af1158015610370573d6000803e3d6000fd5b505050506040513d602081101561038657600080fd5b50519392505050565b600180546001600160a01b0319166001600160a01b039290921691909117905556fea265627a7a72315820d03a1153d5b8978e97b4afaf8e36d3661eb2842d4f7321c77808c6edcc8ac7fb64736f6c634300050c0032";

    public static final String FUNC_ADDPARTICIPANTS = "addParticipants";

    public static final String FUNC_GETPARTICIPANTS = "getParticipants";

    public static final String FUNC_UPGRADETO = "upgradeTo";

    @Deprecated
    protected PrivacyProxy(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected PrivacyProxy(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected PrivacyProxy(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected PrivacyProxy(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<TransactionReceipt> addParticipants(List<String> accounts) {
        final Function function = new Function(
                FUNC_ADDPARTICIPANTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<List> getParticipants() {
        final Function function = new Function(FUNC_GETPARTICIPANTS, 
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

    public RemoteFunctionCall<TransactionReceipt> upgradeTo(String _newImplementation) {
        final Function function = new Function(
                FUNC_UPGRADETO, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _newImplementation)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static PrivacyProxy load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new PrivacyProxy(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static PrivacyProxy load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new PrivacyProxy(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static PrivacyProxy load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new PrivacyProxy(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static PrivacyProxy load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new PrivacyProxy(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<PrivacyProxy> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(PrivacyProxy.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<PrivacyProxy> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(PrivacyProxy.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<PrivacyProxy> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(PrivacyProxy.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<PrivacyProxy> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, String _implementation) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _implementation)));
        return deployRemoteCall(PrivacyProxy.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
    }
}
