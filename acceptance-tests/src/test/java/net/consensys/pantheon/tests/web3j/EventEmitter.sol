pragma solidity ^0.4.0;
// compile with:
// solc EventEmitter.sol --bin --abi --optimize --overwrite -o .
// then create web3j wrappers with:
// web3j solidity generate ./generated/EventEmitter.bin ./generated/EventEmitter.abi -o ../../../../../ -p net.consensys.pantheon.tests.web3j.generated
contract EventEmitter {
    address owner;
    event stored(address _to, uint _amount);
    address _sender;
    uint _value;

    constructor() public {
        owner = msg.sender;
    }

    function store(uint _amount) public {
        emit stored(msg.sender, _amount);
        _value = _amount;
        _sender = msg.sender;
    }

    function value()  constant public  returns (uint) {
        return _value;
    }

    function sender() constant public returns (address) {
        return _sender;
    }
}