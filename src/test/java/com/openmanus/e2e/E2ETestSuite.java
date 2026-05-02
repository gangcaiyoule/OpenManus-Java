package com.openmanus.e2e;

/**
 * E2E test suite marker.
 * Tests in this package are tagged with "e2e" and can be run with:
 * -Dgroups=e2e or mvn test -Dgroups=e2e
 *
 * This marker class exists to support JUnit group-based test selection.
 * No additional configuration needed - the e2e tag on individual test classes
 * is sufficient for Maven Surefire plugin configuration.
 */
public class E2ETestSuite {
    // Suite marker - individual tests use @Tag("e2e") annotation
}
