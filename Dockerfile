# Stage 1: Build the application

FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)

ENV MAVEN_OPTS="-Xmx256m -Xms64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy src and build the package

COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B -T1


# Stage 2: Run the application

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Phase O1: install LibreOffice writer and Vietnamese-capable fonts so
# the office preview converter can produce PDF previews inside the
# container. fontconfig + fonts-noto-core cover Vietnamese diacritics;
# fonts-liberation and fonts-dejavu-core give LibreOffice a baseline
# metric-compatible fallback. The conversion is performed at runtime,
# never during image build, so no document files are baked in.

RUN apt-get update \
    && apt-get install --no-install-recommends -y \
       libreoffice-writer \
       fontconfig \
       fonts-noto-core \
       fonts-liberation \
       fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

# Verify that LibreOffice is installed and the soffice executable exists.
# This will also make the Render build fail immediately if LibreOffice
# was not installed correctly.
RUN which soffice && soffice --version

# LibreOffice headless mode needs a writable HOME directory.
ENV HOME=/tmp

# Copy the built JAR from build stage

COPY --from=build /app/target/*.jar app.jar

# Render assigns a port dynamically via the PORT env var.
# Keep the existing application port configuration.

# Ultra-low memory footprint for 512MB RAM cap:
# - C1 compiler only (-XX:TieredStopAtLevel=1) to eliminate heavy C2 JIT compiler threads and RAM spikes
# - SerialGC to minimize GC thread overhead
# - Capped heap (-Xmx130m), Metaspace (140m), CodeCache (32m), DirectMemory (12m)
ENTRYPOINT ["java", "-Xms32m", "-Xmx130m", "-Xss256k", "-XX:MaxMetaspaceSize=140m", "-XX:ReservedCodeCacheSize=32m", "-XX:CompressedClassSpaceSize=32m", "-XX:MaxDirectMemorySize=12m", "-XX:+UseSerialGC", "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]