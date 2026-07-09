# Breadboard on Amazon Corretto 8 (a TCK-certified OpenJDK 8) -- the same Java 8
# runtime bundled in the per-platform zips, so the host needs no Java. The Play
# 2.2 / Scala 2.10 stack only runs on Java 8 (the launcher even passes CMS GC
# flags removed in JDK 14), which is exactly what this base image provides.
#
# Build context is assembled by create_prod_dist.sh under target/docker/:
#   app/     the unpacked `sbt dist` tree (bin/ lib/ conf/ share/)
#   groovy/  runtime Groovy scripts (loaded from disk, not bundled in the jar)
# Build with:  docker build -t breadboard:v2.5.0 target/docker
FROM amazoncorretto:8

WORKDIR /opt/breadboard

COPY app/ ./
COPY groovy/ ./groovy/

# The base image already sets JAVA_HOME and puts java on PATH, so bin/breadboard
# uses the image's Corretto 8 -- no bundled runtime needed here. We only need to
# make the launcher executable and materialize the config file it points at (the
# dist ships it as a `_application-prod.conf` reference copy). A mounted config
# volume overrides this baked-in copy at run time (cp -n won't clobber).
RUN chmod +x bin/breadboard \
 && cp -n conf/_application-prod.conf conf/application-prod.conf 2>/dev/null || true

# HTTP (9000) and HTTPS (9443). Persist the H2 database by mounting a volume at
# db/. Provide secrets/config at run time, e.g.:
#   docker run -p 9000:9000 \
#     -e APPLICATION_SECRET=$(openssl rand -hex 32) \
#     -e AMT_ACCESS_KEY=... -e AMT_SECRET_KEY=... \
#     -v breadboard-db:/opt/breadboard/db \
#     -v "$PWD/conf/application-prod.conf:/opt/breadboard/conf/application-prod.conf" \
#     breadboard:v2.5.0
EXPOSE 9000 9443
VOLUME ["/opt/breadboard/db"]

# CMS GC flags are passed via -J (the bash launcher strips -J and forwards to the
# JVM); they are valid on Java 8. Append extra flags after the image name, e.g.
# `-Dhttps.port=9443`, and they reach bin/breadboard too.
ENTRYPOINT ["./bin/breadboard"]
CMD ["-Dconfig.file=conf/application-prod.conf", "-J-XX:+UseConcMarkSweepGC", "-J-XX:+CMSClassUnloadingEnabled"]
