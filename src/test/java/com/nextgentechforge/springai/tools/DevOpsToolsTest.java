package com.nextgentechforge.springai.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import static org.assertj.core.api.Assertions.*;

class DevOpsToolsTest {
    private final DevOpsTools tools = new DevOpsTools();
    @Test void reportsKnownServices() {
        assertThat(tools.getDeploymentVersion(" Payment-Service ").version()).isEqualTo("2.4.1");
        assertThat(tools.getServiceStatus("order-service").status()).isEqualTo("RUNNING");
        assertThat(tools.getServiceStatus("notification-service").status()).isEqualTo("DEGRADED");
        assertThat(tools.getServiceStatus("payment-service").simulated()).isTrue();
    }
    @Test void unknownNamesAreNeverInvented() {
        assertThat(tools.getServiceStatus("missing").status()).isEqualTo("UNKNOWN");
        assertThat(tools.getServiceStatus(null).version()).isEqualTo("UNKNOWN");
        assertThat(tools.getEnvironmentHealth("staging").status()).isEqualTo("UNKNOWN");
    }
    @Test void environmentReflectsDegradedNotificationService() {
        assertThat(tools.getEnvironmentHealth("PRODUCTION").status()).isEqualTo("DEGRADED");
    }
    @Test void springAiCanDiscoverAndInvokeAnnotatedTool() {
        var callback = java.util.Arrays.stream(ToolCallbacks.from(tools))
                .filter(t -> t.getToolDefinition().name().equals("getDeploymentVersion")).findFirst().orElseThrow();
        assertThat(callback.call("{\"service\":\"payment-service\"}")).contains("2.4.1", "production", "true");
    }
}
