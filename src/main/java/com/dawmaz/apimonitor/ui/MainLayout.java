package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.i18n.TranslationProvider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.Locale;

public class MainLayout extends AppLayout implements LocaleChangeObserver {

    private final SideNavItem dashboardItem = new SideNavItem("", DashboardView.class, VaadinIcon.DASHBOARD.create());
    private final SideNavItem hostsItem = new SideNavItem("", HostsView.class, VaadinIcon.SERVER.create());
    private final Select<Locale> languagePicker = new Select<>();

    public MainLayout() {
        DrawerToggle toggle = new DrawerToggle();

        H1 title = new H1("API Monitor");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-lg)")
                .set("margin", "0")
                .set("flex", "1");

        languagePicker.setItems(TranslationProvider.LOCALES);
        languagePicker.setItemLabelGenerator(MainLayout::languageLabel);
        languagePicker.setRenderer(new ComponentRenderer<>(locale -> {
            Div row = new Div(flagImage(locale), new Span(languageLabel(locale)));
            row.getStyle().set("display", "flex").set("align-items", "center").set("gap", "0.4rem");
            return row;
        }));
        languagePicker.getStyle().set("min-width", "5rem").set("margin-inline-end", "var(--lumo-space-s)");
        languagePicker.addValueChangeListener(e -> {
            if (!e.isFromClient() || e.getValue() == null) return;
            Locale chosen = e.getValue();
            VaadinSession.getCurrent().setLocale(chosen);
            UI.getCurrent().setLocale(chosen);
        });

        addToNavbar(toggle, title, languagePicker);

        SideNav nav = new SideNav();
        nav.addItem(dashboardItem, hostsItem);

        Scroller scroller = new Scroller(nav);
        scroller.setClassName(LumoUtility.Padding.SMALL);

        addToDrawer(scroller);
        setDrawerOpened(true);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        dashboardItem.setLabel(getTranslation("nav.dashboard"));
        hostsItem.setLabel(getTranslation("nav.hosts"));
        Locale match = resolve(event.getLocale());
        languagePicker.setValue(match);
        languagePicker.setPrefixComponent(flagImage(match));
    }

    private static Locale resolve(Locale locale) {
        return TranslationProvider.POLISH.getLanguage().equals(locale.getLanguage())
                ? TranslationProvider.POLISH
                : TranslationProvider.ENGLISH;
    }

    private static String languageLabel(Locale locale) {
        return TranslationProvider.POLISH.getLanguage().equals(locale.getLanguage()) ? "PL" : "EN";
    }

    private static Image flagImage(Locale locale) {
        String code = TranslationProvider.POLISH.getLanguage().equals(locale.getLanguage()) ? "pl" : "gb";
        Image flag = new Image("https://flagcdn.com/20x15/" + code + ".png", "");
        flag.getStyle().set("flex-shrink", "0");
        return flag;
    }
}
