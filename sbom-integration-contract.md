# SBOM Integration Contract

A contract between SBOM generators, product teams, and vulnerability scanners for SBOM-based software composition analysis.

**Status:** Proposal — based on working implementations of SBOM generators, not yet a finalized standard.

**Specification:** [CycloneDX](https://cyclonedx.org/) 1.6. CycloneDX was chosen for initial prototyping due to its lighter-weight tooling and easier integration in the Java and NPM ecosystems. [SPDX](https://spdx.dev/) support is planned for a future revision of this document — the concepts in this contract (placement, naming, product identification, component attribution) are spec-agnostic and will be covered for both CycloneDX and SPDX.

## Table of Contents

- [1. Introduction](#1-introduction)
- [2. Why Prefer SBOMs Over Filesystem Scanning](#2-why-prefer-sboms-over-filesystem-scanning)
- [3. SBOM Placement and Naming](#3-sbom-placement-and-naming)
- [4. SBOM Content](#4-sbom-content)
- [5. Product Information Models](#5-product-information-models)
- [6. Reference Implementations](#6-reference-implementations)

## 1. Introduction

This document defines how CycloneDX SBOMs should be generated, bundled with products, and consumed by vulnerability scanners. It is an integration contract between three parties, each with distinct obligations:

| Role | Who | Obligation |
|------|-----|------------|
| **Generator** | SBOM tool developers | Produce compliant SBOMs with correct structure, naming, placement, and component identification. Support product metadata configuration. |
| **Product Team** | Engineers configuring SBOM generation for a product build | Configure generators with accurate product identity (CPE, supplier, version). Choose output mode and ensure SBOMs are included in distribution artifacts. |
| **Scanner** | Vulnerability scanner developers and integrators | Discover SBOMs, extract product and component information, match against VEX/CVE sources, and follow external SBOM references when present. |

Throughout this document, obligations are annotated with RFC 2119 keywords (**MUST**, **SHOULD**, **MAY**) and tagged with the responsible role.

This proposal is grounded in working implementations:

- [maven-assembly-sbom](https://github.com/cyberstamp/maven-assembly-sbom) — content-based SBOM generator for Maven distribution archives
- [Quarkus CycloneDX extension](https://quarkus.io/guides/cyclonedx) — SBOM embedded in application JARs and native executables
- [WildFly/EAP Galleon plugin](https://github.com/aloubyansky/galleon-plugins/tree/sbom-cdx) — SBOM generated during server provisioning

Example project integrations:

- [Apache Artemis distribution](https://github.com/apache/artemis/pull/6600)
- [Keycloak distribution](https://github.com/keycloak/keycloak/pull/51743)
- [WildFly Galleon plugins](https://github.com/wildfly/galleon-plugins/pull/372)

## 2. Why Prefer SBOMs Over Filesystem Scanning

Vulnerability scanners that rely on filesystem inspection to identify products and components face fundamental limitations that SBOMs solve.

### Product identity is not reliably derivable from filesystem contents

A scanner examining an installed distribution cannot always determine the product name, version, or CPE. There is no universal convention for exposing this information on the filesystem, and different products use different mechanisms (version files, manifest entries, directory names) — or none at all. SBOMs carry this information explicitly in a standard format.

### Packaging obscures component identity

Modern build and packaging techniques make it difficult or impossible to identify individual components by examining files:

- **Shaded/fat JARs** — extremely common in the Java ecosystem. Multiple Maven artifacts are repackaged into a single JAR, often with relocated package names. The original GAV (groupId, artifactId, version) coordinates, directory structure, and manifest entries are often lost.

- **Tree-shaking and minification** — JavaScript and frontend build tools remove unused code and compress what remains. Original package names, version strings, and file boundaries are lost.

- **GraalVM native images** — a single native binary compiled from Maven JARs, NPM packages, and other dependencies. No individual component files exist in the output.

### SBOMs are authoritative

An SBOM generated at build time has access to the full dependency resolution context — the dependency tree, resolved versions, license information, and package identifiers. This is information that a scanner cannot reconstruct after the fact. When an SBOM is present, it is the authoritative source of product and component information, and scanners should prefer it over their own heuristics.

## 3. SBOM Placement and Naming

This section defines where SBOMs are placed and how they are named, covering the obligations of generators (who produce and place them), product teams (who configure placement), and scanners (who discover them).

SBOMs MAY be GZip-compressed to reduce size. When compressed, the `*.gz` suffix is appended to the standard filename (e.g., `sbom.cdx.json.gz`). Scanners must be prepared to handle both compressed and uncompressed SBOMs in all placement locations described below.

### 3.1 Naming Convention

**Generator** MUST use the `*.cdx.json` filename pattern for JSON-format CycloneDX SBOMs (or `*.cdx.xml` for XML format). If GZip-compressed, the file MUST use `*.cdx.json.gz` (or `*.cdx.xml.gz`).

**Generator** SHOULD default to `sbom.cdx.json` as the canonical filename.

**Scanner** MUST recognize files matching `*.cdx.json` and `*.cdx.xml` as CycloneDX SBOM files. **Scanner** MUST also recognize `*.cdx.json.gz` and `*.cdx.xml.gz` as GZip-compressed SBOMs and decompress them before parsing.

**Scanner** SHOULD prefer `sbom.cdx.json` (or `sbom.cdx.json.gz`) when multiple SBOM files are found in the same location. If both a compressed and uncompressed version of the same SBOM exist (e.g., `sbom.cdx.json` and `sbom.cdx.json.gz`), the scanner SHOULD prefer the uncompressed version.

### 3.2 Filesystem Placement

For directory-based distributions (e.g., an application server unpacked from a ZIP/tar archive), the SBOM is placed at the distribution root.

**Generator/Product Team** MUST place the SBOM at the root of the distribution directory.

**Scanner** MUST check the distribution root for `*.cdx.json` files.

For JARs with external SBOMs, the SBOM is placed next to the JAR:

**Generator** MUST name the external SBOM `<jar-filename>.cdx.json` (e.g., `myapp-1.0.jar.cdx.json` next to `myapp-1.0.jar`).

**Scanner** SHOULD check for `<filename>.cdx.json` next to JAR files.

#### Example: distribution filesystem layout

```
keycloak-26.0.0/
  sbom.cdx.json                         <-- distribution SBOM at root
  bin/
    kc.sh
  lib/
    lib/
      io.quarkus.quarkus-core-3.15.1.jar
      org.keycloak.keycloak-server-spi-26.0.0.jar
      ...
  providers/
```

```
artemis-2.40.0/
  sbom.cdx.json                         <-- distribution SBOM at root
  bin/
    artemis
    artemis.cmd
  lib/
    artemis-core-client-2.40.0.jar
    netty-buffer-4.1.115.Final.jar
    ...
  web/
    console.war
```

### 3.3 Embedded in Artifacts

SBOMs can be embedded inside JARs or native executables, allowing them to travel with the artifact.

#### JARs

**Generator** MUST place embedded SBOMs under the `META-INF/` directory using the `*.cdx.json` naming convention.

**Generator** MAY GZip-compress embedded SBOMs. If compressed, the file MUST use the `*.cdx.json.gz` extension. Quarkus compresses embedded SBOMs by default.

**Scanner** MUST open JARs as ZIP archives and check `META-INF/` for CycloneDX SBOM files (both `*.cdx.json` and the compressed `*.cdx.json.gz` form, per [3.1](#31-naming-convention)).

```
myapp-1.0-runner.jar
  META-INF/
    sbom.cdx.json.gz                    <-- GZip-compressed SBOM
  com/
    example/
      ...
```

#### GraalVM Native Images

GraalVM embeds a gzip-compressed CycloneDX SBOM in native images by default (via `--enable-sbom`).

The binary exports two symbols:

- `sbom` — the start address of the compressed SBOM data
- `sbom_length` — the size of the compressed data in bytes

**Scanner** SHOULD locate the `sbom` and `sbom_length` exported symbols in the native binary, read `sbom_length` bytes starting at the `sbom` address, and GZip-decompress the result to obtain the CycloneDX JSON. The symbols are exported in the platform's native format — ELF on Linux, Mach-O on macOS, PE on Windows — so scanners need to handle the symbol table format appropriate to the target platform.

See the [GraalVM SBOM documentation](https://www.graalvm.org/jdk25/security-guide/native-image/sbom/) for details.

### 3.4 OCI Attestations

SBOMs may be attached to container images as attestations using tools like [cosign](https://docs.sigstore.dev/signing/signing_with_containers/) or [in-toto](https://in-toto.io/). This mechanism is well-suited for container-native deployment pipelines but is outside the primary scope of this contract. Scanners operating in container environments should consult the respective attestation specifications.

### 3.5 Runtime Endpoint

[RFC 9472](https://datatracker.ietf.org/doc/rfc9472/) defines a well-known URI for serving SBOMs from running systems:

```
GET /.well-known/sbom
```

**Product Team** MAY configure applications to expose their SBOM at this endpoint over HTTPS.

This mechanism is primarily relevant to runtime monitoring tools rather than filesystem scanners. The response format is indicated by the `Content-Type` header and is format-neutral (supports CycloneDX, SPDX, and others).


## 4. SBOM Content

This section defines what goes inside an SBOM and how each role interacts with its contents.

### 4.1 Product Identification

Product identity is carried in the `metadata.component` field of the CycloneDX SBOM. This is the top-level component that represents the product itself.

**Product Team** MUST provide at minimum: CPE, product name, and version. **Product Team** SHOULD also provide: supplier, manufacturer, and purl.

**Generator** MUST populate `metadata.component` with the product information when configured by the product team.

**Scanner** SHOULD extract `metadata.component` as the primary source of product identity. The CPE is the key field for matching against VEX and CVE sources.

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.6",
  "metadata": {
    "component": {
      "type": "application",
      "name": "keycloak",
      "version": "26.0.0",
      "group": "org.keycloak",
      "purl": "pkg:maven/org.keycloak/keycloak-distribution@26.0.0?type=zip",
      "cpe": "cpe:2.3:a:redhat:keycloak:26.0.0:*:*:*:*:*:*:*",
      "supplier": {
        "name": "Red Hat",
        "url": ["https://www.redhat.com"]
      }
    }
  },
  "components": [ ... ]
}
```

### 4.2 Component Inventory

The `components` array lists every identifiable component in the distribution. Each component carries a Package URL (purl) as its primary identifier.

**Generator** MUST populate the `components` array with a purl for each identified component.

**Generator** SHOULD include `evidence/occurrences` entries showing where each component appears in the distribution.

**Scanner** MUST extract component purls. **Scanner** SHOULD match both the product CPE and component purls against VEX and CVE data sources.

Occurrence locations use the same archive URI syntax as external references: a path relative to the distribution root, with `!` separating each archive boundary from the entry path within it (e.g., `web/console.war!/WEB-INF/lib/resources.jar`).

```json
{
  "components": [
    {
      "type": "library",
      "group": "io.netty",
      "name": "netty-buffer",
      "version": "4.1.115.Final",
      "purl": "pkg:maven/io.netty/netty-buffer@4.1.115.Final?type=jar",
      "licenses": [
        { "license": { "id": "Apache-2.0" } }
      ],
      "hashes": [
        { "alg": "SHA-256", "content": "a1b2c3d4..." }
      ],
      "evidence": {
        "occurrences": [
          { "location": "lib/netty-buffer-4.1.115.Final.jar" }
        ]
      }
    },
    {
      "type": "library",
      "name": "lodash",
      "version": "4.17.21",
      "purl": "pkg:npm/lodash@4.17.21",
      "evidence": {
        "occurrences": [
          { "location": "web/console.war!/WEB-INF/lib/resources.jar" }
        ]
      }
    }
  ]
}
```

### 4.3 External SBOM References

SBOMs are typically self-contained — all product and component information is in a single file. This is the expected case, and scanners can treat it as the norm.

However, in some cases a component within the SBOM may reference a separate, external SBOM. This is done using a CycloneDX `externalReference` of type `bom` on the component, with the URL pointing to the location of the referenced SBOM file.

In this contract, external references are **local filesystem references** — either relative paths within the distribution, or paths into embedded archives. Remote URLs (e.g., `https://...`) are out of scope: scanners typically operate without network access to external repositories, and remote references cannot be reliably resolved at scan time.

#### Archive URI syntax

Both `externalReference` URLs and `evidence/occurrences` locations use the same archive URI syntax:

```
<path-relative-to-distribution-root>[!/<entry-path-inside-archive>]*
```

The `!` character separates each archive from the entry path within it. This nesting can repeat for archives within archives:

```
web/console.war!/WEB-INF/lib/nested.jar!/META-INF/sbom.cdx.json
```

This means: open `web/console.war` as a ZIP, then open the `WEB-INF/lib/nested.jar` entry within it as a ZIP, then read `META-INF/sbom.cdx.json` from that inner archive.

**Generator** MAY add `externalReference` entries of type `bom` to components that carry their own SBOM (e.g., a WAR or JAR inside the distribution that has an embedded SBOM).

**Generator** MUST use the archive URI syntax described above for all local references. The path MUST be relative to the distribution root.

**Generator** MUST NOT use remote URLs (e.g., `https://...`) as external reference URLs. Remote references cannot be reliably resolved by scanners operating without network access.

**Scanner** SHOULD be prepared to follow local references. When an `externalReference` of type `bom` is found on a component, the scanner should resolve it using the archive URI syntax: split on `!`, treat each segment as an archive to open, and read the final entry from the innermost archive. The components in the referenced SBOM belong to the parent component that carries the reference.

**Scanner** MAY skip or log a warning for any `externalReference` of type `bom` whose URL is a remote URL, since these cannot be resolved in a typical scan environment.

```json
{
  "type": "library",
  "group": "com.example",
  "name": "admin-console",
  "version": "2.0.0",
  "purl": "pkg:maven/com.example/admin-console@2.0.0?type=war",
  "externalReferences": [
    {
      "type": "bom",
      "url": "web/admin-console.war!/META-INF/sbom.cdx.json"
    }
  ]
}
```

In this example, the distribution's main SBOM lists `admin-console.war` as a component and points to its embedded SBOM. A scanner following the reference would open `web/admin-console.war` as a ZIP archive and read `META-INF/sbom.cdx.json` from inside it to discover the WAR's own components.

### 4.4 SBOMs Without Product Information

An SBOM that lacks `metadata.component` with CPE or other product identity information is a component-level SBOM. These are commonly produced by build plugins (e.g., `cyclonedx-maven-plugin`) for individual modules or libraries.

**Scanner** cannot perform CPE-based VEX matching against component-level SBOMs, since they carry no product identity. However, scanners MAY still use them for component recognition (e.g., matching files on disk by hash against components listed in the SBOM).

Product SBOMs (with CPE in `metadata.component`) are authoritative sources of product identity and are required for VEX matching. Component-level SBOMs are supplementary — useful for component identification or when referenced by a product SBOM via an external reference (see [4.3](#43-external-sbom-references)), but not independently actionable for product-level vulnerability matching.

## 5. Product Information Models

Different runtime architectures require different approaches to product identification. This section describes the two primary models and a proposed mechanism for the more complex case.

### 5.1 Single-Product Runtimes

Products like Keycloak and Apache Artemis are single-product distributions: one SBOM describes one product, and all components belong to it. This is [4.1](#41-product-identification) applied directly — the **Product Team** supplies the product's CPE, name, version, and supplier in `metadata.component`, and every entry in the `components` array belongs to that product (see [4.1](#41-product-identification) and [4.2](#42-component-inventory) for the field structures).

This is the straightforward case: scanners match the single product CPE against VEX data and apply the results to all components in the SBOM.

### 5.2 Multi-Product Runtimes

Some runtimes combine components from multiple products. A Quarkus application, for example, bundles Quarkus framework components (which have their own CPE and VEX stream), the customer's application code, and possibly third-party libraries.

In these cases, the top-level `metadata.component` typically identifies the deployed application or the overall runtime. However, a scanner needs to know which components belong to which product in order to match them against the correct VEX/CVE sources.

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.6",
  "metadata": {
    "component": {
      "type": "application",
      "name": "my-quarkus-app",
      "version": "1.0.0",
      "group": "com.example",
      "purl": "pkg:maven/com.example/my-quarkus-app@1.0.0"
    }
  },
  "components": [
    {
      "type": "library",
      "group": "io.quarkus",
      "name": "quarkus-core",
      "version": "3.15.1",
      "purl": "pkg:maven/io.quarkus/quarkus-core@3.15.1?type=jar"
    },
    {
      "type": "library",
      "group": "com.example",
      "name": "my-service",
      "version": "1.0.0",
      "purl": "pkg:maven/com.example/my-service@1.0.0?type=jar"
    },
    {
      "type": "library",
      "group": "com.fasterxml.jackson.core",
      "name": "jackson-databind",
      "version": "2.18.0",
      "purl": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.18.0?type=jar"
    }
  ]
}
```

In this example, `quarkus-core` belongs to the Quarkus product (which has its own CPE), `my-service` belongs to the customer's application, and `jackson-databind` is a third-party library that may be covered by multiple VEX streams. Without attribution, a scanner cannot route these components to the correct vulnerability data sources.

### 5.3 Component-to-Product Attribution

> **This section describes a feature in active implementation.** The [Quarkus CycloneDX extension](https://quarkus.io/guides/cyclonedx) implements this mechanism. Scanner teams can plan their architecture around this design.

#### Problem

In multi-product runtimes ([5.2](#52-multi-product-runtimes)), a scanner needs to associate each component with the product(s) it belongs to. Without this association, the scanner cannot determine which VEX stream or CPE applies to a given component.

#### Mechanism: dependency-graph `provides` edges

Component-to-product attribution uses the CycloneDX dependency graph. Each attributed product is modeled as a `framework` component carrying its CPE, and component membership is expressed through `provides` edges in the `dependencies` array. This adapts [Red Hat's published product SBOM pattern](https://github.com/RedHatProductSecurity/security-data-guidelines/tree/main/sbom/examples/product) to the application SBOM context, reuses native CycloneDX fields, and cleanly handles components shared across multiple products.

**Product taxonomy.** Two distinct "product" notions must not be conflated:

1. **The application** — a product in its own right (the customer's), and the SBOM subject: it is the `metadata.component` / root. It carries no upstream product CPE.
2. **Upstream products** — Red Hat products consumed by the application (e.g., Quarkus, Camel Quarkus), each with a CPE, whose artifacts are attributed to them. These are never the SBOM root.

**Structure.** Unlike Red Hat's standalone *product* SBOM (where the single product is the root and provides everything), here the root is the application and each member product is an additional, non-root `framework` component:

- Red Hat CPEs attach only to the member product components — never to the app root, and never to individual `pkg:maven` artifacts.
- The app relates to its dependencies via `dependsOn` (the normal Maven dependency graph).
- The app also `dependsOn` each member product component, marked `scope: excluded` so the member node is graph-reachable but flagged as a build-time/tooling construct, not a runtime deliverable. Runtime-closure computation is unaffected because the attributed artifacts remain reachable via the direct `app → dependsOn → artifact` edges.
- Each member product `provides` the subset of `pkg:maven` artifacts attributed to it. A shared artifact simply appears in multiple members' `provides` lists — one canonical component/bom-ref, no duplication.

```
app (root, metadata.component — the customer's product)
 ├─ dependsOn ─▶ pkg:maven artifacts            (scope: required — runtime graph)
 └─ dependsOn ─▶ member product (CPE, framework, scope: excluded)
                     └─ provides ─▶ the attributed subset of those pkg:maven artifacts
```

```json
{
  "components": [
    {
      "type": "framework",
      "bom-ref": "pkg:maven/com.redhat.quarkus.platform/quarkus-camel-bom@3.15.1.redhat-00001?type=pom",
      "group": "com.redhat.quarkus.platform",
      "name": "quarkus-camel-bom",
      "version": "3.15.1.redhat-00001",
      "purl": "pkg:maven/com.redhat.quarkus.platform/quarkus-camel-bom@3.15.1.redhat-00001?type=pom",
      "cpe": "cpe:2.3:a:redhat:camel_quarkus:3.15.1:*:*:*:*:*:*:*",
      "scope": "excluded",
      "evidence": {
        "identity": [
          { "field": "cpe", "concludedValue": "cpe:2.3:a:redhat:camel_quarkus:3.15.1:*:*:*:*:*:*:*" }
        ]
      }
    }
  ],
  "dependencies": [
    {
      "ref": "pkg:maven/com.example/my-quarkus-app@1.0.0",
      "dependsOn": [
        "pkg:maven/org.apache.camel.quarkus/camel-quarkus-atom@3.15.1?type=jar",
        "pkg:maven/com.redhat.quarkus.platform/quarkus-camel-bom@3.15.1.redhat-00001?type=pom"
      ]
    },
    {
      "ref": "pkg:maven/com.redhat.quarkus.platform/quarkus-camel-bom@3.15.1.redhat-00001?type=pom",
      "provides": [
        "pkg:maven/org.apache.camel.quarkus/camel-quarkus-atom@3.15.1?type=jar"
      ]
    }
  ]
}
```

**Member product component identity.** Unlike Red Hat's RHEL products (which have no purl, hence CPE-as-`bom-ref` out of necessity), Quarkus platform members have a natural purl — their Maven BOM coordinates:

- **`purl`** — the member BOM coordinates (`pkg:maven/<groupId>/<bom-artifactId>@<version>?type=pom`).
- **`bom-ref`** — the purl (not the CPE). `bom-ref` is a document-local handle; purl-as-`bom-ref` keeps edges uniform with the rest of a Maven-generated SBOM and is unambiguous for multi-CPE members.
- **CPE placement** — canonical CPE in `component.cpe` (so single-CPE scanners match); all CPEs in `evidence.identity[]` (`field: cpe`) to support multi-CPE members.
- **`type: framework`, `scope: excluded`.**

#### Role Obligations

**Product Team** MUST declare which products their components are attributed to via generator configuration.

**Generator** MUST model each attributed product as a `framework` component with `scope: excluded` and a CPE.

**Generator** MUST add `provides` edges in the `dependencies` array linking each member product component to the `pkg:maven` artifacts attributed to it.

**Scanner** MUST recognize `framework` components with `scope: excluded` and a CPE as product attribution nodes, not runtime deliverables.

**Scanner** MUST follow `provides` edges to determine which product CPE (and therefore which VEX stream) applies to each component.

**Scanner** SHOULD handle components that appear in multiple products' `provides` lists by evaluating them against all applicable VEX streams.

Every component in the SBOM belongs to the top-level product or application identified in `metadata.component`. Component-level attribution identifies which upstream product team is responsible for a patch. A component with no `provides` edge pointing to it is still part of the top-level application — the application owner has no upstream product stream to look to and must address vulnerabilities directly.

#### Open questions

- **Semantic fit of `provides`**: its canonical CycloneDX meaning is "implements a specification/standard" (CBOM origin); the product-membership reading follows Red Hat's usage but is broader than the field's documented intent.
- **Consumer key for attribution**: confirm whether the consumer (Trustify) keys attribution off `component.cpe` / `evidence.identity` (correct) or relies on the RH CPE-as-`bom-ref` convention emitted by generators such as SBOMer — this determines whether purl-as-`bom-ref` is safe to adopt.

## 6. Reference Implementations

### SBOM Generators

| Generator | Approach | Placement | Key Features |
|-----------|----------|-----------|--------------|
| [maven-assembly-sbom](https://github.com/cyberstamp/maven-assembly-sbom) | Content-based: hashes every file in the archive and matches against known Maven artifacts | Embedded in archive (`sbom.cdx.json`), external next to archive (`*.cdx.json`), or both | Shaded JAR detection, unpacked WAR handling, multi-ecosystem SBOM merging (npm, pnpm), external SBOM linking, product metadata configuration |
| [Quarkus CycloneDX](https://quarkus.io/guides/cyclonedx) | Dependency-resolution-based: records components during application build | Embedded in JAR (`META-INF/sbom.cdx.json.gz`) or native executable (GraalVM SBOM) | GZip compression by default, travels with the artifact, covers both JVM and native builds |
| [WildFly/EAP Galleon plugin](https://github.com/aloubyansky/galleon-plugins/tree/sbom-cdx) | Provisioning-based: records artifacts as they are installed into the server distribution | Filesystem at distribution root (`sbom.cdx.json`) | Shaded JAR detection, npm package detection via JavaScript source map analysis, evidence/occurrences tracking |

### Scanners

| Scanner | Notes |
|---------|-------|
| [Red Hat Advanced Cluster Security (ACS)](https://www.redhat.com/en/technologies/cloud-computing/openshift/advanced-cluster-security-kubernetes) | Kubernetes-native security platform; its Scanner V4 is built on Clair and ingests SBOMs |
| [Clair](https://github.com/quay/clair) | Container vulnerability scanner with SBOM ingestion support |
