package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.config.EndpointConfig;
import com.dawmaz.apimonitor.model.DomainCertificateStatus;
import com.dawmaz.apimonitor.model.EndpointStatus;
import com.dawmaz.apimonitor.service.DomainStatusService;
import com.dawmaz.apimonitor.service.EndpointStatusService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout implements LocaleChangeObserver, HasDynamicTitle {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final EndpointStatusService statusService;
    private final DomainStatusService domainStatusService;
    private final ConfigStateService configStateService;

    private final H2 heading = new H2();
    private final TabSheet tabs = new TabSheet();
    private final Tab endpointsTab = new Tab();
    private final Tab domainsTab = new Tab();

    private final Grid<EndpointStatusRow> endpointGrid = new Grid<>();
    private final Grid<DomainStatusRow> domainGrid = new Grid<>();

    private Grid.Column<EndpointStatusRow> nameColumn;
    private Grid.Column<EndpointStatusRow> urlColumn;
    private Grid.Column<EndpointStatusRow> statusColumn;
    private Grid.Column<EndpointStatusRow> responseTimeColumn;
    private Grid.Column<EndpointStatusRow> httpCodeColumn;
    private Grid.Column<EndpointStatusRow> lastCheckedColumn;
    private Grid.Column<EndpointStatusRow> errorColumn;

    private Grid.Column<DomainStatusRow> domainNameColumn;
    private Grid.Column<DomainStatusRow> domainStatusColumn;
    private Grid.Column<DomainStatusRow> domainExpiresAtColumn;
    private Grid.Column<DomainStatusRow> domainDaysLeftColumn;
    private Grid.Column<DomainStatusRow> domainIssuerColumn;
    private Grid.Column<DomainStatusRow> domainLastCheckedColumn;
    private Grid.Column<DomainStatusRow> domainErrorColumn;

    public DashboardView(EndpointStatusService statusService,
                         DomainStatusService domainStatusService,
                         ConfigStateService configStateService) {
        this.statusService = statusService;
        this.domainStatusService = domainStatusService;
        this.configStateService = configStateService;

        setSizeFull();
        setPadding(true);

        heading.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);
        add(heading);

        configureEndpointGrid();
        configureDomainGrid();

        tabs.add(endpointsTab, wrap(endpointGrid));
        tabs.add(domainsTab, wrap(domainGrid));
        tabs.setSizeFull();
        add(tabs);

        refreshEndpointGrid();
        refreshDomainGrid();
    }

    private VerticalLayout wrap(Grid<?> grid) {
        VerticalLayout layout = new VerticalLayout(grid);
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);
        return layout;
    }

    private void configureEndpointGrid() {
        endpointGrid.setSizeFull();
        nameColumn = endpointGrid.addColumn(EndpointStatusRow::name).setAutoWidth(true).setSortable(true);
        urlColumn = endpointGrid.addColumn(EndpointStatusRow::url).setFlexGrow(1).setSortable(true);
        statusColumn = endpointGrid.addComponentColumn(row -> createEndpointStatusBadge(row.status())).setAutoWidth(true).setSortable(false);
        responseTimeColumn = endpointGrid.addColumn(row -> row.responseTimeMs() > 0 ? row.responseTimeMs() + " ms" : "—").setAutoWidth(true).setSortable(true);
        httpCodeColumn = endpointGrid.addColumn(row -> row.statusCode() > 0 ? String.valueOf(row.statusCode()) : "—").setAutoWidth(true);
        lastCheckedColumn = endpointGrid.addColumn(row -> row.lastChecked() != null ? TIME_FMT.format(row.lastChecked()) : "—").setAutoWidth(true);
        errorColumn = endpointGrid.addColumn(row -> row.errorMessage() != null ? row.errorMessage() : "—").setFlexGrow(1);
    }

    private void configureDomainGrid() {
        domainGrid.setSizeFull();
        domainNameColumn = domainGrid.addColumn(DomainStatusRow::domain).setAutoWidth(true).setSortable(true);
        domainStatusColumn = domainGrid.addComponentColumn(row -> createDomainStatusBadge(row.status())).setAutoWidth(true);
        domainExpiresAtColumn = domainGrid.addColumn(row -> row.expiresAt() == null ? "—" : DATE_FMT.format(row.expiresAt())).setAutoWidth(true).setSortable(true);
        domainDaysLeftColumn = domainGrid.addColumn(row -> row.expiresAt() == null ? "—" : String.valueOf(row.daysUntilExpiry())).setAutoWidth(true).setSortable(true);
        domainIssuerColumn = domainGrid.addColumn(row -> row.issuer() == null ? "—" : row.issuer()).setFlexGrow(1);
        domainLastCheckedColumn = domainGrid.addColumn(row -> row.lastChecked() == null ? "—" : TIME_FMT.format(row.lastChecked())).setAutoWidth(true);
        domainErrorColumn = domainGrid.addColumn(row -> row.errorMessage() == null ? "—" : row.errorMessage()).setFlexGrow(1);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("nav.dashboard") + " | API Monitor";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        UI.getCurrent().getPage().setTitle(getPageTitle());
        heading.setText(getTranslation("dashboard.title"));

        endpointsTab.setLabel(getTranslation("dashboard.tab.endpoints"));
        domainsTab.setLabel(getTranslation("dashboard.tab.domains"));

        nameColumn.setHeader(getTranslation("dashboard.col.name"));
        urlColumn.setHeader(getTranslation("dashboard.col.url"));
        statusColumn.setHeader(getTranslation("dashboard.col.status"));
        responseTimeColumn.setHeader(getTranslation("dashboard.col.responseTime"));
        httpCodeColumn.setHeader(getTranslation("dashboard.col.httpCode"));
        lastCheckedColumn.setHeader(getTranslation("dashboard.col.lastChecked"));
        errorColumn.setHeader(getTranslation("dashboard.col.error"));

        domainNameColumn.setHeader(getTranslation("dashboard.domains.col.domain"));
        domainStatusColumn.setHeader(getTranslation("dashboard.col.status"));
        domainExpiresAtColumn.setHeader(getTranslation("dashboard.domains.col.expiresAt"));
        domainDaysLeftColumn.setHeader(getTranslation("dashboard.domains.col.daysLeft"));
        domainIssuerColumn.setHeader(getTranslation("dashboard.domains.col.issuer"));
        domainLastCheckedColumn.setHeader(getTranslation("dashboard.col.lastChecked"));
        domainErrorColumn.setHeader(getTranslation("dashboard.col.error"));

        refreshEndpointGrid();
        refreshDomainGrid();
    }

    private void refreshEndpointGrid() {
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

        endpointGrid.setItems(rows);
    }

    private void refreshDomainGrid() {
        List<DomainConfig> domains = configStateService.current().domains();
        if (domains == null) {
            domainGrid.setItems(List.of());
            return;
        }
        Map<String, DomainCertificateStatus> statuses = domainStatusService.getStatuses();

        List<DomainStatusRow> rows = domains.stream()
                .map(d -> {
                    DomainCertificateStatus s = statuses.get(d.domain());
                    if (s != null) {
                        return new DomainStatusRow(
                                d.domain(), s.status(), s.expiresAt(),
                                s.daysUntilExpiry(), s.issuer(), s.errorMessage(), s.lastChecked());
                    }
                    return new DomainStatusRow(
                            d.domain(), DomainCertificateStatus.Status.UNKNOWN,
                            null, 0, null, null, null);
                })
                .toList();

        domainGrid.setItems(rows);
    }

    private Span createEndpointStatusBadge(EndpointStatus.Status status) {
        Span badge = new Span(getTranslation("status." + status.name()));
        styleBadge(badge);
        switch (status) {
            case UP -> colorBadge(badge, "#d4edda", "#155724");
            case DOWN -> colorBadge(badge, "#f8d7da", "#721c24");
            case SLOW -> colorBadge(badge, "#fff3cd", "#856404");
            default -> colorBadge(badge, "#e2e3e5", "#383d41");
        }
        return badge;
    }

    private Span createDomainStatusBadge(DomainCertificateStatus.Status status) {
        Span badge = new Span(getTranslation("domainStatus." + status.name()));
        styleBadge(badge);
        switch (status) {
            case VALID -> colorBadge(badge, "#d4edda", "#155724");
            case EXPIRING_SOON -> colorBadge(badge, "#fff3cd", "#856404");
            case EXPIRED -> colorBadge(badge, "#f8d7da", "#721c24");
            case ERROR -> colorBadge(badge, "#f8d7da", "#721c24");
            default -> colorBadge(badge, "#e2e3e5", "#383d41");
        }
        return badge;
    }

    private void styleBadge(Span badge) {
        badge.getStyle()
                .set("padding", "2px 10px")
                .set("border-radius", "12px")
                .set("font-size", "0.8em")
                .set("font-weight", "bold")
                .set("letter-spacing", "0.05em");
    }

    private void colorBadge(Span badge, String bg, String fg) {
        badge.getStyle().set("background-color", bg).set("color", fg);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        var ui = attachEvent.getUI();
        Registration endpointReg = statusService.addUpdateListener(ignored -> ui.access(this::refreshEndpointGrid));
        Registration domainReg = domainStatusService.addUpdateListener(ignored -> ui.access(this::refreshDomainGrid));
        addDetachListener(e -> {
            endpointReg.remove();
            domainReg.remove();
        });
    }

    record EndpointStatusRow(
            String name,
            String url,
            EndpointStatus.Status status,
            long responseTimeMs,
            int statusCode,
            String errorMessage,
            Instant lastChecked
    ) {}

    record DomainStatusRow(
            String domain,
            DomainCertificateStatus.Status status,
            Instant expiresAt,
            long daysUntilExpiry,
            String issuer,
            String errorMessage,
            Instant lastChecked
    ) {}
}
