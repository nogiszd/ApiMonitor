package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.EndpointConfig;
import com.dawmaz.apimonitor.model.EndpointStatus;
import com.dawmaz.apimonitor.service.EndpointStatusService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout implements LocaleChangeObserver, HasDynamicTitle {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final EndpointStatusService statusService;
    private final ConfigStateService configStateService;
    private final H2 heading = new H2();
    private final Grid<EndpointStatusRow> grid = new Grid<>();

    private Grid.Column<EndpointStatusRow> nameColumn;
    private Grid.Column<EndpointStatusRow> urlColumn;
    private Grid.Column<EndpointStatusRow> statusColumn;
    private Grid.Column<EndpointStatusRow> responseTimeColumn;
    private Grid.Column<EndpointStatusRow> httpCodeColumn;
    private Grid.Column<EndpointStatusRow> lastCheckedColumn;
    private Grid.Column<EndpointStatusRow> errorColumn;

    public DashboardView(EndpointStatusService statusService, ConfigStateService configStateService) {
        this.statusService = statusService;
        this.configStateService = configStateService;

        setSizeFull();
        setPadding(true);

        heading.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);
        add(heading);
        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        nameColumn = grid.addColumn(EndpointStatusRow::name).setAutoWidth(true).setSortable(true);
        urlColumn = grid.addColumn(EndpointStatusRow::url).setFlexGrow(1).setSortable(true);
        statusColumn = grid.addComponentColumn(row -> createStatusBadge(row.status())).setAutoWidth(true).setSortable(false);
        responseTimeColumn = grid.addColumn(row -> row.responseTimeMs() > 0 ? row.responseTimeMs() + " ms" : "—").setAutoWidth(true).setSortable(true);
        httpCodeColumn = grid.addColumn(row -> row.statusCode() > 0 ? String.valueOf(row.statusCode()) : "—").setAutoWidth(true);
        lastCheckedColumn = grid.addColumn(row -> row.lastChecked() != null ? TIME_FMT.format(row.lastChecked()) : "—").setAutoWidth(true);
        errorColumn = grid.addColumn(row -> row.errorMessage() != null ? row.errorMessage() : "—").setFlexGrow(1);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("nav.dashboard") + " | API Monitor";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        heading.setText(getTranslation("dashboard.title"));
        nameColumn.setHeader(getTranslation("dashboard.col.name"));
        urlColumn.setHeader(getTranslation("dashboard.col.url"));
        statusColumn.setHeader(getTranslation("dashboard.col.status"));
        responseTimeColumn.setHeader(getTranslation("dashboard.col.responseTime"));
        httpCodeColumn.setHeader(getTranslation("dashboard.col.httpCode"));
        lastCheckedColumn.setHeader(getTranslation("dashboard.col.lastChecked"));
        errorColumn.setHeader(getTranslation("dashboard.col.error"));
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
            case UP -> badge.getStyle().set("background-color", "#d4edda").set("color", "#155724");
            case DOWN -> badge.getStyle().set("background-color", "#f8d7da").set("color", "#721c24");
            case SLOW -> badge.getStyle().set("background-color", "#fff3cd").set("color", "#856404");
            default -> badge.getStyle().set("background-color", "#e2e3e5").set("color", "#383d41");
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
