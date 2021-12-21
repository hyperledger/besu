/*
 * Copyright ConsenSys AG.
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
pragma solidity >=0.7.0 <0.9.0;

// compile with:
// solc EventEmitter.sol --bin --abi --optimize --overwrite -o .
// then create web3j wrappers with:
// web3j solidity generate -b ./generated/EventEmitter.bin -a ./generated/EventEmitter.abi -o ../../../../../ -p org.hyperledger.besu.tests.web3j.generated
contract EventEmitter {
    address owner;
    event stored(address _to, uint _amount);
    address _sender;
    uint _value;

    constructor() {
        owner = msg.sender;
    }

    function store(uint _amount) public {
        emit stored(msg.sender, _amount);
        _value = _amount;
        _sender = msg.sender;
    }

    function value()  view public  returns (uint) {
        return _value;
    }

    function sender() view public returns (address) {
        return _sender;
    }
}