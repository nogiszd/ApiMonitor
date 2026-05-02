package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigLoader;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.DiscordConfig;
import com.dawmaz.apimonitor.config.DiscordConfig.DiscordMessageTemplateConfig;
import com.dawmaz.apimonitor.config.DiscordConfig.DiscordMessagesGroup;
import com.dawmaz.apimonitor.i18n.TranslationProvider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
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

@Route(value = "discord", layout = MainLayout.class)
public class DiscordView extends VerticalLayout implements LocaleChangeObserver, HasDynamicTitle {

    private static final List<String> EVENT_KEYS = List.of(
            "incidentStart", "incidentRecovered", "slowResponse",
            "domainCertificateExpirationWarning", "domainCertificateExpired"
    );

    private final ConfigStateService configStateService;
    private final ConfigLoader configLoader;

    private final H2 heading = new H2();
    private final Checkbox enabledField = new Checkbox();
    private final TextField webhookUrlField = new TextField();
    private final TabSheet languageTabs = new TabSheet();
    private final Button saveButton = new Button();
    private final Map<String, LanguageEditor> editors = new LinkedHashMap<>();

    public DiscordView(ConfigStateService configStateService, ConfigLoader configLoader) {
        this.configStateService = configStateService;
        this.configLoader = configLoader;

        setWidthFull();
        setPadding(true);

        heading.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);

        webhookUrlField.setWidthFull();

        FormLayout connectionForm = new FormLayout();
        connectionForm.add(enabledField, webhookUrlField);
        connectionForm.setColspan(webhookUrlField, 2);
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
        return getTranslation("nav.discord") + " | API Monitor";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        heading.setText(getTranslation("discord.title"));
        enabledField.setLabel(getTranslation("discord.field.enabled"));
        webhookUrlField.setLabel(getTranslation("discord.field.webhookUrl"));
        webhookUrlField.setPlaceholder(getTranslation("discord.field.webhookUrl.placeholder"));
        saveButton.setText(getTranslation("discord.button.save"));
        editors.forEach((code, editor) -> editor.applyTranslations());
    }

    private void loadFromConfig() {
        DiscordConfig discord = configStateService.current().discord();
        if (discord == null) {
            enabledField.setValue(false);
            webhookUrlField.setValue("");
            editors.values().forEach(LanguageEditor::clear);
            return;
        }
        enabledField.setValue(discord.enabled());
        webhookUrlField.setValue(discord.webhookUrl() == null ? "" : discord.webhookUrl());
        Map<String, DiscordMessagesGroup> messages = discord.messages();
        editors.forEach((code, editor) -> {
            DiscordMessagesGroup group = messages == null ? null : messages.get(code);
            editor.populate(group);
        });
    }

    private void save() {
        AppConfig current = configStateService.current();

        Map<String, DiscordMessagesGroup> updatedMessages = new LinkedHashMap<>();
        editors.forEach((code, editor) -> updatedMessages.put(code, editor.toGroup()));

        DiscordConfig updatedDiscord = new DiscordConfig(
                enabledField.getValue(),
                webhookUrlField.getValue() == null ? "" : webhookUrlField.getValue().trim(),
                updatedMessages
        );

        AppConfig updated = new AppConfig(
                current.language(),
                current.intervalSeconds(),
                current.timeoutSeconds(),
                current.incidentFailureThreshold(),
                current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(),
                updatedDiscord,
                current.smtp(),
                current.endpoints(),
                current.domains()
        );

        try {
            configLoader.save(updated);
            Notification n = Notification.show(
                    getTranslation("discord.notify.saved"), 3000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            Notification n = Notification.show(
                    getTranslation("discord.notify.saveFailed", ex.getMessage()),
                    4000, Notification.Position.MIDDLE);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private static String languageLabel(Locale locale) {
        return TranslationProvider.POLISH.getLanguage().equals(locale.getLanguage()) ? "PL" : "EN";
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

        void populate(DiscordMessagesGroup group) {
            templates.get("incidentStart").populate(group == null ? null : group.incidentStart());
            templates.get("incidentRecovered").populate(group == null ? null : group.incidentRecovered());
            templates.get("slowResponse").populate(group == null ? null : group.slowResponse());
            templates.get("domainCertificateExpirationWarning").populate(group == null ? null : group.domainCertificateExpirationWarning());
            templates.get("domainCertificateExpired").populate(group == null ? null : group.domainCertificateExpired());
        }

        void clear() {
            templates.values().forEach(t -> t.populate(null));
        }

        DiscordMessagesGroup toGroup() {
            return new DiscordMessagesGroup(
                    templates.get("incidentStart").toTemplate(),
                    templates.get("incidentRecovered").toTemplate(),
                    templates.get("slowResponse").toTemplate(),
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
        final H3 sectionTitle = new H3();
        final TextField titleField = new TextField();
        final TextArea descriptionField = new TextArea();
        final TextField colorField = new TextField();

        TemplateEditor(String eventKey) {
            this.eventKey = eventKey;

            titleField.setWidthFull();
            descriptionField.setWidthFull();
            descriptionField.setMinHeight("6em");
            colorField.setWidthFull();
            colorField.setPlaceholder("E74C3C");

            FormLayout form = new FormLayout(titleField, descriptionField, colorField);
            form.setColspan(titleField, 2);
            form.setColspan(descriptionField, 2);
            form.setColspan(colorField, 2);
            form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

            sectionTitle.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.NONE);

            details = new Details();
            details.setWidthFull();
            details.add(form);
            applyTranslations();
        }

        void applyTranslations() {
            details.setSummaryText(getTranslation("discord.section." + eventKey));
            titleField.setLabel(getTranslation("discord.field.title"));
            descriptionField.setLabel(getTranslation("discord.field.description"));
            colorField.setLabel(getTranslation("discord.field.color"));
            colorField.setHelperText(getTranslation("discord.field.color.helper"));
        }

        void populate(DiscordMessageTemplateConfig template) {
            if (template == null) {
                titleField.setValue("");
                descriptionField.setValue("");
                colorField.setValue("");
                return;
            }
            titleField.setValue(template.title() == null ? "" : template.title());
            descriptionField.setValue(template.description() == null ? "" : template.description());
            colorField.setValue(template.color() == null ? "" : template.color());
        }

        DiscordMessageTemplateConfig toTemplate() {
            return new DiscordMessageTemplateConfig(
                    nullIfBlank(titleField.getValue()),
                    nullIfBlank(descriptionField.getValue()),
                    nullIfBlank(colorField.getValue())
            );
        }

        private String nullIfBlank(String value) {
            if (value == null) return null;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
