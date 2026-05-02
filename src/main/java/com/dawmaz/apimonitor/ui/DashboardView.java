package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.config.EndpointConfig;
import com.dawmaz.apimonitor.model.DomainCertificateStatus;
import com.dawmaz.apimonitor.model.EndpointStatus;
import com.dawmaz.apimonitor.model.MetricsSnapshot;
import com.dawmaz.apimonitor.model.MetricsSnapshot.EndpointMetrics;
import com.dawmaz.apimonitor.service.DomainStatusService;
import com.dawmaz.apimonitor.service.EndpointStatusService;
import com.dawmaz.apimonitor.service.MetricsStore;
import com.dawmaz.apimonitor.ui.components.MetricsBarChart;
import com.dawmaz.apimonitor.ui.components.StatCard;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final MetricsStore metricsStore;

    private final H2 heading = new H2();
    private final TabSheet tabs = new TabSheet();
    private final Tab endpointsTab = new Tab();
    private final Tab domainsTab = new Tab();
    private final Tab metricsTab = new Tab();

    private final Grid<EndpointStatusRow> endpointGrid = new Grid<>();
    private final Grid<DomainStatusRow> domainGrid = new Grid<>();
    private final Grid<EndpointMetrics> metricsGrid = new Grid<>();

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

    private Grid.Column<EndpointMetrics> metricsEndpointColumn;
    private Grid.Column<EndpointMetrics> metricsTotalColumn;
    private Grid.Column<EndpointMetrics> metricsSuccessColumn;
    private Grid.Column<EndpointMetrics> metricsFailureColumn;
    private Grid.Column<EndpointMetrics> metricsSuccessRateColumn;
    private Grid.Column<EndpointMetrics> metricsAvgColumn;
    private Grid.Column<EndpointMetrics> metricsMaxColumn;

    private final StatCard totalChecksCard = new StatCard();
    private final StatCard successRateCard = new StatCard();
    private final StatCard avgResponseTimeCard = new StatCard();
    private final H3 chartTitle = new H3();
    private final MetricsBarChart metricsChart = new MetricsBarChart();

    public DashboardView(EndpointStatusService statusService,
                         DomainStatusService domainStatusService,
                         ConfigStateService configStateService,
                         MetricsStore metricsStore) {
        this.statusService = statusService;
        this.domainStatusService = domainStatusService;
        this.configStateService = configStateService;
        this.metricsStore = metricsStore;

        setSizeFull();
        setPadding(true);

        heading.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE, LumoUtility.Margin.Bottom.SMALL);
        add(heading);

        configureEndpointGrid();
        configureDomainGrid();
        configureMetricsGrid();

        tabs.add(endpointsTab, wrap(endpointGrid));
        tabs.add(domainsTab, wrap(domainGrid));
        tabs.add(metricsTab, buildMetricsTab());
        tabs.setSizeFull();
        add(tabs);

        refreshEndpointGrid();
        refreshDomainGrid();
        refreshMetrics();
    }

    private VerticalLayout wrap(Grid<?> grid) {
        VerticalLayout layout = new VerticalLayout(grid);
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);
        return layout;
    }

    private VerticalLayout buildMetricsTab() {
        FlexLayout cards = new FlexLayout(totalChecksCard, successRateCard, avgResponseTimeCard);
        cards.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cards.getStyle().set("gap", "var(--lumo-space-m)");
        cards.setWidthFull();

        chartTitle.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.NONE);
        chartTitle.getStyle().set("margin-top", "var(--lumo-space-m)");

        VerticalLayout layout = new VerticalLayout(cards, chartTitle, metricsChart, metricsGrid);
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);
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

    private void configureMetricsGrid() {
        metricsGrid.setAllRowsVisible(true);
        metricsGrid.setWidthFull();
        metricsEndpointColumn = metricsGrid.addColumn(m -> displayName(m)).setAutoWidth(true).setFlexGrow(1).setSortable(true);
        metricsTotalColumn = metricsGrid.addColumn(EndpointMetrics::totalChecks).setAutoWidth(true).setSortable(true);
        metricsSuccessColumn = metricsGrid.addColumn(EndpointMetrics::successCount).setAutoWidth(true);
        metricsFailureColumn = metricsGrid.addColumn(EndpointMetrics::failureCount).setAutoWidth(true);
        metricsSuccessRateColumn = metricsGrid.addColumn(m -> formatPercent(m.successRatePercent())).setAutoWidth(true);
        metricsAvgColumn = metricsGrid.addColumn(m -> formatMillis(m.avgResponseTimeMs())).setAutoWidth(true).setSortable(true);
        metricsMaxColumn = metricsGrid.addColumn(m -> m.maxResponseTimeMs() + " ms").setAutoWidth(true);
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
        metricsTab.setLabel(getTranslation("dashboard.tab.metrics"));

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

        metricsEndpointColumn.setHeader(getTranslation("dashboard.metrics.col.endpoint"));
        metricsTotalColumn.setHeader(getTranslation("dashboard.metrics.col.total"));
        metricsSuccessColumn.setHeader(getTranslation("dashboard.metrics.col.success"));
        metricsFailureColumn.setHeader(getTranslation("dashboard.metrics.col.failure"));
        metricsSuccessRateColumn.setHeader(getTranslation("dashboard.metrics.col.successRate"));
        metricsAvgColumn.setHeader(getTranslation("dashboard.metrics.col.avgMs"));
        metricsMaxColumn.setHeader(getTranslation("dashboard.metrics.col.maxMs"));

        totalChecksCard.setLabel(getTranslation("dashboard.metrics.totalChecks"));
        successRateCard.setLabel(getTranslation("dashboard.metrics.successRate"));
        avgResponseTimeCard.setLabel(getTranslation("dashboard.metrics.avgResponseTime"));
        chartTitle.setText(getTranslation("dashboard.metrics.chartTitle"));
        metricsChart.setSeriesLabel(getTranslation("dashboard.metrics.col.avgMs"));
        metricsChart.setEmptyText(getTranslation("dashboard.metrics.empty"));

        refreshEndpointGrid();
        refreshDomainGrid();
        refreshMetrics();
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

    private void refreshMetrics() {
        List<MetricsSnapshot> snapshots = metricsStore.recent();
        Map<String, EndpointMetrics> aggregated = aggregateAcrossSnapshots(snapshots);

        long totalChecks = aggregated.values().stream().mapToLong(EndpointMetrics::totalChecks).sum();
        long totalSuccess = aggregated.values().stream().mapToLong(EndpointMetrics::successCount).sum();
        double weightedAvg = totalChecks == 0 ? 0d :
                aggregated.values().stream()
                        .mapToDouble(m -> m.avgResponseTimeMs() * m.totalChecks())
                        .sum() / totalChecks;
        double successRate = totalChecks == 0 ? 0d : (double) totalSuccess / totalChecks * 100d;

        totalChecksCard.setValue(String.valueOf(totalChecks));
        successRateCard.setValue(formatPercent(successRate));
        avgResponseTimeCard.setValue(formatMillis(weightedAvg));

        List<EndpointMetrics> sorted = new ArrayList<>(aggregated.values());
        sorted.sort((a, b) -> a.endpointName().compareToIgnoreCase(b.endpointName()));
        metricsGrid.setItems(sorted);
        metricsChart.setData(sorted);
    }

    private Map<String, EndpointMetrics> aggregateAcrossSnapshots(List<MetricsSnapshot> snapshots) {
        Map<String, long[]> totals = new HashMap<>();
        Map<String, double[]> avgWeighted = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, Long> max = new HashMap<>();

        for (MetricsSnapshot snapshot : snapshots) {
            for (EndpointMetrics m : snapshot.perEndpoint().values()) {
                long[] arr = totals.computeIfAbsent(m.endpointId(), k -> new long[3]);
                arr[0] += m.totalChecks();
                arr[1] += m.successCount();
                arr[2] += m.failureCount();
                avgWeighted.merge(m.endpointId(), new double[]{m.avgResponseTimeMs() * m.totalChecks()},
                        (a, b) -> new double[]{a[0] + b[0]});
                max.merge(m.endpointId(), m.maxResponseTimeMs(), Math::max);
                names.putIfAbsent(m.endpointId(), m.endpointName());
            }
        }

        Map<String, EndpointMetrics> result = new HashMap<>();
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            String id = entry.getKey();
            long total = entry.getValue()[0];
            long success = entry.getValue()[1];
            long failure = entry.getValue()[2];
            double avg = total == 0 ? 0d : avgWeighted.get(id)[0] / total;
            long maxMs = max.getOrDefault(id, 0L);
            result.put(id, new EndpointMetrics(id, names.get(id), total, success, failure, avg, maxMs));
        }
        return result;
    }

    private String displayName(EndpointMetrics m) {
        if (m.endpointName() != null && !m.endpointName().isBlank()) {
            return m.endpointName();
        }
        return m.endpointId();
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String formatMillis(double value) {
        return String.format(Locale.ROOT, "%.0f ms", value);
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
        Registration metricsReg = metricsStore.addListener(ignored -> ui.access(this::refreshMetrics));
        addDetachListener(e -> {
            endpointReg.remove();
            domainReg.remove();
            metricsReg.remove();
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
