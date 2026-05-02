package com.dawmaz.apimonitor.config;

import java.util.Map;

public record SmtpConfig(
        Boolean enabled,
        String host,
        Integer port,
        String username,
        String password,
        String from,
        Map<String, SmtpTemplatesGroup> templates
) {

    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    public SmtpTemplatesGroup templatesFor(String language) {
        if (templates == null) {
            return null;
        }
        SmtpTemplatesGroup group = language == null ? null : templates.get(language);
        if (group != null) {
            return group;
        }
        return templates.get("en");
    }

    public record SmtpTemplatesGroup(
            SmtpTemplateConfig domainCertificateExpirationWarning,
            SmtpTemplateConfig domainCertificateExpired
    ) {
    }

    public record SmtpTemplateConfig(
            String subject,
            String body
    ) {
    }
}
