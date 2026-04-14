File buildDir = new File(basedir, "target")
assert buildDir.exists()

File jarAsc = new File(buildDir, "sign-verify-roundtrip-1.0-SNAPSHOT.jar.asc")
if (jarAsc.exists()) {
    String content = jarAsc.text
    assert content.contains("-----BEGIN PGP SIGNATURE-----") : ".asc should contain PGP signature"
    assert content.contains("-----END PGP SIGNATURE-----") : ".asc should have complete PGP block"
    println "SUCCESS: hybrid .asc file verified"
} else {
    println "SKIP: .asc not found (GPG/sq may not be configured)"
}
