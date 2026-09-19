package com.nextgentechforge.springai.tools;

import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DevOpsTools {
    private static final Map<String, ServiceInfo> SERVICES = Map.of(
            "payment-service", new ServiceInfo("payment-service", "2.4.1", "RUNNING", "production", true),
            "order-service", new ServiceInfo("order-service", "1.8.3", "RUNNING", "production", true),
            "notification-service", new ServiceInfo("notification-service", "3.1.0", "DEGRADED", "production", true));

    @Tool(description = "Get simulated current service status. Read-only demo data, not live infrastructure.")
    public ServiceInfo getServiceStatus(@ToolParam(description = "Service name, e.g. payment-service") String service) {
        String name = normalize(service);
        return SERVICES.getOrDefault(name, new ServiceInfo(name, "UNKNOWN", "UNKNOWN", "UNKNOWN", true));
    }

    @Tool(description = "Get the currently deployed version of a service from simulated demo data.")
    public ServiceInfo getDeploymentVersion(@ToolParam(description = "Service name, e.g. payment-service") String service) {
        return getServiceStatus(service);
    }

    @Tool(description = "Get simulated environment health. Only production has demo observations.")
    public EnvironmentHealth getEnvironmentHealth(@ToolParam(description = "Environment name") String environment) {
        String name = normalize(environment);
        return new EnvironmentHealth(name, name.equals("production") ? "DEGRADED" : "UNKNOWN",
                name.equals("production") ? "notification-service has elevated delivery latency; payment and order are running"
                        : "No demo observations for this environment", true);
    }

    private static String normalize(String value) {
        return value == null ? "unknown" : value.strip().toLowerCase(Locale.ROOT);
    }

    public record ServiceInfo(String service, String version, String status, String environment, boolean simulated) { }
    public record EnvironmentHealth(String environment, String status, String detail, boolean simulated) { }
}
