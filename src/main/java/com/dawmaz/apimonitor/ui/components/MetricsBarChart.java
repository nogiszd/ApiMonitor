package com.dawmaz.apimonitor.ui.components;

import com.dawmaz.apimonitor.model.MetricsSnapshot.EndpointMetrics;
import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.ChartBuilder;
import com.github.appreciated.apexcharts.config.builder.DataLabelsBuilder;
import com.github.appreciated.apexcharts.config.builder.PlotOptionsBuilder;
import com.github.appreciated.apexcharts.config.builder.XAxisBuilder;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.config.chart.builder.ToolbarBuilder;
import com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder;
import com.github.appreciated.apexcharts.helper.Series;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.List;

public class MetricsBarChart extends Composite<Div> {

    private final Div root = new Div();
    private final Span emptyHint = new Span();
    private String seriesLabel = "Avg";
    private String color = "#3B82F6";

    public MetricsBarChart() {
        root.setWidthFull();
        root.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("box-sizing", "border-box");
        emptyHint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-style", "italic");
    }

    @Override
    protected Div initContent() {
        return root;
    }

    public void setSeriesLabel(String seriesLabel) {
        this.seriesLabel = seriesLabel == null ? "" : seriesLabel;
    }

    public void setEmptyText(String text) {
        emptyHint.setText(text == null ? "" : text);
    }

    public void setColor(String color) {
        if (color != null && !color.isBlank()) {
            this.color = color;
        }
    }

    public void setData(List<EndpointMetrics> metrics) {
        root.removeAll();
        if (metrics == null || metrics.isEmpty()) {
            root.add(emptyHint);
            return;
        }
        root.add(buildChart(metrics));
    }

    private ApexCharts buildChart(List<EndpointMetrics> metrics) {
        String[] categories = metrics.stream()
                .map(this::displayName)
                .toArray(String[]::new);
        Double[] values = metrics.stream()
                .map(m -> Math.round(m.avgResponseTimeMs() * 10.0) / 10.0)
                .toArray(Double[]::new);

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.BAR)
                        .withToolbar(ToolbarBuilder.get().withShow(false).build())
                        .build())
                .withPlotOptions(PlotOptionsBuilder.get()
                        .withBar(BarBuilder.get().withHorizontal(true).build())
                        .build())
                .withDataLabels(DataLabelsBuilder.get().withEnabled(true).build())
                .withSeries(new Series<>(seriesLabel, values))
                .withXaxis(XAxisBuilder.get().withCategories(categories).build())
                .withColors(color)
                .build();
        chart.setWidthFull();
        chart.setHeight(Math.max(220, 60 + metrics.size() * 40) + "px");
        return chart;
    }

    private String displayName(EndpointMetrics m) {
        if (m.endpointName() != null && !m.endpointName().isBlank()) {
            return m.endpointName();
        }
        return m.endpointId();
    }
}
