package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigLoader;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.EndpointConfig;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Route(value = "hosts", layout = MainLayout.class)
public class HostsView extends VerticalLayout implements LocaleChangeObserver, HasDynamicTitle {

    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final ConfigStateService configStateService;
    private final ConfigLoader configLoader;
    private final H2 header = new H2();
    private final Button addButton = new Button();
    private final Grid<EndpointConfig> grid = new Grid<>();

    private Grid.Column<EndpointConfig> idColumn;
    private Grid.Column<EndpointConfig> nameColumn;
    private Grid.Column<EndpointConfig> urlColumn;
    private Grid.Column<EndpointConfig> methodColumn;
    private Grid.Column<EndpointConfig> expectedCodesColumn;
    private Grid.Column<EndpointConfig> slowThresholdColumn;
    private Grid.Column<EndpointConfig> actionsColumn;

    public HostsView(ConfigStateService configStateService, ConfigLoader configLoader) {
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
        idColumn = grid.addColumn(EndpointConfig::id).setAutoWidth(true).setSortable(true);
        nameColumn = grid.addColumn(EndpointConfig::name).setAutoWidth(true).setSortable(true);
        urlColumn = grid.addColumn(EndpointConfig::url).setFlexGrow(1);
        methodColumn = grid.addColumn(EndpointConfig::method).setAutoWidth(true);
        expectedCodesColumn = grid.addColumn(ep -> IntStream.of(ep.expectedStatuses())
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "))).setAutoWidth(true);
        slowThresholdColumn = grid.addColumn(ep -> ep.slowThresholdMs() + " ms").setAutoWidth(true);
        actionsColumn = grid.addComponentColumn(ep -> {
            Button edit = new Button(getTranslation("hosts.button.edit"), e -> openEditDialog(ep));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button delete = new Button(getTranslation("hosts.button.delete"), e -> confirmDelete(ep));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(edit, delete);
        }).setAutoWidth(true).setFlexGrow(0);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("nav.hosts") + " | API Monitor";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        header.setText(getTranslation("hosts.title"));
        addButton.setText(getTranslation("hosts.addButton"));
        idColumn.setHeader(getTranslation("hosts.col.id"));
        nameColumn.setHeader(getTranslation("hosts.col.name"));
        urlColumn.setHeader(getTranslation("hosts.col.url"));
        methodColumn.setHeader(getTranslation("hosts.col.method"));
        expectedCodesColumn.setHeader(getTranslation("hosts.col.expectedCodes"));
        slowThresholdColumn.setHeader(getTranslation("hosts.col.slowThreshold"));
        actionsColumn.setHeader(getTranslation("hosts.col.actions"));
        refreshGrid();
    }

    private void refreshGrid() {
        grid.setItems(new ArrayList<>(configStateService.current().endpoints()));
    }

    private void openEditDialog(EndpointConfig existing) {
        Dialog dialog = new Dialog();
        dialog.setWidth("480px");
        dialog.setHeaderTitle(getTranslation(existing == null ? "hosts.dialog.add" : "hosts.dialog.edit"));

        TextField idField = new TextField(getTranslation("hosts.field.id"));
        idField.setWidthFull();
        idField.setPlaceholder(getTranslation("hosts.field.id.placeholder"));
        idField.setHelperText(getTranslation("hosts.field.id.helper"));

        TextField nameField = new TextField(getTranslation("hosts.field.name"));
        nameField.setWidthFull();
        nameField.setPlaceholder(getTranslation("hosts.field.name.placeholder"));

        TextField urlField = new TextField(getTranslation("hosts.field.url"));
        urlField.setWidthFull();
        urlField.setPlaceholder(getTranslation("hosts.field.url.placeholder"));

        Select<String> methodSelect = new Select<>();
        methodSelect.setLabel(getTranslation("hosts.field.method"));
        methodSelect.setItems(HTTP_METHODS);
        methodSelect.setWidthFull();

        TextField expectedStatusesField = new TextField(getTranslation("hosts.field.statuses"));
        expectedStatusesField.setWidthFull();
        expectedStatusesField.setPlaceholder(getTranslation("hosts.field.statuses.placeholder"));
        expectedStatusesField.setHelperText(getTranslation("hosts.field.statuses.helper"));

        NumberField slowThresholdField = new NumberField(getTranslation("hosts.field.slowThreshold"));
        slowThresholdField.setWidthFull();
        slowThresholdField.setMin(0);
        slowThresholdField.setStep(100);

        if (existing != null) {
            idField.setValue(existing.id());
            idField.setReadOnly(true);
            nameField.setValue(existing.name());
            urlField.setValue(existing.url());
            methodSelect.setValue(existing.method());
            expectedStatusesField.setValue(IntStream.of(existing.expectedStatuses())
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(", ")));
            slowThresholdField.setValue((double) existing.slowThresholdMs());
        } else {
            methodSelect.setValue("GET");
            expectedStatusesField.setValue("200");
            slowThresholdField.setValue(1000.0);
        }

        FormLayout form = new FormLayout(idField, nameField, urlField, methodSelect,
                expectedStatusesField, slowThresholdField);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button saveButton = new Button(getTranslation("hosts.button.save"), e -> {
            String error = validateForm(idField, nameField, urlField, methodSelect, expectedStatusesField, slowThresholdField);
            if (error != null) {
                showError(error);
                return;
            }
            EndpointConfig updated = buildEndpointConfig(idField, nameField, urlField, methodSelect,
                    expectedStatusesField, slowThresholdField);
            saveEndpoint(existing, updated);
            dialog.close();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(getTranslation("hosts.button.cancel"), e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private String validateForm(TextField idField, TextField nameField, TextField urlField,
                                Select<String> methodSelect, TextField expectedStatusesField,
                                NumberField slowThresholdField) {
        if (idField.getValue().isBlank()) return getTranslation("hosts.validate.idRequired");
        if (idField.getValue().contains(" ")) return getTranslation("hosts.validate.idNoSpaces");
        if (nameField.getValue().isBlank()) return getTranslation("hosts.validate.nameRequired");
        if (urlField.getValue().isBlank()) return getTranslation("hosts.validate.urlRequired");
        if (methodSelect.getValue() == null) return getTranslation("hosts.validate.methodRequired");
        if (expectedStatusesField.getValue().isBlank()) return getTranslation("hosts.validate.statusesRequired");
        try {
            Arrays.stream(expectedStatusesField.getValue().split(","))
                    .map(String::trim)
                    .forEach(s -> Integer.parseInt(s));
        } catch (NumberFormatException ex) {
            return getTranslation("hosts.validate.statusesFormat");
        }
        if (slowThresholdField.getValue() == null || slowThresholdField.getValue() < 0) {
            return getTranslation("hosts.validate.slowThreshold");
        }
        return null;
    }

    private EndpointConfig buildEndpointConfig(TextField idField, TextField nameField, TextField urlField,
                                               Select<String> methodSelect, TextField expectedStatusesField,
                                               NumberField slowThresholdField) {
        int[] statuses = Arrays.stream(expectedStatusesField.getValue().split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();
        return new EndpointConfig(
                idField.getValue().trim(),
                nameField.getValue().trim(),
                urlField.getValue().trim(),
                methodSelect.getValue(),
                statuses,
                slowThresholdField.getValue().longValue()
        );
    }

    private void saveEndpoint(EndpointConfig existing, EndpointConfig updated) {
        AppConfig current = configStateService.current();
        List<EndpointConfig> endpoints = new ArrayList<>(current.endpoints());

        if (existing == null) {
            boolean idExists = endpoints.stream().anyMatch(ep -> ep.id().equals(updated.id()));
            if (idExists) {
                showError(getTranslation("hosts.validate.idExists", updated.id()));
                return;
            }
            endpoints.add(updated);
        } else {
            int idx = IntStream.range(0, endpoints.size())
                    .filter(i -> endpoints.get(i).id().equals(existing.id()))
                    .findFirst().orElse(-1);
            if (idx >= 0) endpoints.set(idx, updated);
        }

        writeConfig(new AppConfig(
                current.intervalSeconds(), current.timeoutSeconds(),
                current.incidentFailureThreshold(), current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(), current.discord(), endpoints));
    }

    private void confirmDelete(EndpointConfig ep) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(getTranslation("hosts.delete.header", ep.name()));
        dialog.setText(getTranslation("hosts.delete.text"));
        dialog.setCancelable(true);
        dialog.setConfirmText(getTranslation("hosts.button.delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> deleteEndpoint(ep));
        dialog.open();
    }

    private void deleteEndpoint(EndpointConfig ep) {
        AppConfig current = configStateService.current();
        List<EndpointConfig> endpoints = current.endpoints().stream()
                .filter(e -> !e.id().equals(ep.id()))
                .collect(Collectors.toCollection(ArrayList::new));
        writeConfig(new AppConfig(
                current.intervalSeconds(), current.timeoutSeconds(),
                current.incidentFailureThreshold(), current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(), current.discord(), endpoints));
    }

    private void writeConfig(AppConfig config) {
        try {
            configLoader.save(config);
            refreshGrid();
            Notification n = Notification.show(getTranslation("hosts.notify.saved"), 3000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            showError(getTranslation("hosts.notify.saveFailed", ex.getMessage()));
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
