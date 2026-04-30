package com.dawmaz.apimonitor.i18n;

import com.vaadin.flow.i18n.I18NProvider;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Component
public class TranslationProvider implements I18NProvider {

    private static final String BUNDLE = "i18n.translations";

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale POLISH = Locale.of("pl");
    public static final List<Locale> LOCALES = List.of(ENGLISH, POLISH);

    // No-fallback control: prevents the JVM's default locale from leaking into
    // the candidate chain (e.g. en -> pl -> root when default is pl_PL).
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @Override
    public List<Locale> getProvidedLocales() {
        return LOCALES;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        Locale target = resolveLocale(locale);
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, target, getClass().getClassLoader(), NO_FALLBACK);

        String value;
        try {
            value = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }

        if (params == null || params.length == 0) {
            return value;
        }
        return new MessageFormat(value, target).format(params);
    }

    private static Locale resolveLocale(Locale locale) {
        if (locale == null) return ENGLISH;
        for (Locale supported : LOCALES) {
            if (supported.getLanguage().equals(locale.getLanguage())) {
                return supported;
            }
        }
        return ENGLISH;
    }
}
