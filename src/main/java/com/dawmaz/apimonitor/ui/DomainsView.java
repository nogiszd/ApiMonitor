package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigLoader;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.config.DomainConfig.DomainWarningsConfig;
import com.dawmaz.apimonitor.config.DomainConfig.EmailWarningConfig;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Route(value = "domains", layout = MainLayout.class)
public class DomainsView extends VerticalLayout implements LocaleChangeObserver, HasDynamicTitle {

    private final ConfigStateService configStateService;
    private final ConfigLoader configLoader;

    private final H2 header = new H2();
    private final Button addButton = new Button();
    private final Grid<DomainConfig> grid = new Grid<>();

    private Grid.Column<DomainConfig> domainColumn;
    private Grid.Column<DomainConfig> thresholdColumn;
    private Grid.Column<DomainConfig> actionsColumn;

    public DomainsView(ConfigStateService configStateService, ConfigLoader configLoader) {
        this.configStateService = configStateService;
        this.configLoader = configLoader;

        setSizeFull();
        setPadding(true);

        header.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openEditDialog(null));

        HorizontalLayout toolbar = new HorizontalLayout(header, addButton);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(header);

        add(toolbar);
        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        domainColumn = grid.addColumn(DomainConfig::domain).setAutoWidth(true).setSortable(true);
        thresholdColumn = grid.addColumn(d -> d.thresholdDays() + "").setAutoWidth(true);
        actionsColumn = grid.addComponentColumn(d -> {
            Button edit = new Button(getTranslation("domains.button.edit"), e -> openEditDialog(d));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button delete = new Button(getTranslation("domains.button.delete"), e -> confirmDelete(d));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(edit, delete);
        }).setAutoWidth(true).setFlexGrow(0);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("nav.domains") + " | API Monitor";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        header.setText(getTranslation("domains.title"));
        addButton.setText(getTranslation("domains.addButton"));
        domainColumn.setHeader(getTranslation("domains.col.domain"));
        thresholdColumn.setHeader(getTranslation("domains.col.thresholdDays"));
        actionsColumn.setHeader(getTranslation("domains.col.actions"));
        refreshGrid();
    }

    private void refreshGrid() {
        List<DomainConfig> domains = configStateService.current().domains();
        grid.setItems(domains == null ? new ArrayList<>() : new ArrayList<>(domains));
    }

    private void openEditDialog(DomainConfig existing) {
        Dialog dialog = new Dialog();
        dialog.setWidth("480px");
        dialog.setHeaderTitle(getTranslation(existing == null ? "domains.dialog.add" : "domains.dialog.edit"));

        TextField domainField = new TextField(getTranslation("domains.field.domain"));
        domainField.setWidthFull();
        domainField.setPlaceholder(getTranslation("domains.field.domain.placeholder"));

        NumberField thresholdField = new NumberField(getTranslation("domains.field.thresholdDays"));
        thresholdField.setWidthFull();
        thresholdField.setMin(0);
        thresholdField.setStep(1);
        thresholdField.setHelperText(getTranslation("domains.field.thresholdDays.helper"));

        NumberField checkIntervalField = new NumberField(getTranslation("domains.field.checkIntervalSeconds"));
        checkIntervalField.setWidthFull();
        checkIntervalField.setMin(60);
        checkIntervalField.setStep(60);
        checkIntervalField.setHelperText(getTranslation("domains.field.checkIntervalSeconds.helper"));

        Checkbox emailEnabledField = new Checkbox(getTranslation("domains.field.emailEnabled"));
        TextField emailToField = new TextField(getTranslation("domains.field.emailTo"));
        emailToField.setWidthFull();
        emailToField.setPlaceholder(getTranslation("domains.field.emailTo.placeholder"));

        if (existing != null) {
            domainField.setValue(existing.domain());
            domainField.setReadOnly(true);
            thresholdField.setValue((double) existing.thresholdDays());
            checkIntervalField.setValue((double) (existing.checkIntervalSeconds() == null ? 86_400L : existing.checkIntervalSeconds()));
            EmailWarningConfig email = existing.warnings() == null ? null : existing.warnings().email();
            emailEnabledField.setValue(email != null && Boolean.TRUE.equals(email.enabled()));
            emailToField.setValue(email == null || email.to() == null ? "" : email.to());
        } else {
            thresholdField.setValue(7.0);
            checkIntervalField.setValue(86_400.0);
            emailEnabledField.setValue(false);
            emailToField.setValue("");
        }

        FormLayout form = new FormLayout(domainField, thresholdField, checkIntervalField,
                emailEnabledField, emailToField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("domains.button.save"), e -> {
            String error = validateForm(domainField, thresholdField, checkIntervalField, emailEnabledField, emailToField);
            if (error != null) {
                showError(error);
                return;
            }
            DomainConfig updated = buildDomainConfig(domainField, thresholdField, checkIntervalField,
                    emailEnabledField, emailToField);
            saveDomain(existing, updated);
            dialog.close();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("domains.button.cancel"), e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private String validateForm(TextField domainField, NumberField thresholdField,
                                NumberField checkIntervalField,
                                Checkbox emailEnabledField, TextField emailToField) {
        if (domainField.getValue() == null || domainField.getValue().isBlank()) {
            return getTranslation("domains.validate.domainRequired");
        }
        if (domainField.getValue().contains(" ")) {
            return getTranslation("domains.validate.domainNoSpaces");
        }
        if (thresholdField.getValue() == null || thresholdField.getValue() < 0) {
            return getTranslation("domains.validate.thresholdDays");
        }
        if (checkIntervalField.getValue() == null || checkIntervalField.getValue() < 60) {
            return getTranslation("domains.validate.checkInterval");
        }
        if (Boolean.TRUE.equals(emailEnabledField.getValue())
                && (emailToField.getValue() == null || emailToField.getValue().isBlank())) {
            return getTranslation("domains.validate.emailToRequired");
        }
        return null;
    }

    private DomainConfig buildDomainConfig(TextField domainField, NumberField thresholdField,
                                           NumberField checkIntervalField,
                                           Checkbox emailEnabledField, TextField emailToField) {
        EmailWarningConfig email = new EmailWarningConfig(
                Boolean.TRUE.equals(emailEnabledField.getValue()),
                emailToField.getValue() == null ? "" : emailToField.getValue().trim()
        );
        DomainWarningsConfig warnings = new DomainWarningsConfig(thresholdField.getValue().intValue(), email);
        return new DomainConfig(
                domainField.getValue().trim(),
                checkIntervalField.getValue().longValue(),
                warnings
        );
    }

    private void saveDomain(DomainConfig existing, DomainConfig updated) {
        AppConfig current = configStateService.current();
        List<DomainConfig> domains = current.domains() == null
                ? new ArrayList<>()
                : new ArrayList<>(current.domains());

        if (existing == null) {
            boolean exists = domains.stream().anyMatch(d -> d.domain().equalsIgnoreCase(updated.domain()));
            if (exists) {
                showError(getTranslation("domains.validate.domainExists", updated.domain()));
                return;
            }
            domains.add(updated);
        } else {
            int idx = IntStream.range(0, domains.size())
                    .filter(i -> domains.get(i).domain().equals(existing.domain()))
                    .findFirst().orElse(-1);
            if (idx >= 0) domains.set(idx, updated);
        }

        writeConfig(new AppConfig(
                current.language(), current.intervalSeconds(), current.timeoutSeconds(),
                current.incidentFailureThreshold(), current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(), current.discord(), current.smtp(),
                current.endpoints(), domains));
    }

    private void confirmDelete(DomainConfig d) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(getTranslation("domains.delete.header", d.domain()));
        dialog.setText(getTranslation("domains.delete.text"));
        dialog.setCancelable(true);
        dialog.setConfirmText(getTranslation("domains.button.delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> deleteDomain(d));
        dialog.open();
    }

    private void deleteDomain(DomainConfig domain) {
        AppConfig current = configStateService.current();
        List<DomainConfig> domains = current.domains() == null
                ? new ArrayList<>()
                : current.domains().stream()
                        .filter(d -> !d.domain().equals(domain.domain()))
                        .collect(Collectors.toCollection(ArrayList::new));
        writeConfig(new AppConfig(
                current.language(), current.intervalSeconds(), current.timeoutSeconds(),
                current.incidentFailureThreshold(), current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(), current.discord(), current.smtp(),
                current.endpoints(), domains));
    }

    private void writeConfig(AppConfig config) {
        try {
            configLoader.save(config);
            refreshGrid();
            Notification n = Notification.show(getTranslation("domains.notify.saved"), 3000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            showError(getTranslation("domains.notify.saveFailed", ex.getMessage()));
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
