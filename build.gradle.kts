plugins {
    java
    checkstyle
    jacoco
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.0.9"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "com.synapse"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.6")
    }
}

dependencies {
    implementation("com.stripe:stripe-java:32.1.0")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    implementation("org.springframework.modulith:spring-modulith-starter-core")

    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("dev.samstevens.totp:totp:1.7.1")

    implementation("com.github.f4b6a3:uuid-creator:5.3.3")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.avro:avro:1.12.0")
    implementation("io.confluent:kafka-avro-serializer:7.7.0") {
        // SpringDoc 3.x provides swagger-annotations-jakarta; Confluent's older annotations jar conflicts at runtime.
        exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
    }

    implementation("com.google.firebase:firebase-admin:9.4.1")
    implementation("software.amazon.awssdk:sesv2:2.26.29")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.withType<Test> {
    useJUnitPlatform()
}

configure<org.gradle.api.plugins.quality.CheckstyleExtension> {
    toolVersion = "10.12.5"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

tasks.withType<Checkstyle>().configureEach {
    setSource(source.filter {
        !it.path.contains("generated-main-avro-java")
                && !it.path.contains("generated-test-avro-java")
    })
}

spotbugs {
    toolVersion = "4.8.3"
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.synapse.platform.billing*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
