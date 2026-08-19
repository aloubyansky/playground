# SBOM Review: Three-Way Comparison

**Distribution**: WildFly 42.0.0.Beta1-SNAPSHOT (full, no layer filter)  
**Date**: 2026-08-19  

## Three-Way Comparison

| Metric | Fat | Thin | SBOM-only |
|--------|-----|------|-----------|
| Total components | 795 | 678 | 676 |
| Maven components | 792 | 675 | 673 |
| Shaded (pkg:generic) | 3 | 3 | 3 |
| npm (nested under HAL) | 161 | 161 | 161 |
| With occurrences | 791 | 7 | 0 |
| With identity evidence | 792 | 675 | 673 |
| With SHA-256 hashes | 792 | 675 | 1 |
| With licenses | 0 | 0 | 0 |
| Dependency entries | 799 | 799 | 794 |

### Coverage delta

| | Count | Notes |
|---|---|---|
| In fat but not thin | 114 | Recording gap in thin install path |
| In fat but not SBOM-only | 116 | Same gap + 2 artifacts only found during provisioning |
| In thin but not fat | 0 | Fat is a strict superset |
| In SBOM-only but not fat | 0 | Fat is a strict superset |
| In module.xml but not fat | 0 | Fat SBOM has complete coverage |

## What works well

- **Fat distribution has 100% coverage**: every artifact referenced in module.xml is present in the SBOM
- **Shaded JAR detection**: 3 shaded JARs (`wildfly-elytron-tool`, `jboss-client`, `jboss-cli-client`) with `pkg:generic` PURLs, occurrence paths, and nested dependencies (2, 105, 62 respectively) — consistent across all three modes
- **Embedded SBOM detection**: HAL console's `npm-bom.cdx.json` detected and merged — 161 npm components with licenses, versions, and `pkg:npm` PURLs — consistent across all three modes
- **PURL quality**: all well-formed, no missing versions or encoding issues
- **Metadata**: timestamp, main component, tool info, serial number present in all three
- **Dependency graph**: ~799 entries in all three modes
- **Hashes**: SHA-256 computed from resolved JARs (792 in fat, 675 in thin, 1 in SBOM-only)
- **Identity evidence**: MANIFEST_ANALYSIS identity on all Maven components

## Issues

### 1. Thin/SBOM-only missing 114-116 artifacts

114 artifacts present in the fat SBOM are absent from thin (116 from SBOM-only). These include Guava, Netty (12 modules), SmallRye Common (5 modules), and others — spread across 63 module directories.

The fat install path records these through `SimpleArtifactInstaller.installArtifactFat()` which calls `artifactRecorder.record()`. The thin path (`installArtifactThin()`) also calls `record()`, so the gap is likely in a different code path — possibly `AbstractArtifactInstaller` base class methods, bulk resolution, or artifacts installed via `processModuleTemplate()` without going through the installer.

### 2. No license coverage (all three modes)

Zero Maven components have license data. `BomBuilder.addMavenArtifact()` receives `null` for `LicenseChoice`. Adding license resolution would require reading effective POM models during provisioning.

### 3. Occurrence evidence varies by mode

- **Fat**: 791 of 795 components have occurrences (distribution file paths) — expected
- **Thin**: 7 have occurrences (only physically present files: `jboss-modules.jar`, launcher, shaded JARs, `version.txt`, one bundled JAR) — expected for thin
- **SBOM-only**: 0 occurrences — expected (nothing installed)

### 4. Main component identity

The main component is `org.wildfly.galleon-plugins/wildfly-galleon-plugins` (the provisioning tool). Should be the WildFly distribution identity.

### 5. Tool info

Shows `assembly-sbom-core@0.1.3-SNAPSHOT` (from BomBuilder). Should show `wildfly-galleon-plugins` as the generating tool.

### 6. SBOM-only has 1 hash, should have 0 or all

SBOM-only mode doesn't resolve artifacts, so hashes should be 0. The 1 hash is likely from a shaded component's target path that happened to exist.

## Next steps

1. **Investigate the thin recording gap** (114 artifacts) — trace which install path skips `ArtifactRecorder` calls
2. **Fix main component identity** — use feature pack coordinates instead of tool coordinates
3. **Fix tool info** — report `wildfly-galleon-plugins` as the generating tool
4. **Add license resolution** — read licenses from effective POM during provisioning
5. **Fix SBOM-only hash** — ensure no hash computation when artifacts aren't resolved
