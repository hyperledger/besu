pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimplePermissioning {
    struct EnodeIpv6 {
        bytes32 enodeHigh;
        bytes32 enodeLow;
        bytes16 enodeHost; // Ipv6
        uint16 enodePort;
    }
    struct EnodeIpv4 {
        bytes32 enodeHigh;
        bytes32 enodeLow;
        bytes4 enodeHost; // Ipv4
        uint16 enodePort;
    }
    mapping(bytes => EnodeIpv6) private whitelistIpv6; // should there be a size for the whitelists?
    mapping(bytes => EnodeIpv4) private whitelistIpv4; 

    function connectionAllowedIpv6(
        bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes16 sourceEnodeIpv6, uint16 sourceEnodePort, 
        bytes32 destinationEnodeHigh, bytes32 destinationEnodeLow, bytes16 destinationEnodeIpv6, uint16 destinationEnodePort) 
        public view returns (bool) {
        return (enodeAllowedIpv6(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIpv6, sourceEnodePort) && 
        enodeAllowedIpv6(destinationEnodeHigh, destinationEnodeLow, destinationEnodeIpv6, destinationEnodePort));
    }
    function connectionAllowedIpv4(
        bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes4 sourceEnodeIpv4, uint16 sourceEnodePort, 
        bytes32 destinationEnodeHigh, bytes32 destinationEnodeLow, bytes4 destinationEnodeIpv4, uint16 destinationEnodePort) 
        public view returns (bool){
        return (enodeAllowedIpv4(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIpv4, sourceEnodePort) && 
        enodeAllowedIpv4(destinationEnodeHigh, destinationEnodeLow, destinationEnodeIpv4, destinationEnodePort));
    }
    function enodeAllowedIpv6(bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes16 sourceEnodeIpv6, uint16 sourceEnodePort) 
    public view returns (bool){
        bytes memory key = computeKeyIpv6(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIpv6, sourceEnodePort);
        EnodeIpv6 storage whitelistSource = whitelistIpv6[key];
        if (whitelistSource.enodeHost > 0) {
            return true;
        }
    }
    function enodeAllowedIpv4(bytes32 sourceEnodeHigh, bytes32 sourceEnodeLow, bytes4 sourceEnodeIpv4, uint16 sourceEnodePort) 
    public view returns (bool){
        bytes memory key = computeKeyIpv4(sourceEnodeHigh, sourceEnodeLow, sourceEnodeIpv4, sourceEnodePort);
        EnodeIpv4 storage whitelistSource = whitelistIpv4[key];
        if (whitelistSource.enodeHost > 0) {
            return true;
        }
    }
    function addEnodeIpv6(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIpv6, uint16 enodePort) public {
        EnodeIpv6 memory newEnode = EnodeIpv6(enodeHigh, enodeLow, enodeIpv6, enodePort);
        bytes memory key = computeKeyIpv6(enodeHigh, enodeLow, enodeIpv6, enodePort);
        whitelistIpv6[key] = newEnode;
    }
    function addEnodeIpv4(bytes32 enodeHigh, bytes32 enodeLow, bytes4 enodeIpv4, uint16 enodePort) public {
        EnodeIpv4 memory newEnode = EnodeIpv4(enodeHigh, enodeLow, enodeIpv4, enodePort);
        bytes memory key = computeKeyIpv4(enodeHigh, enodeLow, enodeIpv4, enodePort);
        whitelistIpv4[key] = newEnode;
    }
    function removeEnodeIpv6(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIpv6, uint16 enodePort) public {
        bytes memory key = computeKeyIpv6(enodeHigh, enodeLow, enodeIpv6, enodePort);
        EnodeIpv6 memory zeros = EnodeIpv6(bytes32(0), bytes32(0), bytes16(0), 0);
        whitelistIpv6[key] = zeros;
    }
    function removeEnodeIpv4(bytes32 enodeHigh, bytes32 enodeLow, bytes4 enodeIpv4, uint16 enodePort) public {
        bytes memory key = computeKeyIpv4(enodeHigh, enodeLow, enodeIpv4, enodePort);
        EnodeIpv4 memory zeros = EnodeIpv4(bytes32(0), bytes32(0), bytes4(0), 0);
        whitelistIpv4[key] = zeros;
    }
    function computeKeyIpv6(bytes32 enodeHigh, bytes32 enodeLow, bytes16 enodeIpv6, uint16 enodePort) public pure returns (bytes memory) {
        return abi.encode(enodeHigh, enodeLow, enodeIpv6, enodePort);
    }
    function computeKeyIpv4(bytes32 enodeHigh, bytes32 enodeLow, bytes4 enodeIpv4, uint16 enodePort) public pure returns (bytes memory) {
        return abi.encode(enodeHigh, enodeLow, enodeIpv4, enodePort);
    }
}
