FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY target/summer-realworld-0.1.0.jar app.jar

EXPOSE 8080

CMD ["java", "--enable-preview", "-XX:InitialRAMPercentage=75.0", "-XX:MaxRAMPercentage=75.0", "-XX:+AlwaysPreTouch", "-jar", "app.jar"]