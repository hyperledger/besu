/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimpleNodePermissioning {
    struct Enode {
        bytes32 enodeHigh;
        bytes32 enodeLow;
        bytes16 enodeHost;
        uint16 enodePort;
    }
    mapping(bytes => Enode) private whitelist; // should there be a size for the whitelists?

    function connectionAllowed(
        bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes16 sourceEnodeIp, uint16 sourceEnodePort,
        bytes32 destinationEnodeHigh, bytes32 destinationEnodeLow, bytes16 destinationEnodeIp, uint16 destinationEnodePort)
        public view returns (bytes32) {
        if (enodeAllowed(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIp, sourceEnodePort) &&
        enodeAllowed(destinationEnodeHigh, destinationEnodeLow, destinationEnodeIp, destinationEnodePort)) {
            return 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff;
        } else {
            return 0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff;
        }
    }
    function enodeAllowed(bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes16 sourceEnodeIp, uint16 sourceEnodePort)
    public view returns (bool){
        bytes memory key = computeKey(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIp);
        Enode storage whitelistSource = whitelist[key];
        if (whitelistSource.enodeHost > 0) {
            return true;
        }
    }
    function addEnode(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIp, uint16 enodePort) public {
        Enode memory newEnode = Enode(enodeHigh, enodeLow, enodeIp, enodePort);
        bytes memory key = computeKey(enodeHigh, enodeLow, enodeIp);
        whitelist[key] = newEnode;
    }
    function removeEnode(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIp, uint16 enodePort) public {
        bytes memory key = computeKey(enodeHigh, enodeLow, enodeIp);
        Enode memory zeros = Enode(bytes32(0), bytes32(0), bytes16(0), 0);
        whitelist[key] = zeros;
    }
    function computeKey(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIp) public pure returns (bytes memory) {
        // enodePort has been removed to aid acceptance tests with random node ports
        // This method is not used in production code
        return abi.encode(enodeHigh, enodeLow, enodeIp);
    }
}
