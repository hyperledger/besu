// Copyright ConsenSys AG.
// SPDX-License-Identifier: Apache-2.0
const TestPermissioning = artifacts.require('SimpleNodePermissioning.sol');
var proxy;

var node1High = "0x9bd359fdc3a2ed5df436c3d8914b1532740128929892092b7fcb320c1b62f375";
var node1Low = "0x892092b7fcb320c1b62f3759bd359fdc3a2ed5df436c3d8914b1532740128929";
var node1Host = "0x9bd359fdc3a2ed5df436c3d8914b1532";
var node1Port = 30303;

var node2High = "0x892092b7fcb320c1b62f3759bd359fdc3a2ed5df436c3d8914b1532740128929";
var node2Low = "0x892092b7fcb320c1b62f3759bd359fdc3a2ed5df436c3d8914b1532740128929";
var node2Host = "0x596c3d8914b1532fdc3a2ed5df439bd3";
var node2Port = 30304;

contract('Permissioning: Nodes', () => {
  it('Should NOT permit any node when none have been added', async () => {
    proxy = await TestPermissioning.new();
    let permitted = await proxy.enodeAllowed(node1High, node1Low, node1Host, node1Port);
    assert.equal(permitted, false, 'expected node NOT permitted');
  });

  it('Should compute key', async () => {
    let key1 = await proxy.computeKey(node1High, node1Low, node1Host);
    let key2 = await proxy.computeKey(node1High, node1Low, node1Host);
    assert.equal(key1, key2, "computed keys should be the same");

    let key3 = await proxy.computeKey(node2High, node2Low, node2Host);
    assert(key3 != key2, "computed keys for different nodes should be different");
  });

  it('Should add a node to the whitelist and then permit that node', async () => {
    await proxy.addEnode(node1High, node1Low, node1Host, node1Port);
    let permitted = await proxy.enodeAllowed(node1High, node1Low, node1Host, node1Port);
    assert.equal(permitted, true, 'expected node added to be permitted');

    // await another
    await proxy.addEnode(node2High, node2Low, node2Host, node2Port);
    permitted = await proxy.enodeAllowed(node2High, node2Low, node2Host, node2Port);
    assert.equal(permitted, true, 'expected node 2 added to be permitted');

    // first one still permitted
    permitted = await proxy.enodeAllowed(node1High, node1Low, node1Host, node1Port);
    assert.equal(permitted, true, 'expected node 1 added to be permitted');
  });

  it('Should allow a connection between 2 added nodes', async () => {
    let permitted = await proxy.connectionAllowed(node1High, node1Low, node1Host, node1Port, node2High, node2Low, node2Host, node2Port);
    assert.equal(permitted, '0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 'expected 2 added nodes to work as source <> destination');
  });

  it('Should remove a node from the whitelist and then NOT permit that node', async () => {
    await proxy.removeEnode(node1High, node1Low, node1Host, node1Port);
    let permitted = await proxy.enodeAllowed(node1High, node1Low, node1Host, node1Port);
    assert.equal(permitted, false, 'expected removed node NOT permitted');

    permitted = await proxy.connectionAllowed(node1High, node1Low, node1Host, node1Port, node2High, node2Low, node2Host, node2Port);
    assert.equal(permitted, '0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 'expected source disallowed since it was removed');
  });
});
