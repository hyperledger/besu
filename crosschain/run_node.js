#!/usr/bin/env node
"use strict";

const cp = require('child_process');

if (process.argv.length < 3) {
    console.log("Usage: run_node.js <chainId> <nodeNumber> ['extra args for Pantheon']");
    process.exit(1);
}

const chainId = process.argv[2];
if (chainId>99) {
    console.log("Use a chainId < 99");
    process.exit(2)
}
const nodeNum = process.argv[3] || 0; // default to node 0

const resourcesPath = `crosschain/resources`;
const destPath = process.env.HOME+`/crosschain_data`;
const chainPath = `${destPath}/chain${chainId}`;
const nodePath = `${chainPath}/node${nodeNum}`;

let child = cp.execSync(`build/install/besu/bin/besu --config-file=${nodePath}/config.toml --data-path=${nodePath} --genesis-file=${chainPath}/genesis.json `+ (process.argv[4] || ""), { stdio: 'inherit' })//, { shell: false });
