package com.dawmaz.apimonitor.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

public class StatCard extends Div {

    private final Span labelEl = new Span();
    private final Span valueEl = new Span();

    public StatCard() {
        labelEl.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em");
        valueEl.getStyle()
                .set("font-size", "var(--lumo-font-size-xxxl)")
                .set("font-weight", "600")
                .set("color", "var(--lumo-primary-text-color)");

        Div valueWrap = new Div(valueEl);
        valueWrap.getStyle().set("margin-top", "var(--lumo-space-xs)");

        add(labelEl, valueWrap);
        getStyle()
                .set("flex", "1 1 200px")
                .set("min-width", "200px")
                .set("padding", "var(--lumo-space-m)")
                .set("background-color", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-sizing", "border-box");
    }

    public StatCard(String label, String value) {
        this();
        setLabel(label);
        setValue(value);
    }

    public void setLabel(String label) {
        labelEl.setText(label == null ? "" : label);
    }

    public void setValue(String value) {
        valueEl.setText(value == null ? "" : value);
    }
}
