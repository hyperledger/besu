---
name: Release Checklist
about: items to be completed for each release
title: ''
labels: ''
assignees: ''

---

- [ ] Confirm anything outstanding for release with other maintainers on #besu-release in Discord
- [ ] Update changelog if necessary, and merge a PR for it to main
  - [ ] Notify maintainers about updating changelog for in-flight PRs 
- [ ] Optional: for hotfixes, create a release branch and cherry-pick, e.g. `release-<version>-hotfix`
  - [ ] Optional: for hotfixes, create a PR into main from the hotfix branch to see the CI checks pass
- [ ] On the appropriate branch/commit, create a calver tag for the release candidate, format example: `24.4.0-RC1`
  - [ ] git tag 24.4.0-RC1
  - [ ] git push upstream 24.4.0-RC1
- [ ] Sign-off with team; announce the tag in #besu-release in Discord
  - [ ] Targeting this tag for the burn-in: https://github.com/hyperledger/besu/releases/tag/24.4.0-RC1
- [ ] Consensys staff start burn-in using this tag
- [ ] Seek sign off for burn-in
  - [ ] Pass? Go ahead and complete the release process
  - [ ] Fail? Put a message in #besu-release in Discord indicating the release will be aborted because it failed burn-in 
- [ ] Optional: Perform a dry run with https://github.com/consensys/protocols-release-sandbox to test the workflows
  - [ ] Sync fork
  - [ ] git checkout <sha of 24.4.0-RC1>
  - [ ] git tag 24.4.0
  - [ ] git push origin 24.4.0
  - [ ] Manually run https://github.com/Consensys/protocols-release-sandbox/actions/workflows/draft-release.yml using `main` branch and `24.4.0` tag
- [ ] Back on besu, using the same git sha as 24.4.0-RC1, create a calver tag for the FULL RELEASE, example format `24.4.0`
  - [ ] git checkout 24.4.0-RC1
  - [ ] git tag 24.4.0
  - [ ] git push upstream 24.4.0
- [ ] Manually run https://github.com/hyperledger/besu/actions/workflows/draft-release.yml using `main` branch` and the FULL RELEASE tag name, i.e. `24.4.0`. Note, this workflow should always be run from `main` branch (hotfix tags will still be released even if they were created based on another branch)
    - publishes artefacts and version-specific docker tags but does not fully publish the GitHub release so subscribers are not yet notified
- [ ] Check all draft-release workflow jobs went green
- [ ] Check binary SHAs are correct on the release page
- [ ] Update release notes in the GitHub draft release, save draft and sign-off with team
- [ ] Publish draft release ensuring it is marked as latest release (if appropriate)
    - this is now public and notifies subscribed users
    - makes the release "latest" in github
    - publishes the docker `latest` tag variants
- [ ] Create homebrew release using [update-version workflow](https://github.com/hyperledger/homebrew-besu/actions/workflows/update-version.yml)
  - If the PR has not been automatically created, create the PR manually using the created branch `update-<version>`
  - Run commands `brew tap hyperledger/besu && brew install besu` on MacOSX and verify latest version has been installed
- [ ] Delete the burn-in nodes (unless required for further analysis eg performance)
- [ ] Social announcements
