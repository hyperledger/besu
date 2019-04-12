#!/bin/sh -e

#
# Creates a new branch from a release tag, updating the gradle.properties version number.
#
# Assumed format of the version number: major.minor.bugfix
# Assumed location of the version number: gradle.properties
#
# NOTE: Any additional markers after the bugfix (i.e. -RC2) will be lost.

if [ -z "${1}" ]
then
  echo "[ERROR] Missing required first argument of tag name"
  exit 1
fi

if [ -z "${2}" ]
then
  echo "[ERROR] Missing required second argument of branch name"
  exit 1
fi


tag_name=${1}
branch_name=${2}

# Locally create a branch from the tag
git checkout -b ${branch_name} ${tag_name}

# extract the version from gradle.properties
tag_version=$(grep "^version" gradle.properties | cut -d'=' -f2)

# Assuming semantic naming format of major.minor.bugfix[-RC|-SNAPSHOT|-other]
major_version=$(echo ${tag_version} | cut -d'.' -f1)
minor_version=$(echo ${tag_version} | cut -d'.' -f2)
bugfix_version=$(echo ${tag_version} | cut -d'.' -f3 | cut -d'-' -f1)

# Increment the bugfix version that goes on the new branch
branch_bugfix_version=$((${bugfix_version}+1))

# Reconstruct the version number for the branch
branch_version="${major_version}.${minor_version}.${branch_bugfix_version}-SNAPSHOT"

# Change the local gradle.properties version to branch version
sed -i "s/${tag_version}/${branch_version}/g" gradle.properties

# Update the Jenkins job default branch name
sed -i "s#defaultValue: 'master'#defaultValue: '${branch_name}'#g" Jenkinsfile.release

git commit -am"[Release Script] Updating version to ${branch_version}"
git push --set-upstream origin ${branch_name}
