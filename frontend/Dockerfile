# syntax=docker/dockerfile:1
# Builds the Android debug APK. Build context: frontend/
# Used by docker compose (service "android"), which copies the APK to ./dist
FROM eclipse-temurin:21-jdk AS build

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="$PATH:/opt/android-sdk/cmdline-tools/latest/bin"
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && unzip -q /tmp/tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0" > /dev/null

WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY licenses licenses
COPY app app

# Client configuration (not secret: it ships inside every APK), passed from .env by docker compose
ARG API_BASE_URL=http://10.0.2.2:8080
ARG CHAT_BASE_URL=ws://10.0.2.2:8081
ARG FIREBASE_PROJECT_ID=
ARG FIREBASE_APP_ID=
ARG FIREBASE_API_KEY=
ARG FIREBASE_SENDER_ID=
ARG FIREBASE_WEB_CLIENT_ID=
RUN printf '%s\n' \
      "sdk.dir=$ANDROID_HOME" \
      "bookintok.apiBaseUrl=$API_BASE_URL" \
      "bookintok.chatBaseUrl=$CHAT_BASE_URL" \
      "firebase.projectId=$FIREBASE_PROJECT_ID" \
      "firebase.appId=$FIREBASE_APP_ID" \
      "firebase.apiKey=$FIREBASE_API_KEY" \
      "firebase.senderId=$FIREBASE_SENDER_ID" \
      "firebase.webClientId=$FIREBASE_WEB_CLIENT_ID" > local.properties

# The Gradle cache survives between builds, so only the first one downloads dependencies
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew :app:assembleDebug --no-daemon --console=plain -q \
    && cp app/build/outputs/apk/debug/app-debug.apk /bookintok.apk

FROM busybox:1.37
COPY --from=build /bookintok.apk /apk/bookintok.apk
# Runs once on "docker compose up" and leaves the APK in ./dist
CMD ["sh", "-c", "cp /apk/bookintok.apk /dist/bookintok.apk && echo 'APK listo en dist/bookintok.apk'"]
