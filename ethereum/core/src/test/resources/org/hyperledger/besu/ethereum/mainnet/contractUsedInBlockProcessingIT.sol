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
pragma solidity ^0.8.19;

contract SimpleStorage {
    uint256 private slot1; //0x0
    uint256 private slot2; //0x1
    uint256 private slot3; //0x2

    function setSlot1(uint256 value) public {
        slot1 = value;
    }

    function setSlot2(uint256 value) public {
        slot2 = value;
    }

    function setSlot3(uint256 value) public {
        slot3 = value;
    }

    function getSlot1() public view returns (uint256) {
        return slot1;
    }

    function getSlot2() public view returns (uint256) {
        return slot2;
    }

    function getSlot3() public view returns (uint256) {
        return slot3;
    }

    function getBalance(address account) public view returns (uint256) {
        return account.balance;
    }

    function transferTo(address payable recipient, uint256 amount) public payable {
        require(amount <= address(this).balance, "Insufficient balance in contract");
        recipient.transfer(amount);
    }

    receive() external payable {}
}
