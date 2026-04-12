package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.EndpointConfig;
import com.dawmaz.apimonitor.model.EndpointStatus;
import com.dawmaz.apimonitor.service.EndpointStatusService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@PageTitle("Dashboard | API Monitor")
@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final EndpointStatusService statusService;
    private final ConfigStateService configStateService;
    private final Grid<EndpointStatusRow> grid = new Grid<>();

    public DashboardView(EndpointStatusService statusService, ConfigStateService configStateService) {
        this.statusService = statusService;
        this.configStateService = configStateService;

        setSizeFull();
        setPadding(true);

        add(new H2("Endpoint Status"));
        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(EndpointStatusRow::name)
                .setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(EndpointStatusRow::url)
                .setHeader("URL").setFlexGrow(1).setSortable(true);
        grid.addComponentColumn(row -> createStatusBadge(row.status()))
                .setHeader("Status").setAutoWidth(true).setSortable(false);
        grid.addColumn(row -> row.responseTimeMs() > 0 ? row.responseTimeMs() + " ms" : "—")
                .setHeader("Response Time").setAutoWidth(true).setSortable(true);
        grid.addColumn(row -> row.statusCode() > 0 ? String.valueOf(row.statusCode()) : "—")
                .setHeader("HTTP Code").setAutoWidth(true);
        grid.addColumn(row -> row.lastChecked() != null ? TIME_FMT.format(row.lastChecked()) : "—")
                .setHeader("Last Checked").setAutoWidth(true);
        grid.addColumn(row -> row.errorMessage() != null ? row.errorMessage() : "—")
                .setHeader("Error").setFlexGrow(1);
    }

    private void refreshGrid() {
        List<EndpointConfig> endpoints = configStateService.current().endpoints();
        Map<String, EndpointStatus> statuses = statusService.getStatuses();

        List<EndpointStatusRow> rows = endpoints.stream()
                .map(ep -> {
                    EndpointStatus s = statuses.get(ep.id());
                    if (s != null) {
                        return new EndpointStatusRow(
                                ep.name(), ep.url(), s.status(),
                                s.responseTimeMs(), s.statusCode(), s.errorMessage(), s.lastChecked());
                    }
                    return new EndpointStatusRow(
                            ep.name(), ep.url(), EndpointStatus.Status.UNKNOWN,
                            0, 0, null, null);
                })
                .toList();

        grid.setItems(rows);
    }

    private Span createStatusBadge(EndpointStatus.Status status) {
        Span badge = new Span(status.name());
        badge.getStyle()
                .set("padding", "2px 10px")
                .set("border-radius", "12px")
                .set("font-size", "0.8em")
                .set("font-weight", "bold")
                .set("letter-spacing", "0.05em");
        switch (status) {
            case UP -> badge.getStyle()
                    .set("background-color", "#d4edda").set("color", "#155724");
            case DOWN -> badge.getStyle()
                    .set("background-color", "#f8d7da").set("color", "#721c24");
            case SLOW -> badge.getStyle()
                    .set("background-color", "#fff3cd").set("color", "#856404");
            default -> badge.getStyle()
                    .set("background-color", "#e2e3e5").set("color", "#383d41");
        }
        return badge;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        var ui = attachEvent.getUI();
        Registration reg = statusService.addUpdateListener(ignored -> ui.access(this::refreshGrid));
        addDetachListener(e -> reg.remove());
    }

    record EndpointStatusRow(
            String name,
            String url,
            EndpointStatus.Status status,
            long responseTimeMs,
            int statusCode,
            String errorMessage,
            java.time.Instant lastChecked
    ) {}
}
