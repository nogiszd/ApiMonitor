package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigLoader;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.SmtpConfig;
import com.dawmaz.apimonitor.config.SmtpConfig.SmtpTemplateConfig;
import com.dawmaz.apimonitor.config.SmtpConfig.SmtpTemplatesGroup;
import com.dawmaz.apimonitor.i18n.TranslationProvider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Route(value = "smtp", layout = MainLayout.class)
public class SmtpView extends VerticalLayout implements LocaleChangeObserver, HasDynamicTitle {

    private static final List<String> EVENT_KEYS = List.of(
            "domainCertificateExpirationWarning", "domainCertificateExpired"
    );

    private final ConfigStateService configStateService;
    private final ConfigLoader configLoader;

    private final H2 heading = new H2();
    private final Checkbox enabledField = new Checkbox();
    private final TextField hostField = new TextField();
    private final IntegerField portField = new IntegerField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField fromField = new TextField();
    private final TabSheet languageTabs = new TabSheet();
    private final Button saveButton = new Button();
    private final Map<String, LanguageEditor> editors = new LinkedHashMap<>();

    public SmtpView(ConfigStateService configStateService, ConfigLoader configLoader) {
        this.configStateService = configStateService;
        this.configLoader = configLoader;

        setWidthFull();
        setPadding(true);

        heading.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);

        hostField.setWidthFull();
        portField.setMin(1);
        portField.setMax(65_535);
        usernameField.setWidthFull();
        passwordField.setWidthFull();
        fromField.setWidthFull();

        FormLayout connectionForm = new FormLayout();
        connectionForm.add(enabledField, fromField, hostField, portField, usernameField, passwordField);
        connectionForm.setColspan(enabledField, 2);
        connectionForm.setColspan(fromField, 2);
        connectionForm.setColspan(usernameField, 2);
        connectionForm.setColspan(passwordField, 2);
        connectionForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("40rem", 2)
        );

        for (Locale locale : TranslationProvider.LOCALES) {
            String code = locale.getLanguage();
            LanguageEditor editor = new LanguageEditor();
            editors.put(code, editor);
            languageTabs.add(languageLabel(locale), editor.layout);
        }
        languageTabs.setWidthFull();

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        HorizontalLayout footer = new HorizontalLayout(saveButton);
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.END);

        add(heading, connectionForm, languageTabs, footer);
        loadFromConfig();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("nav.smtp") + " | API Monitor";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        heading.setText(getTranslation("smtp.title"));
        enabledField.setLabel(getTranslation("smtp.field.enabled"));
        hostField.setLabel(getTranslation("smtp.field.host"));
        portField.setLabel(getTranslation("smtp.field.port"));
        usernameField.setLabel(getTranslation("smtp.field.username"));
        passwordField.setLabel(getTranslation("smtp.field.password"));
        fromField.setLabel(getTranslation("smtp.field.from"));
        saveButton.setText(getTranslation("smtp.button.save"));
        editors.forEach((code, editor) -> editor.applyTranslations());
    }

    private void loadFromConfig() {
        SmtpConfig smtp = configStateService.current().smtp();
        if (smtp == null) {
            enabledField.setValue(false);
            hostField.setValue("");
            portField.setValue(587);
            usernameField.setValue("");
            passwordField.setValue("");
            fromField.setValue("");
            editors.values().forEach(LanguageEditor::clear);
            return;
        }
        enabledField.setValue(Boolean.TRUE.equals(smtp.enabled()));
        hostField.setValue(smtp.host() == null ? "" : smtp.host());
        portField.setValue(smtp.port() == null ? 587 : smtp.port());
        usernameField.setValue(smtp.username() == null ? "" : smtp.username());
        passwordField.setValue(smtp.password() == null ? "" : smtp.password());
        fromField.setValue(smtp.from() == null ? "" : smtp.from());

        Map<String, SmtpTemplatesGroup> templates = smtp.templates();
        editors.forEach((code, editor) -> {
            SmtpTemplatesGroup group = templates == null ? null : templates.get(code);
            editor.populate(group);
        });
    }

    private void save() {
        AppConfig current = configStateService.current();

        Map<String, SmtpTemplatesGroup> updatedTemplates = new LinkedHashMap<>();
        editors.forEach((code, editor) -> updatedTemplates.put(code, editor.toGroup()));

        SmtpConfig updatedSmtp = new SmtpConfig(
                enabledField.getValue(),
                nullIfBlank(hostField.getValue()),
                portField.getValue(),
                nullIfBlank(usernameField.getValue()),
                nullIfBlank(passwordField.getValue()),
                nullIfBlank(fromField.getValue()),
                updatedTemplates
        );

        AppConfig updated = new AppConfig(
                current.language(),
                current.intervalSeconds(),
                current.timeoutSeconds(),
                current.incidentFailureThreshold(),
                current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(),
                current.discord(),
                updatedSmtp,
                current.endpoints(),
                current.domains()
        );

        try {
            configLoader.save(updated);
            Notification n = Notification.show(
                    getTranslation("smtp.notify.saved"), 3000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            Notification n = Notification.show(
                    getTranslation("smtp.notify.saveFailed", ex.getMessage()),
                    4000, Notification.Position.MIDDLE);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private static String languageLabel(Locale locale) {
        return TranslationProvider.POLISH.getLanguage().equals(locale.getLanguage()) ? "PL" : "EN";
    }

    private static String nullIfBlank(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private final class LanguageEditor {
        final VerticalLayout layout = new VerticalLayout();
        final Map<String, TemplateEditor> templates = new LinkedHashMap<>();

        LanguageEditor() {
            layout.setPadding(false);
            layout.setSpacing(true);
            for (String key : EVENT_KEYS) {
                TemplateEditor templateEditor = new TemplateEditor(key);
                templates.put(key, templateEditor);
                layout.add(templateEditor.details);
            }
        }

        void populate(SmtpTemplatesGroup group) {
            templates.get("domainCertificateExpirationWarning").populate(group == null ? null : group.domainCertificateExpirationWarning());
            templates.get("domainCertificateExpired").populate(group == null ? null : group.domainCertificateExpired());
        }

        void clear() {
            templates.values().forEach(t -> t.populate(null));
        }

        SmtpTemplatesGroup toGroup() {
            return new SmtpTemplatesGroup(
                    templates.get("domainCertificateExpirationWarning").toTemplate(),
                    templates.get("domainCertificateExpired").toTemplate()
            );
        }

        void applyTranslations() {
            templates.values().forEach(TemplateEditor::applyTranslations);
        }
    }

    private final class TemplateEditor {
        final String eventKey;
        final Details details;
        final TextField subjectField = new TextField();
        final TextArea bodyField = new TextArea();
        final Span previewLabel = new Span();
        final Div previewFrame = new Div();

        TemplateEditor(String eventKey) {
            this.eventKey = eventKey;

            subjectField.setWidthFull();
            bodyField.setWidthFull();
            bodyField.setMinHeight("16em");
            bodyField.getStyle().set("font-family", "var(--lumo-font-family-monospace, monospace)");
            bodyField.addValueChangeListener(e -> updatePreview(e.getValue()));

            previewLabel.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("margin-top", "var(--lumo-space-s)");

            previewFrame.setWidthFull();
            previewFrame.getStyle()
                    .set("border", "1px solid var(--lumo-contrast-20pct)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("padding", "var(--lumo-space-m)")
                    .set("background-color", "var(--lumo-base-color)")
                    .set("min-height", "8em")
                    .set("overflow", "auto")
                    .set("box-sizing", "border-box");

            FormLayout form = new FormLayout(subjectField, bodyField);
            form.setColspan(subjectField, 2);
            form.setColspan(bodyField, 2);
            form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

            VerticalLayout content = new VerticalLayout(form, previewLabel, previewFrame);
            content.setPadding(false);
            content.setSpacing(false);
            content.setWidthFull();

            details = new Details();
            details.setWidthFull();
            details.add(content);
            applyTranslations();
        }

        void applyTranslations() {
            details.setSummaryText(getTranslation("smtp.section." + eventKey));
            subjectField.setLabel(getTranslation("smtp.field.subject"));
            bodyField.setLabel(getTranslation("smtp.field.body"));
            bodyField.setHelperText(getTranslation("smtp.field.body.helper"));
            previewLabel.setText(getTranslation("smtp.field.body.preview"));
        }

        void populate(SmtpTemplateConfig template) {
            String body = template == null || template.body() == null ? "" : template.body();
            String subject = template == null || template.subject() == null ? "" : template.subject();
            subjectField.setValue(subject);
            bodyField.setValue(body);
            updatePreview(body);
        }

        void updatePreview(String html) {
            previewFrame.getElement().setProperty("innerHTML", html == null ? "" : html);
        }

        SmtpTemplateConfig toTemplate() {
            return new SmtpTemplateConfig(
                    nullIfBlankLocal(subjectField.getValue()),
                    nullIfBlankLocal(bodyField.getValue())
            );
        }

        private String nullIfBlankLocal(String value) {
            if (value == null) return null;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
