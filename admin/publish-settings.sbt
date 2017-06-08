pgpPublicRing := file("admin/pubring.asc")
pgpSecretRing := file("admin/secring.asc")
pgpPassphrase := Some("12345".toArray)
bintrayCredentialsFile in ThisBuild := file("admin/bintray-credentials")
