# Local-only act runner image: maven + JDK 25 + a central mirror.
#
# Why: the act container's egress to repo.maven.apache.org is Cloudflare-blocked
# (403 "Attention Required" — the container does not share the host's proxy).
# The host and the GitHub runner are not affected. The aliyun mirror is baked
# into the global settings so every mvn invocation in the container uses it.
#
# Build:   docker build -t summer-act:latest .github/act
# Run:     act -W .github/workflows/maven.yml --job build \
#            -P ubuntu-latest=summer-act:latest --pull=false
#          (NO --bind: copy mode keeps the repo's target/ dirs unowned by root —
#           a --bind run writes root-owned files into the repo and breaks local
#           builds until cleaned)
FROM maven:3.9-eclipse-temurin-25
COPY settings.xml /usr/share/maven/conf/settings.xml
