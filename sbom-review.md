# SBOM Review: Three-Way Comparison

**Distribution**: WildFly 42.0.0.Beta1-SNAPSHOT (full, no layer filter)  
**Date**: 2026-08-20  

## Three-Way Comparison

| Metric | Fat | Thin | SBOM-only |
|--------|-----|------|-----------|
| Total components | 796 | 796 | 796 |
| Maven components | 793 | 793 | 793 |
| Shaded (pkg:generic) | 3 | 3 | 3 |
| npm (nested under HAL) | 161 | 161 | 161 |
| With occurrences | 796 | 13 | 0 |
| With identity evidence | 793 | 793 | 793 |
| With SHA-256 hashes | 793 | 793 | 1 |
| With licenses | 0 | 0 | 0 |
| Dependency entries | 798 | 798 | 794 |

### Coverage delta

| | Count | Notes |
|---|---|---|
| In fat but not thin | 0 | All three modes match |
| In fat but not SBOM-only | 0 | All three modes match |
| In thin but not fat | 0 | |
| In SBOM-only but not fat | 0 | |

## What works well

- **All three modes produce identical component sets**: 796 components (793 Maven, 3 shaded, 161 npm) across fat, thin, and SBOM-only
- **Fat distribution has 100% occurrence coverage**: all 796 components have occurrence paths
- **Provisioning tool exclusion**: `wildfly-config-gen` correctly excluded from the SBOM via `recordToolDependency()` — it is a build-time artifact, not a distribution component
- **Shaded JAR detection**: 3 shaded JARs (`wildfly-elytron-tool`, `jboss-client`, `jboss-cli-client`) with `pkg:generic` PURLs, occurrence paths, and nested dependencies — consistent across all three modes
- **Embedded SBOM detection**: HAL console's `npm-bom.cdx.json` detected and merged — 161 npm components with licenses, versions, and `pkg:npm` PURLs — consistent across all three modes
- **PURL quality**: all well-formed, no missing versions or encoding issues
- **Metadata**: timestamp, main component, tool info, serial number present in all three
- **Dependency graph**: 798 entries in fat/thin, 794 in SBOM-only
- **Hashes**: SHA-256 computed from resolved JARs (793 in fat and thin, 1 in SBOM-only)
- **Identity evidence**: MANIFEST_ANALYSIS identity on all Maven components
- **Thin mode hash coverage**: thin SBOMs now have SHA-256 hashes for all 793 Maven components, matching fat — thin SBOMs are as complete as fat for artifact verification

### Thin mode occurrences (13)

Components with physical presence in thin distributions:

| Component | Location |
|-----------|----------|
| jboss-modules | `jboss-modules.jar` |
| wildfly-launcher | `bin/launcher.jar` |
| wildfly-ee-feature-pack-product-conf | `version.txt` |
| wildfly-elytron-tool | `bin/wildfly-elytron-tool.jar` (shaded) |
| jboss-client | `bin/client/jboss-client.jar` (shaded) |
| jboss-cli-client | `bin/client/jboss-cli-client.jar` (shaded) |
| resteasy-spring | `modules/.../bundled/resteasy-spring-jar/resteasy-spring-3.2.0.Final.jar` |
| activemq-artemis-native | `modules/.../org/apache/activemq/artemis/journal/main` |
| netty-transport-native-epoll (x86_64) | `modules/.../io/netty/netty-transport-native-epoll/main/lib` |
| netty-transport-native-epoll (aarch_64) | `modules/.../io/netty/netty-transport-native-epoll/main/lib` |
| netty-transport-native-kqueue (x86_64) | `modules/.../io/netty/netty-transport-native-kqueue/main/lib` |
| netty-transport-native-kqueue (aarch_64) | `modules/.../io/netty/netty-transport-native-kqueue/main/lib` |
| wildfly-openssl-all | `modules/.../org/wildfly/openssl/main/lib` |

## Remaining issues

### 1. No license coverage (all three modes)

Zero Maven components have license data. `BomBuilder.addMavenArtifact()` receives `null` for `LicenseChoice`. Adding license resolution would require reading effective POM models during provisioning.

### 2. Main component identity

The main component is `org.wildfly.galleon-plugins/wildfly-galleon-plugins` (the provisioning tool). Should be the WildFly distribution identity (e.g., `org.wildfly/wildfly-galleon-pack@42.0.0.Beta1-SNAPSHOT`).

### 3. Tool info

Shows `assembly-sbom-core@0.1.3-SNAPSHOT` (from BomBuilder). Should show `wildfly-galleon-plugins` as the generating tool.

### 4. SBOM-only has 1 hash, should have 0 or all

`hal-console@3.7.21.Final?classifier=resources` has a SHA-256 hash in SBOM-only mode. This artifact is resolved to extract the embedded npm SBOM, and its hash gets recorded as a side effect. SBOM-only mode doesn't resolve other artifacts, so hashes should be either 0 (no resolution) or all (resolve for hashing).

### 5. SBOM-only dependency count (794 vs 798)

SBOM-only has 4 fewer dependency entries than fat/thin. Minor discrepancy likely from dependency graph edges involving artifacts resolved during provisioning that SBOM-only skips.

## Resolved issues (from prior review)

### Thin/SBOM-only missing 114-116 artifacts — FIXED

All three modes now produce identical 796-component sets. The recording gap in thin and SBOM-only install paths has been closed.

### Provisioning tool in SBOM — FIXED

`wildfly-config-gen` is no longer in the SBOM. The new `ArtifactRecorder.recordToolDependency()` method allows provisioning tools to be recorded in `artifacts.txt` while being excluded from the SBOM.

### Fat distribution missing occurrences — FIXED

Fat now has 796/796 components with occurrences (100%), up from 791/795. The single component previously without occurrences (`wildfly-config-gen`) has been excluded from the SBOM.

## Next steps

1. **Fix main component identity** — use feature pack coordinates instead of tool coordinates
2. **Fix tool info** — report `wildfly-galleon-plugins` as the generating tool
3. **Add license resolution** — read licenses from effective POM during provisioning
4. **Fix SBOM-only hash** — ensure no hash computation when artifacts aren't resolved, or resolve all for hashing
