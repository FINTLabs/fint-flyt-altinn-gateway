package no.fintlabs.golden;

import kotlin.jvm.functions.Function1;
import no.fintlabs.flyt.IncomingInstanceMappingService;
import no.fintlabs.flyt.InstanceProcessorConfiguration;
import no.novari.flyt.gateway.instance.InstanceProcessorFactoryService;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static no.fintlabs.golden.GoldenFiles.assertMatchesGolden;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Locks the sourceApplicationIntegrationId and sourceApplicationInstanceId the gateway derives from an Altinn instance.
 */
class InstanceProcessorConfigurationGoldenTest {

    @Test
    @SuppressWarnings("unchecked")
    void idsDerivedFromTheInstance() throws Exception {
        InstanceProcessorFactoryService factory = mock(InstanceProcessorFactoryService.class);
        IncomingInstanceMappingService mappingService = mock(IncomingInstanceMappingService.class);

        new InstanceProcessorConfiguration().incomingInstanceProcessor(factory, mappingService);

        ArgumentCaptor<Function1<KafkaAltinnInstance, String>> integrationId = ArgumentCaptor.forClass(Function1.class);
        ArgumentCaptor<Function1<KafkaAltinnInstance, String>> instanceId = ArgumentCaptor.forClass(Function1.class);
        verify(factory).createInstanceProcessor(integrationId.capture(), instanceId.capture(), eq(mappingService));

        Map<String, Object> result = new LinkedHashMap<>();
        for (KafkaAltinnInstance instance : List.of(
                KafkaAltinnInstance.builder().appId("vigo/drosjeloyve").instanceId("50001234/abc").build(),
                KafkaAltinnInstance.builder().appId("vigo/drosjesentral").instanceId("50001234/def").build(),
                KafkaAltinnInstance.builder().appId("vigo/drosjeloyve").build()
        )) {
            Map<String, Object> ids = new LinkedHashMap<>();
            ids.put("sourceApplicationIntegrationId", integrationId.getValue().invoke(instance));
            ids.put("sourceApplicationInstanceId", instanceId.getValue().invoke(instance));
            result.put(instance.getAppId() + " " + instance.getInstanceId(), ids);
        }
        assertMatchesGolden("altinn-instance-processor-ids", result);
    }
}
