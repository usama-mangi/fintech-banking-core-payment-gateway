
# Stage 1: Build the native binary
FROM ghcr.io/graalvm/native-image-community:17 AS builder
WORKDIR /app
COPY . .
RUN ./mvnw --no-transfer-progress clean native:compile -Pnative

# Stage 2: Run the light binary
FROM ubuntu:jammy
WORKDIR /app
COPY --from=builder /app/target/my-spring-boot-app /app/server
EXPOSE 8080
CMD ["./server"]

