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

contract SimpleAccountPermissioning {
    mapping (address => bool) private whitelist;
    uint256 private size;

    function transactionAllowed(
        address sender,
        address target,
        uint256 value,
        uint256 gasPrice,
        uint256 gasLimit,
        bytes memory payload)
    public view returns (bool) {
        return whitelistContains(sender);
    }

    function addAccount(address account) public {
        if (!whitelist[account]) {
            whitelist[account] = true;
            size++;
        }
    }

    function removeAccount(address account) public {
        if (whitelist[account]) {
            whitelist[account] = false;
            size--;
        }
    }

    // For testing purposes, this will return true if the whitelist is empty
    function whitelistContains(address account) public view returns(bool) {
        if (size == 0) {
            return true;
        } else {
            return whitelist[account];
        }
    }

    function getSize() public view returns(uint256) {
        return size;
    }
}
