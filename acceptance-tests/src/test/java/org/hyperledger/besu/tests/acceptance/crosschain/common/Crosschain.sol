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
pragma solidity ^0.5.0;

contract Crosschain {
    uint256 constant private LENGTH_OF_LENGTH_FIELD = 4;
    uint256 constant private LENGTH_OF_UINT32 = 4;
    uint256 constant private LENGTH_OF_UINT256 = 0x20;
    uint256 constant private LENGTH_OF_BYTES32 = 0x20;
    uint256 constant private LENGTH_OF_ADDRESS = 20;

    // Value to be passed ot the getInfo precompile.
    uint32 constant private GET_INFO_CROSSCHAIN_TRANSACTION_TYPE = 0;
    uint32 constant private GET_INFO_BLOCKCHAIN_ID = 1;
    uint32 constant private GET_INFO_COORDINAITON_BLOCKHCAIN_ID = 2;
    uint32 constant private GET_INFO_COORDINAITON_CONTRACT_ADDRESS = 3;
    uint32 constant private GET_INFO_ORIGINATING_BLOCKCHAIN_ID = 4;
    uint32 constant private GET_INFO_FROM_BLOCKCHAIN_ID = 5;
    uint32 constant private GET_INFO_FROM_CONTRACT_ADDRESS = 6;
    uint32 constant private GET_INFO_CROSSCHAIN_TRANSACTION_ID = 7;

    /** Generic calling of functions across chains.
      * Combined with abi.encodeWithSelector, allows to use Solidity function types and arbitrary arguments.
      * @param encodedFunctionCall  = abi.encodeWithSelector(function.selector, ...)
      */
    function crosschainTransaction(uint256 sidechainId, address addr, bytes memory encodedFunctionCall) internal {

        bytes memory dataBytes = abi.encode(sidechainId, addr, encodedFunctionCall);
        // The "bytes" type has a 32 byte header containing the size in bytes of the actual data,
        // which is transparent to Solidity, so the bytes.length property doesn't report it.
        // But the assembly "call" instruction gets the underlying bytes of the "bytes" data type, so the length needs
        // to be corrected.
        // Also, as of Solidity 0.5.11 there is no sane way to convert a dynamic type to a static array.
        // Therefore we hackishly compensate the "bytes" length and deal with it inside the precompile.
        uint256 dataBytesRawLength = dataBytes.length + LENGTH_OF_LENGTH_FIELD;

        // Note: the tuple being encoded contains a dynamic type itself, which changes its internal representation;
        // but since it is abi.encoded in Solidity, it's transparent.
        // The problem only appears when using the wrapping "bytes" in Assembly.

        assembly {
        // Read: https://medium.com/@rbkhmrcr/precompiles-solidity-e5d29bd428c4
        //call(gasLimit, to, value, inputOffset, inputSize, outputOffset, outputSize)
        // SUBORDINATE_TRANSACTION_PRECOMPILE = 10. Inline assembler doesn't support constants.
            if iszero(call(not(0), 10, 0, dataBytes, dataBytesRawLength, 0, 0)) {
                revert(0, 0)
            }
        }
    }



    function crosschainViewUint256(uint256 sidechainId, address addr, bytes memory encodedFunctionCall) internal view returns (uint256) {

        bytes memory dataBytes = abi.encode(sidechainId, addr, encodedFunctionCall);
        // The "bytes" type has a 32 byte header containing the size in bytes of the actual data,
        // which is transparent to Solidity, so the bytes.length property doesn't report it.
        // But the assembly "call" instruction gets the underlying bytes of the "bytes" data type, so the length needs
        // to be corrected.
        // Also, as of Solidity 0.5.11 there is no sane way to convert a dynamic type to a static array.
        // Therefore we hackishly compensate the "bytes" length and deal with it inside the precompile.
        uint256 dataBytesRawLength = dataBytes.length + LENGTH_OF_LENGTH_FIELD;

        // Note: the tuple being encoded contains a dynamic type itself, which changes its internal representation;
        // but since it is abi.encoded in Solidity, it's transparent.
        // The problem only appears when using the wrapping "bytes" in Assembly.

        uint256[1] memory result;
        uint256 resultLength = LENGTH_OF_UINT256;

        assembly {
            // Read: https://medium.com/@rbkhmrcr/precompiles-solidity-e5d29bd428c4
            // and
            // https://www.reddit.com/r/ethdev/comments/7p8b86/it_is_possible_to_call_a_precompiled_contracts/
            //  staticcall(gasLimit, to, inputOffset, inputSize, outputOffset, outputSize)
            // SUBORDINATE_VIEW_PRECOMPILE = 11. Inline assembler doesn't support constants.
            if iszero(staticcall(not(0), 11, dataBytes, dataBytesRawLength, result, resultLength)) {
                revert(0, 0)
            }
        }

        return result[0];
    }


    /**
     * Determine the type of crosschain transaction being executed.
     *
     * The return value will be one of:
     *  ORIGINATING_TRANSACTION = 0
     *  SUBORDINATE_TRANSACTION = 1
     *  SUBORDINATE_VIEW = 2
     *  ORIGINATING_LOCKABLE_CONTRACT_DEPLOY = 3
     *  SUBORDINATE_LOCKABLE_CONTRACT_DEPLOY = 4
     *  SINGLECHAIN_LOCKABLE_CONTRACT_DEPLOY = 5
     *  UNLOCK_COMMIT_SIGNALLING_TRANSACTION = 6
     *  UNLOCK_IGNORE_SIGNALLING_TRANSACTION = 7
     *
     * @return the type of crosschain transaction.
     */
    function crosschainGetInfoTransactionType() internal view returns (uint32) {
        uint256 inputLength = LENGTH_OF_UINT32;
        uint32[1] memory input;
        input[0] = GET_INFO_CROSSCHAIN_TRANSACTION_TYPE;

        uint32[1] memory result;
        uint256 resultLength = LENGTH_OF_UINT32;

        assembly {
        // Read: https://medium.com/@rbkhmrcr/precompiles-solidity-e5d29bd428c4
        // and
        // https://www.reddit.com/r/ethdev/comments/7p8b86/it_is_possible_to_call_a_precompiled_contracts/
        //  staticcall(gasLimit, to, inputOffset, inputSize, outputOffset, outputSize)
        // GET_INFO_PRECOMPILE = 250. Inline assembler doesn't support constants.
            if iszero(staticcall(not(0), 250, input, inputLength, result, resultLength)) {
                revert(0, 0)
            }
        }

        return result[0];
    }



    /**
     * Get information about the transaction currently executing.
     *
     * @return Blockchain ID of this blockchain.
     */
    function crosschainGetInfoBlockchainId() internal view returns (uint256) {
        return getInfoBlockchainId(GET_INFO_BLOCKCHAIN_ID);
    }

    /**
     * Get information about the transaction currently executing.
     *
     * @return Blockchain ID of the Coordination Blockchain.
     *   0x00 is returned it the current transaction is a Single Blockchain Lockable
     *   Contract Deploy.
     */
    function crosschainGetInfoCoordinationBlockchainId() internal view returns (uint256) {
        return getInfoBlockchainId(GET_INFO_COORDINAITON_BLOCKHCAIN_ID);
    }

    /**
     * Get information about the transaction currently executing.
     *
     * @return Blockchain ID of the Originating Blockchain.
     *   0x00 is returned it the current transaction is an Originating Transaction or a
     *   Single Blockchain Lockable Contract Deploy.
     */
    function crosschainGetInfoOriginatingBlockchainId() internal view returns (uint256) {
        return getInfoBlockchainId(GET_INFO_ORIGINATING_BLOCKCHAIN_ID);
    }

    /**
     * Get information about the transaction currently executing.
     *
     * @return Blockchain ID of the blockchain from which this function was called.
     *   0x00 is returned it the current transaction is an Originating Transaction or a
     *   Single Blockchain Lockable Contract Deploy.
     */
    function crosschainGetInfoFromBlockchainId() internal view returns (uint256) {
        return getInfoBlockchainId(GET_INFO_FROM_BLOCKCHAIN_ID);
    }

    /**
     * Get information about the transaction currently executing.
     *
     * @return Crosschain Transaction Identifier.
     *   0x00 is returned it the current transaction is a Single Blockchain Lockable
     *   Contract Deploy.
     */
    function crosschainGetInfoCrosschainTransactionId() internal view returns (uint256) {
        return getInfoBlockchainId(GET_INFO_CROSSCHAIN_TRANSACTION_ID);
    }

    function getInfoBlockchainId(uint256 _requestedId) private view returns (uint256) {
        uint256 inputLength = LENGTH_OF_UINT256;
        uint256[1] memory input;
        input[0] = _requestedId;

        uint256[1] memory result;
        uint256 resultLength = LENGTH_OF_UINT256;

        assembly {
        // GET_INFO_PRECOMPILE = 120. Inline assembler doesn't support constants.
            if iszero(staticcall(not(0), 120, input, inputLength, result, resultLength)) {
                revert(0, 0)
            }
        }
        return result[0];
    }


    /**
     * Get information about the transaction currently executing.
     *
     * @return Crosschain Coordination Contract address.
     *   0x00 is returned it the current transaction is a Single Blockchain Lockable
     *   Contract Deploy.
     */
    function crosschainGetInfoCoordinationContractAddress() internal view returns (address) {
        return getInfoAddress(GET_INFO_COORDINAITON_CONTRACT_ADDRESS);
    }

    /**
     * Get information about the transaction currently executing.
     *
     * @return Address of contract from which this function was called.
     *   0x00 is returned it the current transaction is an Originating Transaction or a
     *   Single Blockchain Lockable Contract Deploy.
     */
    function crosschainGetInfoFromAddress() internal view returns (address) {
        return getInfoAddress(GET_INFO_FROM_CONTRACT_ADDRESS);
    }


    function getInfoAddress(uint256 _requestedAddress) private view returns (address) {
        uint256 inputLength = LENGTH_OF_UINT256;
        uint256[1] memory input;
        input[0] = _requestedAddress;

        // The return type is an address. However, we need to specify that we want a whole
        // Ethereum word copied or we will end up with 20 bytes of th eaddress being masked off.
        address[1] memory result;
        uint256 resultLength = LENGTH_OF_UINT256;

        assembly {
        // GET_INFO_PRECOMPILE = 120. Inline assembler doesn't support constants.
            if iszero(staticcall(not(0), 120, input, inputLength, result, resultLength)) {
                revert(0, 0)
            }
        }
        return result[0];
    }

}
