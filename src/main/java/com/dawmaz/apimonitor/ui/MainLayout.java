package com.dawmaz.apimonitor.ui;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigLoader;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.i18n.TranslationProvider;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class MainLayout extends AppLayout implements LocaleChangeObserver {

    private static final Logger log = LoggerFactory.getLogger(MainLayout.class);

    private final ConfigStateService configStateService;
    private final ConfigLoader configLoader;
    private final SideNavItem dashboardItem = new SideNavItem("", DashboardView.class, VaadinIcon.DASHBOARD.create());
    private final SideNavItem hostsItem = new SideNavItem("", HostsView.class, VaadinIcon.SERVER.create());
    private final SideNavItem domainsItem = new SideNavItem("", DomainsView.class, VaadinIcon.GLOBE.create());
    private final SideNavItem discordItem = new SideNavItem("", DiscordView.class, VaadinIcon.CHAT.create());
    private final SideNavItem smtpItem = new SideNavItem("", SmtpView.class, VaadinIcon.ENVELOPE.create());
    private final Select<Locale> languagePicker = new Select<>();

    public MainLayout(ConfigStateService configStateService, ConfigLoader configLoader) {
        this.configStateService = configStateService;
        this.configLoader = configLoader;

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
            persistLanguage(chosen);
        });

        addToNavbar(toggle, title, languagePicker);

        SideNav overviewNav = new SideNav();
        overviewNav.addItem(dashboardItem);
        overviewNav.setWidthFull();

        SideNav resourcesNav = new SideNav();
        resourcesNav.addItem(hostsItem, domainsItem);
        resourcesNav.setWidthFull();

        SideNav notificationsNav = new SideNav();
        notificationsNav.addItem(discordItem, smtpItem);
        notificationsNav.setWidthFull();

        VerticalLayout drawerLayout = new VerticalLayout(
                overviewNav, separator(), resourcesNav, separator(), notificationsNav);
        drawerLayout.setPadding(false);
        drawerLayout.setSpacing(false);
        drawerLayout.setWidthFull();
        drawerLayout.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.STRETCH);
        drawerLayout.getStyle().set("padding", "var(--lumo-space-s)");

        Scroller scroller = new Scroller(drawerLayout);

        addToDrawer(scroller);
        setDrawerOpened(true);
    }

    private static Hr separator() {
        Hr hr = new Hr();
        hr.getStyle()
                .set("margin", "var(--lumo-space-xs) 0")
                .set("border-color", "var(--lumo-contrast-10pct)");
        return hr;
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        dashboardItem.setLabel(getTranslation("nav.dashboard"));
        hostsItem.setLabel(getTranslation("nav.hosts"));
        domainsItem.setLabel(getTranslation("nav.domains"));
        discordItem.setLabel(getTranslation("nav.discord"));
        smtpItem.setLabel(getTranslation("nav.smtp"));
        Locale match = resolve(event.getLocale());
        languagePicker.setValue(match);
        languagePicker.setPrefixComponent(flagImage(match));
    }

    private void persistLanguage(Locale locale) {
        AppConfig current = configStateService.current();
        String code = locale.getLanguage();
        if (code.equals(current.language())) {
            return;
        }
        AppConfig updated = new AppConfig(
                code,
                current.intervalSeconds(),
                current.timeoutSeconds(),
                current.incidentFailureThreshold(),
                current.incidentRecoverySuccessThreshold(),
                current.metricsWindowSeconds(),
                current.discord(),
                current.smtp(),
                current.endpoints(),
                current.domains()
        );
        try {
            configLoader.save(updated);
        } catch (Exception ex) {
            log.warn("[CONFIG] Failed to persist language change: {}", ex.getMessage());
            Notification n = Notification.show(
                    getTranslation("language.persistFailed", ex.getMessage()),
                    4000, Notification.Position.MIDDLE);
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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
