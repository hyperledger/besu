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
    /*
        Node port is not being considered because ATs use random ports
    */
    struct Enode {
        string enodeId;
        string enodeIp;
    }
    mapping(bytes => Enode) private allowlist;

    // Port is being ignored
    function connectionAllowed(string memory enodeId, string memory ip, uint16) public view returns (bool) {
        return enodeAllowed(enodeId, ip);
    }

    function enodeAllowed(string memory enodeId, string memory sourceEnodeIp) private view returns (bool){
        bytes memory key = computeKey(enodeId, sourceEnodeIp);
        // if enode was found, keccak256 will be different
        return keccak256(bytes(allowlist[key].enodeIp)) != keccak256("");
    }

    function addEnode(string memory enodeId, string memory enodeIp, uint16) public {
        Enode memory newEnode = Enode(enodeId, enodeIp);
        bytes memory key = computeKey(enodeId, enodeIp);
        allowlist[key] = newEnode;
    }

    function removeEnode(string memory enodeId, string memory enodeIp, uint16) public {
        bytes memory key = computeKey(enodeId, enodeIp);
        Enode memory zeros = Enode("", "");
        // replace enode with "zeros"
        allowlist[key] = zeros;
    }

    function computeKey(string memory enodeId, string memory enodeIp) private pure returns (bytes memory) {
        return abi.encode(enodeId, enodeIp);
    }
}
