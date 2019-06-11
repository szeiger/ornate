#!/bin/bash

# Setup:
# - Copy keys to admin/secring.asc, admin/pubring.asc
#   - Use a new key pair per repository
# - cp ~/.bintray/.credentials admin/bintray-credentials
# - tar cvf secrets.tar admin/secring.asc admin/bintray-credentials
# - travis encrypt-file secrets.tar --add
# - rm admin/bintray-credentials admin/secring.asc secrets.tar
# - Set passphrase in admin/publish-settings.sbt

set -e

if [[ "$TRAVIS_TAG" =~ ^v[0-9]+\.[0-9]+(\.[0-9]+)?(-[A-Za-z0-9-]+)? ]]; then
  echo "Going to release from tag $TRAVIS_TAG!"
  openssl aes-256-cbc -K $encrypted_950849751342_key -iv $encrypted_950849751342_iv -in secrets.tar.enc -out secrets.tar -d
  myVer=$(echo $TRAVIS_TAG | sed -e s/^v//)
  publishVersion='set every version := "'$myVer'"'
  extraTarget="publishSigned"
  cp admin/publish-settings.sbt ./
  tar xf secrets.tar
fi

sbt "$publishVersion" test makeSite scripted publishLocal $extraTarget
