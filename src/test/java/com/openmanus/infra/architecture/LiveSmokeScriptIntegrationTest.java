package com.openmanus.infra.architecture;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSmokeScriptIntegrationTest {

    private static final Path SOURCE_SCRIPT = Path.of("scripts/run-live-smoke.sh");
    private static final String[] LIVE_SMOKE_ENV_PREFIXES = {
            "OPENMANUS_LIVE_",
            "OPENMANUS_LLM_DEFAULT_LLM_",
            "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_",
            "OPENMANUS_LLM_PROVIDERS_GEMINI_",
            "OPENAI_"
    };

    @TempDir
    Path tempDir;

    @Test
    void shouldPassWhenAllLiveSmokeReportsAreNonSkipped() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-pass");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""),
                reportXml("AnthropicClientLiveSmokeTest", 1, 0, 0, 0, ""),
                reportXml("GeminiClientLiveSmokeTest", 1, 0, 0, 0, "")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=3, failures=0, errors=0, skipped=0"),
                "script should aggregate non-skipped live smoke stats");
        assertTrue(result.output.contains("Live smoke first issue: none"),
                "script should report none when all live smoke tests pass");
    }

    @Test
    void shouldFailFastWhenRequiredLiveSmokeEnvVarsAreMissing() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-missing-env");
        Path script = prepareScript(repo);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: missing live smoke env vars:"),
                "script should fail before invoking maven when required env vars are absent");
        assertTrue(result.output.contains("OPENMANUS_LIVE_BASE_URL"),
                "script should report missing OpenAI-compatible base URL");
        assertTrue(result.output.contains("OPENMANUS_LIVE_API_KEY"),
                "script should report missing OpenAI-compatible API key");
        assertTrue(result.output.contains("OPENMANUS_LLM_DEFAULT_LLM_MODEL"),
                "script should explain the default LLM fallback for OpenAI-compatible env vars");
        assertFalse(result.output.contains("OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL"),
                "script should not require Anthropic fallback guidance when optional provider is fully absent");
        assertFalse(result.output.contains("OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL"),
                "script should not require Gemini fallback guidance when optional provider is fully absent");
    }

    @Test
    void shouldFailFastWhenOptionalProviderEnvIsIncomplete() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-incomplete-optional-provider");
        Path script = prepareScript(repo);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "openai-model"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-key"),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_MODEL", "anthropic-model")
        ), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: incomplete anthropic live smoke env vars:"),
                "script should reject partially configured optional provider env");
        assertTrue(result.output.contains("OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL"),
                "script should explain Anthropic provider profile fallback for incomplete optional provider env");
        assertFalse(result.output.contains("OPENMANUS_LLM_DEFAULT_LLM_MODEL"),
                "script should not print OpenAI fallback guidance when OpenAI vars are already configured");
        assertFalse(result.output.contains("OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL"),
                "script should not print Gemini fallback guidance when Gemini provider is absent");
    }

    @Test
    void shouldRunOnlyOpenAiLiveSmokeByDefault() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-only");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "openai-model"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-key")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should execute only the OpenAI-compatible live smoke by default");
    }

    @Test
    void shouldAllowOpenAiLiveSmokeWhenOnlyModelCandidatesAreConfigured() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-candidates-only");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL_CANDIDATES", "gpt-5-mini,gpt-4o-mini"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-key")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should accept model candidates as the minimal OpenAI-compatible model input");
    }

    @Test
    void shouldAllowOpenAiLiveSmokeWhenOnlyBuiltInFallbackModelsAreAvailable() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-built-in-fallback");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-key")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=gpt-5.4,gpt-5,gpt-4o, baseUrl=https://openai.example/v1, apiKey=len=10,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:built-in-fallback,baseUrl:live,apiKey:live"),
                "script should preview built-in OpenAI-compatible fallback models when no explicit model is configured");
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should allow the OpenAI-compatible live smoke to run with built-in fallback models");
    }

    @Test
    void shouldPreviewDefaultLlmFallbackSourcesWhenOpenAiLiveEnvIsBackfilled() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-default-llm-fallback");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LLM_DEFAULT_LLM_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES", "gpt-5,gpt-4o"),
                Map.entry("OPENMANUS_LLM_DEFAULT_LLM_BASE_URL", "https://default-llm.example/v1"),
                Map.entry("OPENMANUS_LLM_DEFAULT_LLM_API_KEY", "default-llm-secret")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains(
                        "Live smoke effective OpenAI config: models=gpt-5.4,gpt-5,gpt-4o, baseUrl=https://default-llm.example/v1, apiKey=len=18,suffix=cret, caCertFile=<default-jvm-truststore>, source=models:default-llm,baseUrl:default-llm,apiKey:default-llm"),
                "script should preview default-llm backfill sources when OPENMANUS_LIVE_* env vars are absent");
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should still run the OpenAI-compatible live smoke when default-llm fallback is complete");
    }

    @Test
    void shouldPrintMaskedEffectiveOpenAiLiveSmokeConfig() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-config-preview");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL_CANDIDATES", "gpt-5.4,gpt-5"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=gpt-5.4,gpt-5, baseUrl=https://openai.example/v1, apiKey=len=17,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:live,baseUrl:live,apiKey:live"),
                "script should print the effective OpenAI live smoke selection with a masked API key");
        assertFalse(result.output.contains("openai-secret-key"),
                "script should not print the raw OpenAI live smoke API key");
    }

    @Test
    void shouldPrintMaskedEffectiveOpenAiLiveSmokeConfigWhenOnlyPrimaryModelIsConfigured() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-config-primary-only");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=gpt-5.4, baseUrl=https://openai.example/v1, apiKey=len=17,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:live,baseUrl:live,apiKey:live"),
                "script should print the primary OpenAI model even when no candidate list is configured");
        assertFalse(result.output.contains("openai-secret-key"),
                "script should not print the raw OpenAI live smoke API key when only the primary model is configured");
    }

    @Test
    void shouldPrintMergedEffectiveOpenAiCandidatesWhenModelAndCandidatesAreBothConfigured() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-config-merged-preview");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_MODEL_CANDIDATES", "gpt-5,gpt-5.4,gpt-4o"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=gpt-5.4,gpt-5,gpt-4o, baseUrl=https://openai.example/v1, apiKey=len=17,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:live,baseUrl:live,apiKey:live"),
                "script should print the merged and de-duplicated OpenAI candidate list");
        assertFalse(result.output.contains("openai-secret-key"),
                "script should not print the raw OpenAI live smoke API key when merged candidates are shown");
    }

    @Test
    void shouldFailFastWhenConfiguredLiveSmokeCaCertFileIsMissing() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-missing-ca-cert");
        Path script = prepareScript(repo);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key"),
                Map.entry("OPENMANUS_LIVE_CA_CERT_FILE", repo.resolve("missing-ca.pem").toString())
        ), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: OPENMANUS_LIVE_CA_CERT_FILE does not exist:"),
                "script should fail before invoking maven when the configured CA bundle path is missing");
    }

    @Test
    void shouldExpandHomePrefixedLiveSmokeCaCertFileBeforeValidation() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-home-ca-cert");
        Path script = prepareScript(repo);
        Path homeDir = repo.resolve("custom-home");
        Files.createDirectories(homeDir);
        Path certFile = homeDir.resolve("live-ca.pem");
        Files.writeString(certFile, """
                -----BEGIN CERTIFICATE-----
                MIIHDzCCBPegAwIBAgIQOGJdabDTzrTEobwYjnmaOjANBgkqhkiG9w0BAQsFADBn
                MQswCQYDVQQGEwJDTjE3MDUGA1UECgwuSGVuYW4gRmllcmNlIEZpcmUgTmV0d29y
                ayBUZWNobm9sb2d5IENvLiwgTHRkLjEfMB0GA1UEAwwWQ05TU0wgRFYgVExTIEcy
                IFIzNiBDQTAeFw0yNjA0MDIwMjA1NDBaFw0yNjEwMTgwMjA1MzlaMBoxGDAWBgNV
                BAMMD2FwaS53ZWNsYXdhaS5jYzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoC
                ggEBAKFhpvqf5h5l0mmphSdBbghqZI2Mc+3LgrXgp2ENe1Yqfn9nVR5ThGBashH9
                Gb9VBtsidiyriKuRMRhTUmEUHX+9KeSCt4L3YOB1ERFmM++pii1au5/isWmTwKgU
                bL8bkamk+mA4pCy+sQ245Vehce9q6DyQw1o/kdjfPDax084l43IC2GR1dz41Exa7
                13PWyzmirK7JLMmUxAzXyhFzZLaVTi2MK9w7kdn+RiwfMsadEmSWPBryT2IKKB4q
                pbFqn8bKp9sfWKG7wv2igvOA3ohYe+SIP4NLDRsuIgXJwkgklgiDKhrsmW7WMqcZ
                gBjjgLAl2AvnWr6UlV2MHXYYrI0CAwEAAaOCAwIwggL+MAwGA1UdEwEB/wQCMAAw
                TQYDVR0fBEYwRDBCoECgPoY8aHR0cDovL2Nuc3NsZHZ0bHNnMnIzNmNhLmNybC5j
                ZXJ0dW0ucGwvY25zc2xkdnRsc2cycjM2Y2EuY3JsMIGXBggrBgEFBQcBAQSBijCB
                hzA0BggrBgEFBQcwAYYoaHR0cDovL2Nuc3NsZHZ0bHNnMnIzNmNhLm9jc3AtY2Vy
                dHVtLmNvbTBPBggrBgEFBQcwAoZDaHR0cDovL2Nuc3NsZHZ0bHNnMnIzNmNhLnJl
                cG9zaXRvcnkuY2VydHVtLnBsL2Nuc3NsZHZ0bHNnMnIzNmNhLmNlcjAfBgNVHSME
                GDAWgBTx77F5QQnx6RWL6assloKWulHrpTAhBgNVHSAEGjAYMAgGBmeBDAECATAM
                BgoqhGgBhvZ3AmUBMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA4GA1UdDwEB/wQEAwIF
                oDAaBgNVHREEEzARgg9hcGkud2VjbGF3YWkuY2MwggF+BgorBgEEAdZ5AgQCBIIB
                bgSCAWoBaAB3AK9niDtXsE7dj6bZfvYuqOuBCsdxYPAkXlXWDC/nhYc6AAABnUvw
                bs4AAAQDAEgwRgIhALUGviaz+Nc+96eioh7mqwtX1cd7+2mxA6dFWbRzI0GcAiEA
                79MM8vowM5F+8L3mRPhpe4MyjltK6py4Gd7ThgyuS34AdgDYCVU7lE96/8gWGW+U
                T4WrsPj8XodVJg8V0S5yu0VLFAAAAZ1L8G68AAAEAwBHMEUCIQC1gNY+wzAaJIA1
                0wS3y/L/mJ8f47Qmi9dZgnsbd+RP6AIgbgJdlNIewJQqfoagX03NBgEd1iW3zuYa
                atAWBtNnBBsAdQDXbX0Q0af1d8LH6V/XAL/5gskzWmXh0LMBcxfAyMVpdwAAAZ1L
                8HL4AAAEAwBGMEQCIFFl3uiUyyNChgkz3ghSWGOTa82ZiGPnl8FVka5UNyJVAiAC
                1XYJoX7K7B7KKQWyD/VrPXnBCxTiz+c6lM0JPzuXQTANBgkqhkiG9w0BAQsFAAOC
                AgEAQyp6yY5VbwnkqoOyPBmYccfiOtjQ73LCYMNcVO9OlcxsbDgl8Nllw3rFUKWY
                O3JAgrerGI2iISzsCMib114CYJDD+vKh+P4WTik86scN9y3LV6+zD/yd0AFNtrLQ
                mHIgaP4n8njYGC+FJX1ipk0+LS1RiwzOLLkvVQLIoUrKtBhOXC5Hom0KdRJN+Lhx
                7gg2Ei1cF8eJXF8x+s+PuqWmYwO5Q09mHxYOG+OhB/e+3xHg1pymXJ3C68v9NVsW
                gczGDi0A1yInATHGVrc0cpASk7kSzySSBe7md4xXsGMIlhq4GZQfirgTqWxJO5f1
                xqB2A+idXmdIb7qM9kYBQ6SODoZXW/EoAIYbbnOfkL/SnM3fpVDQBlt1gzpSlctr
                fTMzRY9M6KERtp0Fn+PFI9NAlIdvugrkjgPoTnKGUWI4at0PD++AKJlSz6fG3VvS
                rszsJeY9z3CV8iHQWd1XSXxXV08YDy4m69rq2levyOUctMDegiWcB1pm9otYXFdm
                e4PvRn+krCfv2OV5Afygirqeuy1krVmt0Np8FzitWUqx74jsas84Z9X+sIg5oYE+
                nuE+aXLG1bCND5PMMug4kchz9ay+hz6UyZmsBxVpxy/owTXn0feXUjaSLNBO6EL9
                1k1ZobBRld/68dehFhDRUfVXi3AF9s86po/pOMuWoxorv+g=
                -----END CERTIFICATE-----
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnvContains(repo,
                "OpenAiClientLiveSmokeTest",
                "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.trustStore=",
                "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.trustStoreType=PKCS12");

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("HOME", homeDir.toString()),
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key"),
                Map.entry("OPENMANUS_LIVE_CA_CERT_FILE", "~/live-ca.pem")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("caCertFile=" + certFile),
                "script should expand ~/ in the configured CA certificate path before preview and validation");
    }

    @Test
    void shouldExpandHomeVariableInMissingLiveSmokeCaCertFileError() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-home-missing-ca-cert");
        Path script = prepareScript(repo);
        Path homeDir = repo.resolve("custom-home");
        Files.createDirectories(homeDir);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("HOME", homeDir.toString()),
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key"),
                Map.entry("OPENMANUS_LIVE_CA_CERT_FILE", "${HOME}/missing-ca.pem")
        ), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: OPENMANUS_LIVE_CA_CERT_FILE does not exist: "
                        + homeDir.resolve("missing-ca.pem")),
                "script should report the expanded HOME-based CA certificate path when validation fails");
    }

    @Test
    void shouldPassConfiguredLiveSmokeCaCertFileIntoJavaToolOptions() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-live-ca-cert");
        Path script = prepareScript(repo);
        Path certFile = repo.resolve("live-ca.pem");
        Files.writeString(certFile, """
                -----BEGIN CERTIFICATE-----
                MIIHDzCCBPegAwIBAgIQOGJdabDTzrTEobwYjnmaOjANBgkqhkiG9w0BAQsFADBn
                MQswCQYDVQQGEwJDTjE3MDUGA1UECgwuSGVuYW4gRmllcmNlIEZpcmUgTmV0d29y
                ayBUZWNobm9sb2d5IENvLiwgTHRkLjEfMB0GA1UEAwwWQ05TU0wgRFYgVExTIEcy
                IFIzNiBDQTAeFw0yNjA0MDIwMjA1NDBaFw0yNjEwMTgwMjA1MzlaMBoxGDAWBgNV
                BAMMD2FwaS53ZWNsYXdhaS5jYzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoC
                ggEBAKFhpvqf5h5l0mmphSdBbghqZI2Mc+3LgrXgp2ENe1Yqfn9nVR5ThGBashH9
                Gb9VBtsidiyriKuRMRhTUmEUHX+9KeSCt4L3YOB1ERFmM++pii1au5/isWmTwKgU
                bL8bkamk+mA4pCy+sQ245Vehce9q6DyQw1o/kdjfPDax084l43IC2GR1dz41Exa7
                13PWyzmirK7JLMmUxAzXyhFzZLaVTi2MK9w7kdn+RiwfMsadEmSWPBryT2IKKB4q
                pbFqn8bKp9sfWKG7wv2igvOA3ohYe+SIP4NLDRsuIgXJwkgklgiDKhrsmW7WMqcZ
                gBjjgLAl2AvnWr6UlV2MHXYYrI0CAwEAAaOCAwIwggL+MAwGA1UdEwEB/wQCMAAw
                TQYDVR0fBEYwRDBCoECgPoY8aHR0cDovL2Nuc3NsZHZ0bHNnMnIzNmNhLmNybC5j
                ZXJ0dW0ucGwvY25zc2xkdnRsc2cycjM2Y2EuY3JsMIGXBggrBgEFBQcBAQSBijCB
                hzA0BggrBgEFBQcwAYYoaHR0cDovL2Nuc3NsZHZ0bHNnMnIzNmNhLm9jc3AtY2Vy
                dHVtLmNvbTBPBggrBgEFBQcwAoZDaHR0cDovL2Nuc3NsZHZ0bHNnMnIzNmNhLnJl
                cG9zaXRvcnkuY2VydHVtLnBsL2Nuc3NsZHZ0bHNnMnIzNmNhLmNlcjAfBgNVHSME
                GDAWgBTx77F5QQnx6RWL6assloKWulHrpTAhBgNVHSAEGjAYMAgGBmeBDAECATAM
                BgoqhGgBhvZ3AmUBMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA4GA1UdDwEB/wQEAwIF
                oDAaBgNVHREEEzARgg9hcGkud2VjbGF3YWkuY2MwggF+BgorBgEEAdZ5AgQCBIIB
                bgSCAWoBaAB3AK9niDtXsE7dj6bZfvYuqOuBCsdxYPAkXlXWDC/nhYc6AAABnUvw
                bs4AAAQDAEgwRgIhALUGviaz+Nc+96eioh7mqwtX1cd7+2mxA6dFWbRzI0GcAiEA
                79MM8vowM5F+8L3mRPhpe4MyjltK6py4Gd7ThgyuS34AdgDYCVU7lE96/8gWGW+U
                T4WrsPj8XodVJg8V0S5yu0VLFAAAAZ1L8G68AAAEAwBHMEUCIQC1gNY+wzAaJIA1
                0wS3y/L/mJ8f47Qmi9dZgnsbd+RP6AIgbgJdlNIewJQqfoagX03NBgEd1iW3zuYa
                atAWBtNnBBsAdQDXbX0Q0af1d8LH6V/XAL/5gskzWmXh0LMBcxfAyMVpdwAAAZ1L
                8HL4AAAEAwBGMEQCIFFl3uiUyyNChgkz3ghSWGOTa82ZiGPnl8FVka5UNyJVAiAC
                1XYJoX7K7B7KKQWyD/VrPXnBCxTiz+c6lM0JPzuXQTANBgkqhkiG9w0BAQsFAAOC
                AgEAQyp6yY5VbwnkqoOyPBmYccfiOtjQ73LCYMNcVO9OlcxsbDgl8Nllw3rFUKWY
                O3JAgrerGI2iISzsCMib114CYJDD+vKh+P4WTik86scN9y3LV6+zD/yd0AFNtrLQ
                mHIgaP4n8njYGC+FJX1ipk0+LS1RiwzOLLkvVQLIoUrKtBhOXC5Hom0KdRJN+Lhx
                7gg2Ei1cF8eJXF8x+s+PuqWmYwO5Q09mHxYOG+OhB/e+3xHg1pymXJ3C68v9NVsW
                gczGDi0A1yInATHGVrc0cpASk7kSzySSBe7md4xXsGMIlhq4GZQfirgTqWxJO5f1
                xqB2A+idXmdIb7qM9kYBQ6SODoZXW/EoAIYbbnOfkL/SnM3fpVDQBlt1gzpSlctr
                fTMzRY9M6KERtp0Fn+PFI9NAlIdvugrkjgPoTnKGUWI4at0PD++AKJlSz6fG3VvS
                rszsJeY9z3CV8iHQWd1XSXxXV08YDy4m69rq2levyOUctMDegiWcB1pm9otYXFdm
                e4PvRn+krCfv2OV5Afygirqeuy1krVmt0Np8FzitWUqx74jsas84Z9X+sIg5oYE+
                nuE+aXLG1bCND5PMMug4kchz9ay+hz6UyZmsBxVpxy/owTXn0feXUjaSLNBO6EL9
                1k1ZobBRld/68dehFhDRUfVXi3AF9s86po/pOMuWoxorv+g=
                -----END CERTIFICATE-----
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnvContains(repo,
                "OpenAiClientLiveSmokeTest",
                "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.trustStore=",
                "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.trustStoreType=PKCS12",
                "JAVA_TOOL_OPTIONS", "-Djavax.net.ssl.trustStorePassword=changeit");

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key"),
                Map.entry("OPENMANUS_LIVE_CA_CERT_FILE", certFile.toString())
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("caCertFile=" + certFile),
                "script should print the configured CA certificate file when custom trust is enabled");
    }

    @Test
    void shouldPassConfiguredTlsJvmOverridesIntoJavaToolOptions() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-live-tls-jvm-overrides");
        Path script = prepareScript(repo);

        writeFakeMvnwAssertingEnvContains(repo,
                "OpenAiClientLiveSmokeTest",
                "JAVA_TOOL_OPTIONS", "-Djdk.tls.client.protocols=TLSv1.2",
                "JAVA_TOOL_OPTIONS", "-Dhttps.protocols=TLSv1.2",
                "JAVA_TOOL_OPTIONS", "-Djavax.net.debug=ssl:handshake");

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "gpt-5.4"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-secret-key"),
                Map.entry("OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS", "TLSv1.2"),
                Map.entry("OPENMANUS_LIVE_HTTPS_PROTOCOLS", "TLSv1.2"),
                Map.entry("OPENMANUS_LIVE_JAVA_TOOL_OPTIONS", "-Djavax.net.debug=ssl:handshake")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains(
                        "Live smoke JVM overrides: jdkTlsClientProtocols=TLSv1.2, httpsProtocols=TLSv1.2, extraJavaToolOptions=<provided>"),
                "script should print the effective live smoke JVM override summary without echoing raw extra options");
        assertFalse(result.output.contains("Live smoke JVM overrides: jdkTlsClientProtocols=TLSv1.2, httpsProtocols=TLSv1.2, extraJavaToolOptions=-Djavax.net.debug=ssl:handshake"),
                "script should not echo raw extra JAVA_TOOL_OPTIONS values");
    }

    @Test
    void shouldIncludeOptionalProvidersWhenTheirEnvIsConfigured() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-all-provider-selection");
        Path script = prepareScript(repo);

        writeFakeMvnwExpectingTestSelection(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, ""),
                reportXml("AnthropicClientLiveSmokeTest", 1, 0, 0, 0, ""),
                reportXml("GeminiClientLiveSmokeTest", 1, 0, 0, 0, ""));

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=3, failures=0, errors=0, skipped=0"),
                "script should include optional provider live smoke tests when their env is configured");
    }

    @Test
    void shouldFailFastWhenLiveSmokeApiKeysStillUseDotenvExamplePlaceholders() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-placeholder-env");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=gpt-5.4
                OPENMANUS_LIVE_BASE_URL=https://api.openai.com/v1
                OPENMANUS_LIVE_API_KEY=your-openai-live-api-key-here
                OPENMANUS_LIVE_ANTHROPIC_MODEL=claude-sonnet-4-20250514
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://api.anthropic.com
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=your-anthropic-live-api-key-here
                OPENMANUS_LIVE_GEMINI_MODEL=gemini-2.5-pro
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://generativelanguage.googleapis.com
                OPENMANUS_LIVE_GEMINI_API_KEY=your-gemini-live-api-key-here
                """, StandardCharsets.UTF_8);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: placeholder live smoke env vars:"),
                "script should reject dotenv.example placeholder credentials before invoking maven");
        assertTrue(result.output.contains("OPENMANUS_LIVE_API_KEY"),
                "script should report placeholder OpenAI credentials");
        assertTrue(result.output.contains("OPENMANUS_LIVE_ANTHROPIC_API_KEY")
                        || result.output.contains("OPENMANUS_LIVE_GEMINI_API_KEY"),
                "script should reject placeholder credentials for any configured optional provider");
    }

    @Test
    void shouldFailFastWhenOpenAiLiveEnvBackfillsDefaultLlmPlaceholderApiKey() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-default-placeholder");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LLM_DEFAULT_LLM_MODEL=gpt-5.4
                OPENMANUS_LLM_DEFAULT_LLM_BASE_URL=https://api.openai.com/v1
                OPENMANUS_LLM_DEFAULT_LLM_API_KEY=your-openai-compatible-api-key-here
                OPENMANUS_LIVE_ANTHROPIC_MODEL=claude-sonnet-4-20250514
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://api.anthropic.com
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=anthropic-key
                OPENMANUS_LIVE_GEMINI_MODEL=gemini-2.5-pro
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://generativelanguage.googleapis.com
                OPENMANUS_LIVE_GEMINI_API_KEY=gemini-key
                """, StandardCharsets.UTF_8);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: placeholder live smoke env vars: OPENMANUS_LIVE_API_KEY"),
                "script should reject OpenAI placeholder credentials after default LLM backfill");
    }

    @Test
    void shouldFailFastWhenProviderProfileBackfillsProviderPlaceholderApiKeys() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-provider-placeholder");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=gpt-5.4
                OPENMANUS_LIVE_BASE_URL=https://api.openai.com/v1
                OPENMANUS_LIVE_API_KEY=openai-key
                OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL=claude-sonnet-4-20250514
                OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL=https://api.anthropic.com
                OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY=your-anthropic-api-key-here
                OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL=gemini-2.5-pro
                OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL=https://generativelanguage.googleapis.com
                OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY=your-gemini-api-key-here
                """, StandardCharsets.UTF_8);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: placeholder live smoke env vars:"),
                "script should reject provider placeholders after profile backfill");
        assertTrue(result.output.contains("OPENMANUS_LIVE_ANTHROPIC_API_KEY"),
                "script should report Anthropic placeholder credentials after backfill");
        assertTrue(result.output.contains("OPENMANUS_LIVE_GEMINI_API_KEY"),
                "script should report Gemini placeholder credentials after backfill");
    }

    @Test
    void shouldAllowAssumptionSkippedSummaryWhenEnvironmentIsNotReady() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-skipped");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\"><![CDATA[org.opentest4j.TestAbortedException: Assumption failed: live smoke test requires OPENMANUS_LIVE_MODEL/BASE_URL/API_KEY env vars]]></skipped>"),
                reportXml("AnthropicClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\"><![CDATA[org.opentest4j.TestAbortedException: Assumption failed: live smoke test requires OPENMANUS_LIVE_ANTHROPIC_MODEL/BASE_URL/API_KEY env vars]]></skipped>"),
                reportXml("GeminiClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\"><![CDATA[org.opentest4j.TestAbortedException: Assumption failed: live smoke test requires OPENMANUS_LIVE_GEMINI_MODEL/BASE_URL/API_KEY env vars]]></skipped>")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=3, failures=0, errors=0, skipped=3"),
                "script should report skipped live smoke stats");
        assertTrue(result.output.contains("Live smoke first issue: Assumption failed"),
                "script should classify skipped live smoke assumptions");
        assertTrue(result.output.contains("Live smoke first issue detail: org.opentest4j.TestAbortedException: Assumption failed: "
                        + "live smoke test requires OPENMANUS_LIVE_MODEL/BASE_URL/API_KEY env vars"),
                "script should print the first skipped issue detail");
    }

    @Test
    void shouldPrintTlsRemediationGuidanceWhenAssumptionSkipContainsPkixFailure() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-skipped-pkix");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\"><![CDATA["
                                + "org.opentest4j.TestAbortedException: Assumption failed: live smoke skipped because "
                                + "external gateway/TLS environment is not ready: SSLHandshakeException <- PKIX path "
                                + "building failed"
                                + "]]></skipped>")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke first issue: Assumption failed"),
                "script should keep TLS environment skips classified as assumptions");
        assertTrue(result.output.contains("Live smoke remediation: TLS/certificate handshake issue detected. "
                        + "Provide OPENMANUS_LIVE_CA_CERT_FILE with the gateway CA bundle"),
                "script should print the TLS remediation hint for PKIX-based assumption skips");
    }

    @Test
    void shouldAllowAssumptionSkippedSummaryWhenExternalQuotaIsInsufficient() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-skipped-insufficient-balance");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\"><![CDATA["
                                + "org.opentest4j.TestAbortedException: Assumption failed: live smoke skipped because "
                                + "external gateway/TLS environment is not ready: Provider request failed: status=402, "
                                + "body={\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_balance\","
                                + "\"message\":\"[本地网关] 余额不足，请先充值\"}}"
                                + "]]></skipped>")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=1"),
                "script should allow external quota blockers to remain classified as assumption skips");
        assertTrue(result.output.contains("Live smoke first issue: Assumption failed"),
                "script should keep balance blockers classified as assumptions");
        assertTrue(result.output.contains("insufficient_balance"),
                "script should surface the first external quota blocker detail");
    }

    @Test
    void shouldFailAndReportFailureWhenOpenAiCandidatesAllFail() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-openai-candidate-failure");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 1, 0, 0,
                        "<failure message=\"all OpenAI-compatible live smoke model candidates failed\"><![CDATA["
                                + "org.opentest4j.AssertionFailedError: all OpenAI-compatible live smoke model "
                                + "candidates failed: attempted=[gpt-5.4, gpt-5-mini], detail=gpt-5.4 -> "
                                + "403 bad_response_status_code; gpt-5-mini -> 503 model_not_found"
                                + "]]></failure>")
        );

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL_CANDIDATES", "gpt-5.4,gpt-5-mini"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "openai-key")
        ), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=1, errors=0, skipped=0"),
                "script should classify all-candidate OpenAI failures as test failures");
        assertTrue(result.output.contains("Live smoke first issue: failure"),
                "script should prefer failure over skipped when OpenAI candidate probing fails");
        assertTrue(result.output.contains("Live smoke first issue detail: all OpenAI-compatible live smoke model candidates failed"),
                "script should print the first failure message");
    }

    @Test
    void shouldFailWhenSurefireReportsAreMissing() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-missing");
        Path script = prepareScript(repo);
        writeFakeMvnw(repo);

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: no live smoke surefire reports found under target/surefire-reports."),
                "script should fail fast when reports are absent");
    }

    @Test
    void shouldFailWhenLocalMavenWrapperIsMissingOrNotExecutable() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-missing-mvnw");
        Path script = prepareScript(repo);
        Files.createDirectories(repo.resolve("scripts"));

        ProcessResult result = run(repo, script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: ./scripts/mvnw-local.sh is not executable."),
                "script should fail fast when local Maven wrapper is unavailable");
        assertTrue(result.output.contains("Please run: chmod +x scripts/mvnw-local.sh scripts/run-live-smoke.sh"),
                "script should explain how to recover executable permissions");
    }

    @Test
    void shouldIgnoreStaleReportsFromPreviousRuns() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-stale");
        Path script = prepareScript(repo);
        Path reportsDir = repo.resolve("target/surefire-reports");
        Files.createDirectories(reportsDir);
        Files.writeString(reportsDir.resolve("TEST-stale-LiveSmokeTest.xml"),
                reportXml("StaleLiveSmokeTest", 1, 0, 0, 0, ""),
                StandardCharsets.UTF_8);
        writeFakeMvnw(repo);

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("ERROR: no live smoke surefire reports found under target/surefire-reports."),
                "script should delete stale reports before checking current run output");
    }

    @Test
    void shouldFailAndPreferFailureOverOtherIssueTypes() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-failure");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 1, 0, 0,
                        "<failure message=\"boom\">stacktrace</failure>"),
                reportXml("AnthropicClientLiveSmokeTest", 1, 0, 1, 0,
                        "<error message=\"transport\">trace</error>"),
                reportXml("GeminiClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\">skipped</skipped>")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=3, failures=1, errors=1, skipped=1"),
                "script should aggregate failure, error and skipped counts");
        assertTrue(result.output.contains("Live smoke first issue: failure"),
                "script should prefer failure when multiple issue types exist");
        assertTrue(result.output.contains("Live smoke first issue detail: boom"),
                "script should print the first failure detail when multiple issue types exist");
    }

    @Test
    void shouldFailAndClassifyPlainSkippedWhenAssumptionMarkerIsAbsent() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-plain-skipped");
        Path script = prepareScript(repo);

        writeFakeMvnw(repo,
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 1,
                        "<skipped type=\"org.opentest4j.TestAbortedException\">skipped without assumption marker</skipped>")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=1"),
                "script should aggregate plain skipped live smoke stats");
        assertTrue(result.output.contains("Live smoke first issue: skipped"),
                "script should classify skipped when no assumption marker exists");
        assertTrue(result.output.contains("Live smoke first issue detail: skipped without assumption marker"),
                "script should print plain skipped details when no message attribute exists");
    }

    @Test
    void shouldPropagateMavenExitCodeWhenReportsAreClean() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-mvn-exit");
        Path script = prepareScript(repo);

        writeFakeMvnwWithExitCode(repo, 7,
                reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, "")
        );

        ProcessResult result = run(repo, fullLiveEnv(), script.toString());

        assertEquals(7, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should still print aggregated stats before returning mvn exit code");
        assertTrue(result.output.contains("Live smoke first issue: none"),
                "script should preserve clean issue classification when reports are clean");
    }

    @Test
    void shouldLoadLiveSmokeEnvVarsFromDotenvWhenShellEnvIsMissing() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-dotenv");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=dotenv-openai
                export OPENMANUS_LIVE_API_KEY="dotenv-key"
                OPENMANUS_LIVE_BASE_URL='https://dotenv.example/v1'
                OPENMANUS_LIVE_ANTHROPIC_MODEL=dotenv-anthropic
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=dotenv-anthropic-key
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://anthropic.example/v1
                OPENMANUS_LIVE_GEMINI_MODEL=dotenv-gemini
                OPENMANUS_LIVE_GEMINI_API_KEY=dotenv-gemini-key
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://gemini.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "dotenv-openai",
                "OPENMANUS_LIVE_API_KEY", "dotenv-key",
                "OPENMANUS_LIVE_BASE_URL", "https://dotenv.example/v1",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "dotenv-anthropic",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "dotenv-anthropic-key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1",
                "OPENMANUS_LIVE_GEMINI_MODEL", "dotenv-gemini",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "dotenv-gemini-key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should expose .env variables to the live smoke maven run");
    }

    @Test
    void shouldBackfillOpenAiLiveEnvFromDefaultLlmDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-default-llm-openai");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LLM_DEFAULT_LLM_MODEL=default-openai
                OPENMANUS_LLM_DEFAULT_LLM_API_KEY=default-openai-key
                OPENMANUS_LLM_DEFAULT_LLM_BASE_URL=https://default-openai.example/v1
                OPENMANUS_LIVE_ANTHROPIC_MODEL=dotenv-anthropic
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=dotenv-anthropic-key
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://anthropic.example/v1
                OPENMANUS_LIVE_GEMINI_MODEL=dotenv-gemini
                OPENMANUS_LIVE_GEMINI_API_KEY=dotenv-gemini-key
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://gemini.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "default-openai",
                "OPENMANUS_LIVE_API_KEY", "default-openai-key",
                "OPENMANUS_LIVE_BASE_URL", "https://default-openai.example/v1",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "dotenv-anthropic",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "dotenv-anthropic-key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1",
                "OPENMANUS_LIVE_GEMINI_MODEL", "dotenv-gemini",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "dotenv-gemini-key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=default-openai, baseUrl=https://default-openai.example/v1, apiKey=len=18,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:default-llm,baseUrl:default-llm,apiKey:default-llm"),
                "script should preview the backfilled default LLM OpenAI-compatible config");
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should backfill OpenAI live smoke env from default LLM dotenv values");
    }

    @Test
    void shouldBackfillOpenAiLiveEnvFromLegacyOpenAiDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-legacy-openai-live");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENAI_MODEL=legacy-openai
                OPENAI_API_KEY=legacy-openai-key
                OPENAI_BASE_URL=https://legacy-openai.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "legacy-openai",
                "OPENMANUS_LIVE_API_KEY", "legacy-openai-key",
                "OPENMANUS_LIVE_BASE_URL", "https://legacy-openai.example/v1");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=legacy-openai, baseUrl=https://legacy-openai.example/v1, apiKey=len=17,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:legacy-openai,baseUrl:legacy-openai,apiKey:legacy-openai"),
                "script should preview the backfilled legacy OpenAI-compatible config");
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should backfill OpenAI live smoke env from legacy OpenAI dotenv values");
    }

    @Test
    void shouldBackfillAnthropicAndGeminiLiveEnvFromProviderProfileDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-provider-profile-live");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=dotenv-openai
                OPENMANUS_LIVE_API_KEY=dotenv-openai-key
                OPENMANUS_LIVE_BASE_URL=https://dotenv-openai.example/v1
                OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL=dotenv-anthropic
                OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY=dotenv-anthropic-key
                OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL=https://anthropic.example/v1
                OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL=dotenv-gemini
                OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY=dotenv-gemini-key
                OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL=https://gemini.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "dotenv-openai",
                "OPENMANUS_LIVE_API_KEY", "dotenv-openai-key",
                "OPENMANUS_LIVE_BASE_URL", "https://dotenv-openai.example/v1",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "dotenv-anthropic",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "dotenv-anthropic-key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1",
                "OPENMANUS_LIVE_GEMINI_MODEL", "dotenv-gemini",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "dotenv-gemini-key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should backfill Anthropic and Gemini live smoke env from provider profile dotenv values");
    }

    @Test
    void shouldPreferExplicitShellEnvOverDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-dotenv-priority");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=dotenv-openai
                OPENMANUS_LIVE_API_KEY=dotenv-key
                OPENMANUS_LIVE_BASE_URL=https://dotenv.example/v1
                OPENMANUS_LIVE_ANTHROPIC_MODEL=dotenv-anthropic
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=dotenv-anthropic-key
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://anthropic.example/v1
                OPENMANUS_LIVE_GEMINI_MODEL=dotenv-gemini
                OPENMANUS_LIVE_GEMINI_API_KEY=dotenv-gemini-key
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://gemini.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "shell-openai",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "shell-anthropic",
                "OPENMANUS_LIVE_GEMINI_MODEL", "shell-gemini");

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "shell-openai"),
                Map.entry("OPENMANUS_LIVE_API_KEY", "shell-openai-key"),
                Map.entry("OPENMANUS_LIVE_BASE_URL", "https://shell-openai.example/v1"),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_MODEL", "shell-anthropic"),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_API_KEY", "shell-anthropic-key"),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://shell-anthropic.example/v1"),
                Map.entry("OPENMANUS_LIVE_GEMINI_MODEL", "shell-gemini"),
                Map.entry("OPENMANUS_LIVE_GEMINI_API_KEY", "shell-gemini-key"),
                Map.entry("OPENMANUS_LIVE_GEMINI_BASE_URL", "https://shell-gemini.example/v1")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should keep explicit shell variables instead of overwriting them from .env");
    }

    @Test
    void shouldBackfillBlankShellEnvFromDotenv() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-dotenv-blank-shell");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=dotenv-openai
                OPENMANUS_LIVE_API_KEY=dotenv-key
                OPENMANUS_LIVE_BASE_URL=https://dotenv.example/v1
                OPENMANUS_LIVE_ANTHROPIC_MODEL=dotenv-anthropic
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=dotenv-anthropic-key
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://anthropic.example/v1
                OPENMANUS_LIVE_GEMINI_MODEL=dotenv-gemini
                OPENMANUS_LIVE_GEMINI_API_KEY=dotenv-gemini-key
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://gemini.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "dotenv-openai",
                "OPENMANUS_LIVE_API_KEY", "dotenv-key",
                "OPENMANUS_LIVE_BASE_URL", "https://dotenv.example/v1",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "dotenv-anthropic",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "dotenv-anthropic-key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1",
                "OPENMANUS_LIVE_GEMINI_MODEL", "dotenv-gemini",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "dotenv-gemini-key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");

        ProcessResult result = run(repo, Map.ofEntries(
                Map.entry("OPENMANUS_LIVE_MODEL", "   "),
                Map.entry("OPENMANUS_LIVE_API_KEY", ""),
                Map.entry("OPENMANUS_LIVE_BASE_URL", " "),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_MODEL", ""),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_API_KEY", " "),
                Map.entry("OPENMANUS_LIVE_ANTHROPIC_BASE_URL", ""),
                Map.entry("OPENMANUS_LIVE_GEMINI_MODEL", " "),
                Map.entry("OPENMANUS_LIVE_GEMINI_API_KEY", ""),
                Map.entry("OPENMANUS_LIVE_GEMINI_BASE_URL", "   ")
        ), script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "blank shell env should be treated as missing and backfilled from .env");
    }

    @Test
    void shouldTreatBlankDotenvValuesAsMissing() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-blank-dotenv");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=
                OPENMANUS_LIVE_BASE_URL=https://dotenv.example/v1
                OPENMANUS_LIVE_API_KEY=dotenv-key
                OPENMANUS_LIVE_ANTHROPIC_MODEL=dotenv-anthropic
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=dotenv-anthropic-key
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://anthropic.example/v1
                OPENMANUS_LIVE_GEMINI_MODEL=dotenv-gemini
                OPENMANUS_LIVE_GEMINI_API_KEY=dotenv-gemini-key
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://gemini.example/v1
                """, StandardCharsets.UTF_8);
        writeFakeMvnwWithExitCode(repo, 9);

        ProcessResult result = run(repo, script.toString());

        assertEquals(1, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke effective OpenAI config: models=gpt-5.4,gpt-5,gpt-4o, baseUrl=https://dotenv.example/v1, apiKey=len=10,suffix=-key, caCertFile=<default-jvm-truststore>, source=models:built-in-fallback,baseUrl:live,apiKey:live"),
                "blank OpenAI-compatible model config should fall back to built-in model candidates");
        assertFalse(result.output.contains("ERROR: missing live smoke env vars"),
                "script should no longer reject blank OpenAI-compatible model vars when base URL and API key exist");
    }

    @Test
    void shouldIgnoreInlineCommentsInUnquotedDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-dotenv-inline-comments");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL=openai-model # preferred model
                OPENMANUS_LIVE_API_KEY=openai-key # credential note
                OPENMANUS_LIVE_BASE_URL=https://openai.example/v1 # endpoint
                OPENMANUS_LIVE_ANTHROPIC_MODEL=anthropic-model # preferred model
                OPENMANUS_LIVE_ANTHROPIC_API_KEY=anthropic-key # credential note
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL=https://anthropic.example/v1 # endpoint
                OPENMANUS_LIVE_GEMINI_MODEL=gemini-model # preferred model
                OPENMANUS_LIVE_GEMINI_API_KEY=gemini-key # credential note
                OPENMANUS_LIVE_GEMINI_BASE_URL=https://gemini.example/v1 # endpoint
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "openai-model",
                "OPENMANUS_LIVE_API_KEY", "openai-key",
                "OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "anthropic-model",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "anthropic-key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1",
                "OPENMANUS_LIVE_GEMINI_MODEL", "gemini-model",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "gemini-key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should strip inline comments from unquoted dotenv values");
    }

    @Test
    void shouldIgnoreTrailingCommentsAfterQuotedDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-dotenv-quoted-comments");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL="openai-model" # preferred model
                OPENMANUS_LIVE_API_KEY='openai-key' # credential note
                OPENMANUS_LIVE_BASE_URL="https://openai.example/v1" # endpoint
                OPENMANUS_LIVE_ANTHROPIC_MODEL='anthropic-model' # preferred model
                OPENMANUS_LIVE_ANTHROPIC_API_KEY="anthropic-key" # credential note
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL='https://anthropic.example/v1' # endpoint
                OPENMANUS_LIVE_GEMINI_MODEL="gemini-model" # preferred model
                OPENMANUS_LIVE_GEMINI_API_KEY='gemini-key' # credential note
                OPENMANUS_LIVE_GEMINI_BASE_URL="https://gemini.example/v1" # endpoint
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "openai-model",
                "OPENMANUS_LIVE_API_KEY", "openai-key",
                "OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "anthropic-model",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "anthropic-key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1",
                "OPENMANUS_LIVE_GEMINI_MODEL", "gemini-model",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "gemini-key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should strip trailing comments from quoted dotenv values");
    }

    @Test
    void shouldPreserveHashCharactersInsideQuotedDotenvValues() throws Exception {
        requireBash();
        Path repo = tempDir.resolve("repo-dotenv-quoted-hash");
        Path script = prepareScript(repo);
        Files.writeString(repo.resolve(".env"), """
                OPENMANUS_LIVE_MODEL="openai#model"
                OPENMANUS_LIVE_API_KEY='openai#key'
                OPENMANUS_LIVE_BASE_URL="https://openai.example/v1#compat"
                OPENMANUS_LIVE_ANTHROPIC_MODEL='anthropic#model'
                OPENMANUS_LIVE_ANTHROPIC_API_KEY="anthropic#key"
                OPENMANUS_LIVE_ANTHROPIC_BASE_URL='https://anthropic.example/v1#beta'
                OPENMANUS_LIVE_GEMINI_MODEL="gemini#model"
                OPENMANUS_LIVE_GEMINI_API_KEY='gemini#key'
                OPENMANUS_LIVE_GEMINI_BASE_URL="https://gemini.example/v1#flash"
                """, StandardCharsets.UTF_8);
        writeFakeMvnwAssertingEnv(repo,
                "OpenAiClientLiveSmokeTest,AnthropicClientLiveSmokeTest,GeminiClientLiveSmokeTest",
                "OPENMANUS_LIVE_MODEL", "openai#model",
                "OPENMANUS_LIVE_API_KEY", "openai#key",
                "OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1#compat",
                "OPENMANUS_LIVE_ANTHROPIC_MODEL", "anthropic#model",
                "OPENMANUS_LIVE_ANTHROPIC_API_KEY", "anthropic#key",
                "OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1#beta",
                "OPENMANUS_LIVE_GEMINI_MODEL", "gemini#model",
                "OPENMANUS_LIVE_GEMINI_API_KEY", "gemini#key",
                "OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1#flash");

        ProcessResult result = run(repo, script.toString());

        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("Live smoke summary: tests=1, failures=0, errors=0, skipped=0"),
                "script should keep # characters that are part of quoted dotenv values");
    }

    private Path prepareScript(Path repo) throws IOException {
        Path scriptsDir = repo.resolve("scripts");
        Files.createDirectories(scriptsDir);

        Path script = scriptsDir.resolve("run-live-smoke.sh");
        Files.writeString(script, Files.readString(SOURCE_SCRIPT), StandardCharsets.UTF_8);
        setExecutable(script);
        return script;
    }

    private void writeFakeMvnw(Path repo, String... reports) throws IOException {
        writeFakeMvnwWithExitCode(repo, 0, reports);
    }

    private void writeFakeMvnwWithExitCode(Path repo, int exitCode, String... reports) throws IOException {
        Path scriptsDir = repo.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path mvnwLocal = scriptsDir.resolve("mvnw-local.sh");

        StringBuilder body = new StringBuilder()
                .append("#!/usr/bin/env bash\n")
                .append("set -euo pipefail\n")
                .append("mkdir -p \"").append(repo.resolve("target/surefire-reports")).append("\"\n");

        for (int i = 0; i < reports.length; i++) {
            Path report = repo.resolve("target/surefire-reports/TEST-report-" + i + "LiveSmokeTest.xml");
            body.append("cat <<'EOF' > \"").append(report).append("\"\n")
                    .append(reports[i]).append('\n')
                    .append("EOF\n");
        }

        body.append("exit ").append(exitCode).append('\n');
        Files.writeString(mvnwLocal, body.toString(), StandardCharsets.UTF_8);
        setExecutable(mvnwLocal);
    }

    private void writeFakeMvnwExpectingTestSelection(Path repo, String expectedTests, String... reports)
            throws IOException {
        Path scriptsDir = repo.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path mvnwLocal = scriptsDir.resolve("mvnw-local.sh");

        StringBuilder body = new StringBuilder()
                .append("#!/usr/bin/env bash\n")
                .append("set -euo pipefail\n")
                .append("if [[ \"$*\" != *\"-Dtest=").append(expectedTests).append("\"* ]]; then\n")
                .append("  echo \"unexpected test selection: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("if [[ \"$*\" != *\"-Dgroups=live-smoke\"* ]]; then\n")
                .append("  echo \"missing live smoke groups selection: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("if [[ \"$*\" != *\"-Dopenmanus.liveSmoke.enabled=true\"* ]]; then\n")
                .append("  echo \"missing live smoke opt-in property: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("if [[ \"$*\" != *\"-Dsurefire.excludedGroups=\"* ]]; then\n")
                .append("  echo \"missing surefire excludedGroups override: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("mkdir -p \"").append(repo.resolve("target/surefire-reports")).append("\"\n");

        for (int i = 0; i < reports.length; i++) {
            Path report = repo.resolve("target/surefire-reports/TEST-report-" + i + "LiveSmokeTest.xml");
            body.append("cat <<'EOF' > \"").append(report).append("\"\n")
                    .append(reports[i]).append('\n')
                    .append("EOF\n");
        }

        body.append("exit 0\n");
        Files.writeString(mvnwLocal, body.toString(), StandardCharsets.UTF_8);
        setExecutable(mvnwLocal);
    }

    private void writeFakeMvnwAssertingEnv(Path repo,
                                           String expectedTests,
                                           String key,
                                           String expectedValue,
                                           String... extraKeyValues)
            throws IOException {
        Path scriptsDir = repo.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path mvnwLocal = scriptsDir.resolve("mvnw-local.sh");
        Path report = repo.resolve("target/surefire-reports/TEST-report-0LiveSmokeTest.xml");

        StringBuilder body = new StringBuilder()
                .append("#!/usr/bin/env bash\n")
                .append("set -euo pipefail\n")
                .append("if [[ \"$*\" != *\"-Dtest=").append(expectedTests).append("\"* ]]; then\n")
                .append("  echo \"unexpected test selection: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("if [[ \"$*\" != *\"-Dgroups=live-smoke\"* ]]; then\n")
                .append("  echo \"missing live smoke groups selection: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("if [[ \"$*\" != *\"-Dopenmanus.liveSmoke.enabled=true\"* ]]; then\n")
                .append("  echo \"missing live smoke opt-in property: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("if [[ \"$*\" != *\"-Dsurefire.excludedGroups=\"* ]]; then\n")
                .append("  echo \"missing surefire excludedGroups override: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("mkdir -p \"").append(repo.resolve("target/surefire-reports")).append("\"\n");

        appendEnvAssertion(body, key, expectedValue);
        for (int i = 0; i < extraKeyValues.length; i += 2) {
            appendEnvAssertion(body, extraKeyValues[i], extraKeyValues[i + 1]);
        }

        body.append("cat <<'EOF' > \"").append(report).append("\"\n")
                .append(reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, "")).append('\n')
                .append("EOF\n")
                .append("exit 0\n");

        Files.writeString(mvnwLocal, body.toString(), StandardCharsets.UTF_8);
        setExecutable(mvnwLocal);
    }

    private void writeFakeMvnwAssertingEnvContains(Path repo,
                                                   String expectedTests,
                                                   String key,
                                                   String expectedValueFragment,
                                                   String... extraKeyValues)
            throws IOException {
        Path scriptsDir = repo.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path mvnwLocal = scriptsDir.resolve("mvnw-local.sh");
        Path report = repo.resolve("target/surefire-reports/TEST-report-0LiveSmokeTest.xml");

        StringBuilder body = new StringBuilder()
                .append("#!/usr/bin/env bash\n")
                .append("set -euo pipefail\n")
                .append("if [[ \"$*\" != *\"-Dtest=").append(expectedTests).append("\"* ]]; then\n")
                .append("  echo \"unexpected test selection: $*\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n")
                .append("mkdir -p \"").append(repo.resolve("target/surefire-reports")).append("\"\n");

        appendEnvContainsAssertion(body, key, expectedValueFragment);
        for (int i = 0; i < extraKeyValues.length; i += 2) {
            appendEnvContainsAssertion(body, extraKeyValues[i], extraKeyValues[i + 1]);
        }

        body.append("cat <<'EOF' > \"").append(report).append("\"\n")
                .append(reportXml("OpenAiClientLiveSmokeTest", 1, 0, 0, 0, "")).append('\n')
                .append("EOF\n")
                .append("exit 0\n");

        Files.writeString(mvnwLocal, body.toString(), StandardCharsets.UTF_8);
        setExecutable(mvnwLocal);
    }

    private void appendEnvAssertion(StringBuilder body, String key, String expectedValue) {
        body.append("if [[ \"${").append(key).append(":-}\" != \"").append(expectedValue).append("\" ]]; then\n")
                .append("  echo \"unexpected ").append(key).append(": ${").append(key).append(":-}\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n");
    }

    private void appendEnvContainsAssertion(StringBuilder body, String key, String expectedValueFragment) {
        body.append("if [[ \"${").append(key).append(":-}\" != *\"").append(expectedValueFragment).append("\"* ]]; then\n")
                .append("  echo \"unexpected ").append(key).append(": ${").append(key).append(":-}\" >&2\n")
                .append("  exit 9\n")
                .append("fi\n");
    }

    private String reportXml(String suiteName, int tests, int failures, int errors, int skipped, String detail) {
        return "<testsuite name=\"" + suiteName + "\" tests=\"" + tests + "\" failures=\"" + failures
                + "\" errors=\"" + errors + "\" skipped=\"" + skipped + "\">\n"
                + detail + "\n"
                + "</testsuite>";
    }

    private ProcessResult run(Path workDir, String... command) throws IOException, InterruptedException {
        return run(workDir, Map.of(), command);
    }

    private ProcessResult run(Path workDir, Map<String, String> env, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true);
        clearLiveSmokeEnv(builder.environment());
        builder.environment().put("PATH", "/usr/bin:/bin");
        builder.environment().putAll(env);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new ProcessResult(exit, output);
    }

    private void clearLiveSmokeEnv(Map<String, String> environment) {
        environment.keySet().removeIf(this::isLiveSmokeEnvKey);
    }

    private boolean isLiveSmokeEnvKey(String key) {
        for (String prefix : LIVE_SMOKE_ENV_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> fullLiveEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OPENMANUS_LIVE_MODEL", "openai-model");
        env.put("OPENMANUS_LIVE_BASE_URL", "https://openai.example/v1");
        env.put("OPENMANUS_LIVE_API_KEY", "openai-key");
        env.put("OPENMANUS_LIVE_ANTHROPIC_MODEL", "anthropic-model");
        env.put("OPENMANUS_LIVE_ANTHROPIC_BASE_URL", "https://anthropic.example/v1");
        env.put("OPENMANUS_LIVE_ANTHROPIC_API_KEY", "anthropic-key");
        env.put("OPENMANUS_LIVE_GEMINI_MODEL", "gemini-model");
        env.put("OPENMANUS_LIVE_GEMINI_BASE_URL", "https://gemini.example/v1");
        env.put("OPENMANUS_LIVE_GEMINI_API_KEY", "gemini-key");
        return env;
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private static void setExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            assertTrue(path.toFile().setExecutable(true, true),
                    "failed to set executable permission for " + path);
        }
    }

    private String requireBash() {
        Path bashBin = Path.of("/bin/bash");
        if (Files.isExecutable(bashBin)) {
            return bashBin.toString();
        }

        Path bashUsrBin = Path.of("/usr/bin/bash");
        if (Files.isExecutable(bashUsrBin)) {
            return bashUsrBin.toString();
        }

        String discovered = discoverBashFromPath();
        Assumptions.assumeTrue(discovered != null, "bash is required for live smoke script integration tests");
        return discovered;
    }

    private String discoverBashFromPath() {
        try {
            Process process = new ProcessBuilder("sh", "-lc", "command -v bash")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = process.waitFor();
            if (exit != 0 || output.isEmpty()) {
                return null;
            }
            String firstLine = output.lines().findFirst().orElse("").trim();
            if (firstLine.isEmpty()) {
                return null;
            }
            Path bashPath = Path.of(firstLine);
            return Files.isExecutable(bashPath) ? bashPath.toString() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
