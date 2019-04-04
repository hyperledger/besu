pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimplePermissioning {
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
        public view returns (bool) {
        return (enodeAllowed(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIp, sourceEnodePort) &&
        enodeAllowed(destinationEnodeHigh, destinationEnodeLow, destinationEnodeIp, destinationEnodePort));
    }
    function enodeAllowed(bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes16 sourceEnodeIp, uint16 sourceEnodePort)
    public view returns (bool){
        bytes memory key = computeKey(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIp, sourceEnodePort);
        Enode storage whitelistSource = whitelist[key];
        if (whitelistSource.enodeHost > 0) {
            return true;
        }
    }
    function addEnode(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIp, uint16 enodePort) public {
        Enode memory newEnode = Enode(enodeHigh, enodeLow, enodeIp, enodePort);
        bytes memory key = computeKey(enodeHigh, enodeLow, enodeIp, enodePort);
        whitelist[key] = newEnode;
    }
    function removeEnode(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIp, uint16 enodePort) public {
        bytes memory key = computeKey(enodeHigh, enodeLow, enodeIp, enodePort);
        Enode memory zeros = Enode(bytes32(0), bytes32(0), bytes16(0), 0);
        whitelist[key] = zeros;
    }
    function computeKey(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIp, uint16 enodePort) public pure returns (bytes memory) {
        return abi.encode(enodeHigh, enodeLow, enodeIp, enodePort);
    }
}
