package org.hyperledger

def rootPath = System.getProperty("checkSpdxHeader.rootPath")
def spdxHeader = System.getProperty("checkSpdxHeader.spdxHeader")
def filesRegex = System.getProperty("checkSpdxHeader.filesRegex")
def excludeRegex = System.getProperty("checkSpdxHeader.excludeRegex")
def filesWithoutHeader = []

new File(rootPath).traverse(
        type: groovy.io.FileType.FILES,
        nameFilter: ~/${filesRegex}/,
        excludeFilter: ~/${excludeRegex}/
) {
    f ->
        if (!f.getText().contains(spdxHeader)) {
            filesWithoutHeader.add(f)
        }
}

if (!filesWithoutHeader.isEmpty()){
    throw new Exception("Files without headers: " + filesWithoutHeader.join('\n'))
}

