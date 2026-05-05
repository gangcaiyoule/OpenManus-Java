package com.openmanus.smoke;

/**
 * Smoke test suite marker.
 * Tests in this package are tagged with "smoke" and can be run with:
 * -Dgroups=smoke or mvn test -Dgroups=smoke
 *
 * This marker class exists to support JUnit group-based test selection.
 * No additional configuration needed - the smoke tag on individual test classes
 * is sufficient for Maven Surefire plugin configuration.
 */
public class SmokeTestSuite {
    // Suite marker - individual tests use @Tag("smoke") annotation
}
