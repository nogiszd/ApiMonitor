package com.dawmaz.apimonitor.config;

public record DomainConfig(
        String domain,
        Long checkIntervalSeconds,
        DomainWarningsConfig warnings
) {

    public int thresholdDays() {
        if (warnings == null || warnings.expirationThresholdDays() == null) {
            return 0;
        }
        return warnings.expirationThresholdDays();
    }

    public record DomainWarningsConfig(
            Integer expirationThresholdDays,
            EmailWarningConfig email
    ) {
    }

    public record EmailWarningConfig(
            Boolean enabled,
            String to
    ) {
    }
}
