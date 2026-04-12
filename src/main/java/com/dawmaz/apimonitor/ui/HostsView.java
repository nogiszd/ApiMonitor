package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigLoader;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.EndpointConfig;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@PageTitle("Manage Hosts | API Monitor")
@Route(value = "hosts", layout = MainLayout.class)
public class HostsView extends VerticalLayout {

    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final ConfigStateService configStateService;
    private final ConfigLoader configLoader;
    private final Grid<EndpointConfig> grid = new Grid<>();

    public HostsView(ConfigStateService configStateService, ConfigLoader configLoader) {
        this.configStateService = configStateService;
        this.configLoader = configLoader;

        setSizeFull();
        setPadding(true);

        H2 header = new H2("Manage Hosts");
        Button addButton = new Button("Add Host", e -> openEditDialog(null));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

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
        grid.addColumn(EndpointConfig::id).setHeader("ID").setAutoWidth(true).setSortable(true);
        grid.addColumn(EndpointConfig::name).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(EndpointConfig::url).setHeader("URL").setFlexGrow(1);
        grid.addColumn(EndpointConfig::method).setHeader("Method").setAutoWidth(true);
        grid.addColumn(ep -> IntStream.of(ep.expectedStatuses())
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", ")))
                .setHeader("Expected Codes").setAutoWidth(true);
        grid.addColumn(ep -> ep.slowThresholdMs() + " ms").setHeader("Slow Threshold").setAutoWidth(true);
        grid.addComponentColumn(ep -> {
            Button edit = new Button("Edit", e -> openEditDialog(ep));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button delete = new Button("Delete", e -> confirmDelete(ep));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(edit, delete);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void refreshGrid() {
        grid.setItems(new ArrayList<>(configStateService.current().endpoints()));
    }

    private void openEditDialog(EndpointConfig existing) {
        Dialog dialog = new Dialog();
        dialog.setWidth("480px");
        dialog.setHeaderTitle(existing == null ? "Add Host" : "Edit Host");

        TextField idField = new TextField("ID");
        idField.setWidthFull();
        idField.setPlaceholder("e.g. my-api");
        idField.setHelperText("Unique identifier (no spaces)");

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setPlaceholder("e.g. My API");

        TextField urlField = new TextField("URL");
        urlField.setWidthFull();
        urlField.setPlaceholder("https://example.com/health");

        Select<String> methodSelect = new Select<>();
        methodSelect.setLabel("HTTP Method");
        methodSelect.setItems(HTTP_METHODS);
        methodSelect.setWidthFull();

        TextField expectedStatusesField = new TextField("Expected Status Codes");
        expectedStatusesField.setWidthFull();
        expectedStatusesField.setPlaceholder("200, 201, 301");
        expectedStatusesField.setHelperText("Comma-separated HTTP status codes");

        NumberField slowThresholdField = new NumberField("Slow Threshold (ms)");
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

        Button saveButton = new Button("Save", e -> {
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

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private String validateForm(TextField idField, TextField nameField, TextField urlField,
                                Select<String> methodSelect, TextField expectedStatusesField,
                                NumberField slowThresholdField) {
        if (idField.getValue().isBlank()) return "ID is required.";
        if (idField.getValue().contains(" ")) return "ID must not contain spaces.";
        if (nameField.getValue().isBlank()) return "Name is required.";
        if (urlField.getValue().isBlank()) return "URL is required.";
        if (methodSelect.getValue() == null) return "HTTP method is required.";
        if (expectedStatusesField.getValue().isBlank()) return "Expected status codes are required.";
        try {
            Arrays.stream(expectedStatusesField.getValue().split(","))
                    .map(String::trim)
                    .forEach(s -> Integer.parseInt(s));
        } catch (NumberFormatException ex) {
            return "Expected status codes must be comma-separated integers, e.g. 200, 301";
        }
        if (slowThresholdField.getValue() == null || slowThresholdField.getValue() < 0) {
            return "Slow threshold must be a non-negative number.";
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
                showError("An endpoint with ID \"" + updated.id() + "\" already exists.");
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
        dialog.setHeader("Delete \"" + ep.name() + "\"?");
        dialog.setText("This will remove the host from monitoring. The change is saved immediately.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
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
            Notification n = Notification.show("Saved. Config will reload automatically.", 3000, Notification.Position.BOTTOM_END);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            showError("Failed to save config: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
