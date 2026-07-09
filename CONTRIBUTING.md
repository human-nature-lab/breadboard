# Contributing
There are many different ways to contribute to the Breadboard project. We welcome issues, code, documentation
and examples. 

## Development

### Environment setup
Breadboard requires **Java 8** specifically (the Play 2.2 / Scala 2.10 / sbt 0.13.18 stack does
not build under Java 9 or newer — it hangs silently during compilation). Install a Java 8 JDK and
point sbt at it, e.g. `sbt -java-home /path/to/jdk8 test`. See `CLAUDE.md` for the full build/test
commands.

- `cd frontend`
- `pnpm install`

### Running
#### Backend development
If modifications will be made to files in the **frontend** directory, use the frontend development instructions instead
- Start the play framework server using `sbt "run -Dconfig.file=conf/application-prod.conf"`

#### Frontend development
This uses a slightly different configuration to allow hot module replacement via webpack on frontend files.
- Start the webpack server using `cd frontend && pnpm start`
- Start the play framework server using `sbt "run -Dconfig.file=conf/application-dev.conf"`


From a terminal run . This will start a dev server which will
automatically rebuild the frontend files whenever a file changes.


## Production

### Compile jars
- `cd frontend && pnpm run build:all` to build frontend assets (incl. the client/core packages) if this code has changed
- `sh create_prod_dist.sh` to compile distributable files
- In many cases, only copying the compiled **breadboard.jar** file is enough to update existing Breadboard applications.

### Self-contained, Java-bundled builds
`create_prod_dist.sh` also produces **self-contained** zips that bundle a Java 8 runtime
(Amazon Corretto 8, a TCK-certified OpenJDK 8), so recipients need no host Java — they unzip and
run `run.sh` / `run.bat`. Outputs land in `dist/`:

- `breadboard-<ver>-linux-x64.zip`
- `breadboard-<ver>-windows-x64.zip`
- `breadboard-<ver>-mac-x64.zip`
- `breadboard-<ver>-mac-aarch64.zip`

plus a Docker build context under `target/docker/` (base image `amazoncorretto:8`).

The bundling is done by `scripts/bundle-runtime.sh`, which operates on an already-built `sbt dist`
tree, so you can re-bundle without rebuilding the app:

```bash
scripts/bundle-runtime.sh target/dist-unzipped/breadboard-<ver> <ver>
```

Knobs: `BUNDLE_PLATFORMS` (trim the target list), `SKIP_BUNDLE=1` (skip bundling in
`create_prod_dist.sh`), `BUILD_DOCKER=1` (also run `docker build`). The per-platform Corretto JDKs
are downloaded once and cached under `.cache/runtimes/` (gitignored). **Build the linux/mac zips on
Linux/macOS** (they contain symlinks a Windows host may not preserve) — CI does this on native
runners; see `.github/workflows/release.yml`.

**Versioning.** The version is not hardcoded in the packaging script — it comes from
`project/Build.scala`'s `appVersion`, which reads the `BREADBOARD_VERSION` env var (default
`v2.5.0`). `create_prod_dist.sh` then derives the packaging version from the zip `sbt dist` actually
produced, so the two never drift. The release workflow runs **only on a pushed `v*` git tag** and
sets `BREADBOARD_VERSION` from that tag, so the tag drives the version everywhere (dist, bundled
zips, image tag). Locally, run `./create_prod_dist.sh` for the default, or
`BREADBOARD_VERSION=v2.6.0 ./create_prod_dist.sh` to simulate a tagged build.
