# Working in this repo

## Communication

Be plain and concise. No tables unless the data genuinely needs columns, no section
headers on short answers, no restating what was just done. Lead with the finding, not the
process that produced it.

## Project

A Fabric addon for Voxy (`MCRcortex/voxy`), an LOD rendering mod. See DESIGN.md for scope
and the reasoning behind it.

Voxy is All Rights Reserved and must never be bundled or copied into this repo — it is a
`compileOnly` / `runtimeOnly` dependency only.

Keep the mixin surface minimal. Voxy is pre-release and changes often; every mixin is a
future breakage. Prefer its public API, and justify any new mixin in the README table.

## Build

JDK 25, Gradle 9.6+, matched to Voxy's own `gradle.properties`. Voxy is pinned by Modrinth
version *ID* rather than version number, since the same number is published for multiple
Minecraft versions.
