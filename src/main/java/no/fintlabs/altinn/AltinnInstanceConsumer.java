package no.fintlabs.altinn;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.kafka.entity.EntityConsumerFactoryService;
import no.fintlabs.kafka.entity.topic.EntityTopicNameParameters;
import no.fintlabs.kafka.entity.topic.EntityTopicService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AltinnInstanceConsumer {

    private final EntityTopicService entityTopicService;
    private final EntityTopicNameParameters entityTopicNameParameters;

    public AltinnInstanceConsumer(EntityTopicService entityTopicService) {
        this.entityTopicService = entityTopicService;

        this.entityTopicNameParameters = EntityTopicNameParameters.builder()
                .resource("instance-received")
                .orgId("fintlabs-no")
                .domainContext("altinn")
                .build();

        entityTopicService.ensureTopic(entityTopicNameParameters, 0);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, KafkaAltinnInstance> altinnInstanceConsumerConfiguration(
            EntityConsumerFactoryService entityConsumerFactoryService) {


        return entityConsumerFactoryService
                .createRecordConsumerFactory(KafkaAltinnInstance.class, this::process)
                .createContainer(entityTopicNameParameters);
    }

    private void process(ConsumerRecord<String, KafkaAltinnInstance> altinnInstanceRecord) {
        log.info("Congratulations! 🎉 You received a new instance with instanceId {} from organizationName {} in county {}",
                altinnInstanceRecord.value().getInstanceId(),
                altinnInstanceRecord.value().getOrganizationName(),
                altinnInstanceRecord.value().getCountyName());
    }
}
