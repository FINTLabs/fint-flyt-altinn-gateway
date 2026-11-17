package no.fintlabs.altinn;

import lombok.extern.slf4j.Slf4j;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.novari.flyt.gateway.instance.InstanceProcessor;
import no.novari.kafka.consuming.ErrorHandlerConfiguration;
import no.novari.kafka.consuming.ErrorHandlerFactory;
import no.novari.kafka.consuming.ListenerConfiguration;
import no.novari.kafka.consuming.ParameterizedListenerContainerFactoryService;
import no.novari.kafka.topic.EventTopicService;
import no.novari.kafka.topic.configuration.EventCleanupFrequency;
import no.novari.kafka.topic.configuration.EventTopicConfiguration;
import no.novari.kafka.topic.name.EventTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class AltinnInstanceConsumer {

    private static final long SOURCE_APPLICATION_ID = 5L;

    private final EventTopicNameParameters topicNameParameters;
    private final InstanceProcessor<KafkaAltinnInstance> instanceProcessor;

    public AltinnInstanceConsumer(EventTopicService entityTopicService,
                                  @Value("${fint.org-id}") String orgId,
                                  InstanceProcessor<KafkaAltinnInstance> instanceProcessor) {

        this.instanceProcessor = instanceProcessor;

        this.topicNameParameters = EventTopicNameParameters.builder()
                .topicNamePrefixParameters(TopicNamePrefixParameters.stepBuilder()
                        .orgId(orgId)
                        .domainContext("altinn")
                        .build())
                .eventName("instance-received")
                .build();

        // Same as ensureTopic(..., 0) in fint-kafka 4: its defaults were 1 partition, 1 day retention and delete.
        entityTopicService.createOrModifyTopic(topicNameParameters, EventTopicConfiguration.stepBuilder()
                .partitions(1)
                .retentionTime(Duration.ofDays(1))
                .cleanupFrequency(EventCleanupFrequency.NORMAL)
                .build());
    }

    private void process(ConsumerRecord<String, KafkaAltinnInstance> altinnInstanceRecord) {
        try {
            log.info("Congratulations! 🎉 You received a new instance with instanceId {} from organizationName {} in county {}",
                    altinnInstanceRecord.value().getInstanceId(),
                    altinnInstanceRecord.value().getOrganizationName(),
                    altinnInstanceRecord.value().getCountyName());


            // Send til FLYT -> Arkiv:
            instanceProcessor.processInstance(SOURCE_APPLICATION_ID, altinnInstanceRecord.value());

        } catch (Exception e) {
            log.error("Error processing Altinn instance with instanceId {}: {}",
                    altinnInstanceRecord.value().getInstanceId(),
                    e.getMessage(), e);
            throw e;
        }
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, KafkaAltinnInstance> altinnInstanceConsumerConfiguration(
            ParameterizedListenerContainerFactoryService listenerContainerFactoryService,
            ErrorHandlerFactory errorHandlerFactory) {

        log.info("Creating consumer with domainContext {}, orgId {}, and resource {}",
                topicNameParameters.getTopicNamePrefixParameters().getDomainContext(),
                topicNameParameters.getTopicNamePrefixParameters().getOrgId(),
                topicNameParameters.getEventName());

        return listenerContainerFactoryService.createRecordListenerContainerFactory(
                        KafkaAltinnInstance.class,
                        this::process,
                        ListenerConfiguration.stepBuilder()
                                .groupIdApplicationDefault()
                                .maxPollRecordsKafkaDefault()
                                .maxPollIntervalKafkaDefault()
                                .continueFromPreviousOffsetOnAssignment()
                                .build(),
                        // Same as the spring-kafka default that fint-kafka 4 used: 10 attempts, then skip.
                        errorHandlerFactory.createErrorHandler(ErrorHandlerConfiguration.<KafkaAltinnInstance>stepBuilder()
                                .retryWithFixedInterval(Duration.ZERO, 9)
                                .useDefaultRetryClassification()
                                .continueRetryOnExceptionChange()
                                .skipFailedRecords()
                                .build()),
                        container -> container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD)
                )
                .createContainer(topicNameParameters);
    }

}