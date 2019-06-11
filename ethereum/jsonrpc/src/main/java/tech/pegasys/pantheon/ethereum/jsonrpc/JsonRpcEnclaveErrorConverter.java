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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;

public class JsonRpcEnclaveErrorConverter {

  public static JsonRpcError convertEnclaveInvalidReason(final String reason) {

    switch (reason) {
      case "NodeMissingPeerUrl":
        return JsonRpcError.NODE_MISSING_PEER_URL;
      case "NodePushingToPeer":
        return JsonRpcError.NODE_PUSHING_TO_PEER;
      case "NodePropagatingToAllPeers":
        return JsonRpcError.NODE_PROPAGATING_TO_ALL_PEERS;
      case "NoSenderKey":
        return JsonRpcError.NO_SENDER_KEY;
      case "InvalidPayload":
        return JsonRpcError.INVALID_PAYLOAD;
      case "EnclaveCreateKeyPair":
        return JsonRpcError.ENCLAVE_CREATE_KEY_PAIR;
      case "EnclaveDecodePublicKey":
        return JsonRpcError.ENCLAVE_DECODE_PUBLIC_KEY;
      case "EnclaveDecryptWrongPrivateKey":
        return JsonRpcError.ENCLAVE_DECRYPT_WRONG_PRIVATE_KEY;
      case "EnclaveEncryptCombineKeys":
        return JsonRpcError.ENCLAVE_ENCRYPT_COMBINE_KEYS;
      case "EnclaveMissingPrivateKeyPasswords":
        return JsonRpcError.ENCLAVE_MISSING_PRIVATE_KEY_PASSWORD;
      case "EnclaveNoMatchingPrivateKey":
        return JsonRpcError.ENCLAVE_NO_MATCHING_PRIVATE_KEY;
      case "EnclaveNotPayloadOwner":
        return JsonRpcError.ENCLAVE_NOT_PAYLOAD_OWNER;
      case "EnclaveUnsupportedPrivateKeyType":
        return JsonRpcError.ENCLAVE_UNSUPPORTED_PRIVATE_KEY_TYPE;
      case "EnclaveStorageDecrypt":
        return JsonRpcError.ENCLAVE_STORAGE_DECRYPT;
      case "EnclavePrivacyGroupIdCreation":
        return JsonRpcError.ENCLAVE_PRIVACY_GROUP_CREATION;

      default:
        return JsonRpcError.ENCLAVE_ERROR;
    }
  }
}
