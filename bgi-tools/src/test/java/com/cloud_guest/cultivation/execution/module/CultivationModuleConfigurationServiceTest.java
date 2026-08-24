package com.cloud_guest.cultivation.execution.module;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloud_guest.cultivation.execution.BetterGiInstalledScriptSettingsReader;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigEntity;
import com.cloud_guest.cultivation.persistence.CultivationModuleConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CultivationModuleConfigurationServiceTest {
    @Test
    void currentBackendPortOverridesStoredFullyAutoCdApi() {
        CultivationModuleConfigEntity stored = new CultivationModuleConfigEntity();
        stored.setUid("102550550");
        stored.setModuleId(FullyAutoToolsExecutionModule.ID);
        stored.setEnabled(true);
        stored.setSettingsJson("{\"http_api\":\"http://127.0.0.1:18081/bgi/cron/next-timestamp/all\"}");

        CultivationModuleConfigMapper mapper = mock(CultivationModuleConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        BetterGiInstalledScriptSettingsReader reader = mock(BetterGiInstalledScriptSettingsReader.class);
        when(reader.read(FullyAutoToolsExecutionModule.ID)).thenReturn(Map.of());
        CultivationModuleConfigurationService service = new CultivationModuleConfigurationService(
                new CultivationModuleRegistry(List.of(new FullyAutoToolsExecutionModule())),
                mapper, new ObjectMapper(), reader);
        ReflectionTestUtils.setField(service, "serverPort", 8081);

        CultivationModuleConfiguration configuration = service.find(
                "102550550", FullyAutoToolsExecutionModule.ID);

        assertThat(configuration.settings().get("http_api"))
                .isEqualTo("http://127.0.0.1:8081/bgi/cron/next-timestamp/all");
    }
}
