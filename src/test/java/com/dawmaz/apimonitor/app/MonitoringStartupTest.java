package com.dawmaz.apimonitor.app;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.DiscordConfig;
import com.dawmaz.apimonitor.event.EventBus;
import com.dawmaz.apimonitor.service.DiscordAlertService;
import com.dawmaz.apimonitor.service.DomainSchedulerService;
import com.dawmaz.apimonitor.service.IncidentStateService;
import com.dawmaz.apimonitor.service.MetricsStore;
import com.dawmaz.apimonitor.service.SchedulerService;
import com.dawmaz.apimonitor.service.SmtpService;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.Disposables;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringStartupTest {

    @Test
    void shouldStartMonitoringOnlyOnceWhenRunIsCalledMultipleTimes() {
        ConfigStateService configStateService = mock(ConfigStateService.class);
        SchedulerService schedulerService = mock(SchedulerService.class);
        DomainSchedulerService domainSchedulerService = mock(DomainSchedulerService.class);
        DiscordAlertService discordAlertService = mock(DiscordAlertService.class);
        SmtpService smtpService = mock(SmtpService.class);
        IncidentStateService incidentStateService = mock(IncidentStateService.class);
        MetricsStore metricsStore = mock(MetricsStore.class);
        EventBus eventBus = new EventBus();

        AppConfig appConfig = new AppConfig(
                "en",
                5,
                3,
                3,
                2,
                30,
                new DiscordConfig(false, "", null),
                null,
                List.of(),
                List.of()
        );

        Disposable disposable = Disposables.disposed();
        when(configStateService.initialize()).thenReturn(appConfig);
        when(configStateService.startLiveReload()).thenReturn(disposable);
        when(schedulerService.start(configStateService)).thenReturn(disposable);
        when(domainSchedulerService.start(configStateService)).thenReturn(disposable);

        MonitoringStartup startup = new MonitoringStartup(
                configStateService,
                schedulerService,
                domainSchedulerService,
                eventBus,
                discordAlertService,
                smtpService,
                incidentStateService,
                metricsStore
        );

        startup.run();
        startup.run();

        verify(configStateService, times(1)).initialize();
        verify(configStateService, times(1)).startLiveReload();
        verify(schedulerService, times(1)).start(configStateService);
        verify(domainSchedulerService, times(1)).start(configStateService);
    }
}

