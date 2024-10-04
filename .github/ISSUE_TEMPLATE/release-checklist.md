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
- [ ] On the appropriate branch/commit, create a calver tag for the release candidate, format example: `24.4.0-RC2`
  - [ ] git tag 24.4.0-RC2
  - [ ] git push upstream 24.4.0-RC2
- [ ] Sign-off with team; announce the tag in #besu-release in Discord
  - [ ] Targeting this tag for the burn-in: https://github.com/hyperledger/besu/releases/tag/24.4.0-RC2
- [ ] Consensys staff start burn-in using this tag
- [ ] Seek sign off for burn-in
  - [ ] Pass? Go ahead and complete the release process
  - [ ] Fail? Put a message in #besu-release in Discord indicating the release will be aborted because it failed burn-in 
- [ ] Using the same git sha, create a calver tag for the FULL RELEASE, example format `24.4.0`
- [ ] Using the FULL RELEASE tag, create a release in github to trigger the workflows. Once published:
    - this is now public and notifies subscribed users
    - makes the release "latest" in github
    - publishes artefacts and version-specific docker tags
    - publishes the docker `latest` tag variants
- [ ] Check binary SHAs are correct on the release page
- [ ] Check "Container Verify" GitHub workflow has run successfully
- [ ] Update the besu-docs version [update-version workflow](https://github.com/hyperledger/besu-docs/actions/workflows/update-version.yml)
  - If the PR has not been automatically created, create the PR manually using the created branch `besu-version-<version>`
- [ ] Create homebrew release using [update-version workflow](https://github.com/hyperledger/homebrew-besu/actions/workflows/update-version.yml)
  - If the PR has not been automatically created, create the PR manually using the created branch `update-<version>`
  - Run commands `brew tap hyperledger/besu && brew install besu` on MacOSX and verify latest version has been installed
- [ ] Delete the burn-in nodes (unless required for further analysis eg performance)
- [ ] Social announcements
