package no.fintlabs.golden;

import no.fintlabs.altinn.AltinnInstanceConsumer;
import no.fintlabs.gateway.instance.InstanceProcessor;
import no.fintlabs.kafka.common.ListenerContainerFactory;
import no.fintlabs.kafka.event.EventConsumerConfiguration;
import no.fintlabs.kafka.event.EventConsumerFactoryService;
import no.fintlabs.kafka.event.topic.EventTopicService;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static no.fintlabs.golden.GoldenFiles.assertMatchesGolden;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks how a record from the Altinn topic is handed to the instance processor.
 */
class AltinnInstanceConsumerGoldenTest {

    @SuppressWarnings("unchecked")
    private final InstanceProcessor<KafkaAltinnInstance> instanceProcessor = mock(InstanceProcessor.class);

    private Consumer<ConsumerRecord<String, KafkaAltinnInstance>> listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The current consumer fetches a NAM token before each instance.
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("{\"access_token\":\"token\"}")
                        .build()))
                .build();
        AltinnInstanceConsumer consumer = new AltinnInstanceConsumer(mock(EventTopicService.class), webClient, "ofk-no", instanceProcessor);
        ReflectionTestUtils.setField(consumer, "clientId", "client-id");
        ReflectionTestUtils.setField(consumer, "clientSecret", "client-secret");

        EventConsumerFactoryService consumerFactoryService = mock(EventConsumerFactoryService.class);
        when(consumerFactoryService.createRecordConsumerFactory(eq(KafkaAltinnInstance.class), any(Consumer.class), any(EventConsumerConfiguration.class)))
                .thenReturn(mock(ListenerContainerFactory.class));
        consumer.altinnInstanceConsumerConfiguration(consumerFactoryService);

        ArgumentCaptor<Consumer<ConsumerRecord<String, KafkaAltinnInstance>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(consumerFactoryService).createRecordConsumerFactory(eq(KafkaAltinnInstance.class), captor.capture(), any(EventConsumerConfiguration.class));
        listener = captor.getValue();
    }

    @Test
    void recordIsProcessedAsSourceApplicationFive() throws Exception {
        KafkaAltinnInstance instance = InstanceMappingGoldenTest.fullInstance("vigo/drosjeloyve");
        when(instanceProcessor.processInstance(any(Authentication.class), eq(instance)))
                .thenReturn(Mono.just(ResponseEntity.accepted().build()));

        listener.accept(new ConsumerRecord<>("ofk-no.altinn.event.instance-received", 0, 0, instance.getInstanceId(), instance));

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(instanceProcessor).processInstance(authentication.capture(), eq(instance));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceApplicationId", sourceApplicationId(authentication.getValue()));
        result.put("sourceApplicationInstanceId", instance.getInstanceId());
        assertMatchesGolden("altinn-consumer-processed", result);
    }

    @Test
    void processingErrorIsRethrownToTheContainer() throws Exception {
        KafkaAltinnInstance instance = InstanceMappingGoldenTest.fullInstance("vigo/drosjeloyve");
        when(instanceProcessor.processInstance(any(Authentication.class), eq(instance)))
                .thenReturn(Mono.error(new IllegalStateException("intgr-conf-service unavailable")));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                listener.accept(new ConsumerRecord<>("ofk-no.altinn.event.instance-received", 0, 0, instance.getInstanceId(), instance)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exception", exception.getClass().getName());
        result.put("message", exception.getMessage());
        assertMatchesGolden("altinn-consumer-error", result);
    }

    private static Long sourceApplicationId(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("SOURCE_APPLICATION_ID_"))
                .map(authority -> Long.parseLong(authority.substring("SOURCE_APPLICATION_ID_".length())))
                .findFirst()
                .orElse(null);
    }
}
