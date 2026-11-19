package no.fintlabs.golden;

import no.fintlabs.altinn.AltinnInstanceConsumer;
import no.novari.flyt.gateway.instance.InstanceProcessor;
import no.novari.kafka.consuming.ErrorHandlerFactory;
import no.novari.kafka.consuming.ListenerConfiguration;
import no.novari.kafka.consuming.ParameterizedListenerContainerFactory;
import no.novari.kafka.consuming.ParameterizedListenerContainerFactoryService;
import no.novari.kafka.topic.EventTopicService;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.listener.CommonErrorHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static no.fintlabs.golden.GoldenFiles.assertMatchesGolden;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        AltinnInstanceConsumer consumer = new AltinnInstanceConsumer(mock(EventTopicService.class), "ofk-no", instanceProcessor);

        ParameterizedListenerContainerFactoryService factoryService = mock(ParameterizedListenerContainerFactoryService.class);
        when(factoryService.createRecordListenerContainerFactory(eq(KafkaAltinnInstance.class), any(Consumer.class),
                any(ListenerConfiguration.class), any(CommonErrorHandler.class), any(Consumer.class)))
                .thenReturn(mock(ParameterizedListenerContainerFactory.class));
        consumer.altinnInstanceConsumerConfiguration(factoryService, new ErrorHandlerFactory());

        ArgumentCaptor<Consumer<ConsumerRecord<String, KafkaAltinnInstance>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(factoryService).createRecordListenerContainerFactory(eq(KafkaAltinnInstance.class), captor.capture(),
                any(ListenerConfiguration.class), any(CommonErrorHandler.class), any(Consumer.class));
        listener = captor.getValue();
    }

    @Test
    void recordIsProcessedAsSourceApplicationFive() throws Exception {
        KafkaAltinnInstance instance = InstanceMappingGoldenTest.fullInstance("vigo/drosjeloyve");
        when(instanceProcessor.processInstance(anyLong(), eq(instance))).thenReturn(ResponseEntity.accepted().build());

        listener.accept(new ConsumerRecord<>("ofk-no.altinn.event.instance-received", 0, 0, instance.getInstanceId(), instance));

        ArgumentCaptor<Long> sourceApplicationId = ArgumentCaptor.forClass(Long.class);
        verify(instanceProcessor).processInstance(sourceApplicationId.capture(), eq(instance));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceApplicationId", sourceApplicationId.getValue());
        result.put("sourceApplicationInstanceId", instance.getInstanceId());
        assertMatchesGolden("altinn-consumer-processed", result);
    }

    @Test
    void processingErrorIsRethrownToTheContainer() throws Exception {
        KafkaAltinnInstance instance = InstanceMappingGoldenTest.fullInstance("vigo/drosjeloyve");
        when(instanceProcessor.processInstance(anyLong(), eq(instance)))
                .thenThrow(new IllegalStateException("intgr-conf-service unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                listener.accept(new ConsumerRecord<>("ofk-no.altinn.event.instance-received", 0, 0, instance.getInstanceId(), instance)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exception", exception.getClass().getName());
        result.put("message", exception.getMessage());
        assertMatchesGolden("altinn-consumer-error", result);
    }
}
