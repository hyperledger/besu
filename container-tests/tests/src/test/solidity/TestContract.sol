pragma solidity >=0.6.0 < 0.8.0;

contract TestContract {
    event TestEvent(address indexed _from, int val);

    function logEvent(int randomNum) public {
        emit TestEvent(msg.sender, randomNum);
    }
}
