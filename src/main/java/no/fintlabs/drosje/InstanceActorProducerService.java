package no.fintlabs.drosje;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.kafka.event.EventProducer;
import no.fintlabs.kafka.event.EventProducerFactory;
import no.fintlabs.kafka.event.EventProducerRecord;
import no.fintlabs.kafka.event.topic.EventTopicNameParameters;
import no.fintlabs.kafka.event.topic.EventTopicService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InstanceActorProducerService {

    private final EventProducer<KafkaInstanceActor> eventProducer;
    private final EventTopicNameParameters topicNameParameters;

    public InstanceActorProducerService(EventProducerFactory eventProducerFactory,
                                        @Value("${fint.org-id}") String orgId,
                                        EventTopicService eventTopicService){

        eventProducer = eventProducerFactory.createProducer(KafkaInstanceActor.class);

        this.topicNameParameters = EventTopicNameParameters.builder()
                .orgId(orgId).domainContext("drosje").eventName("instance-actor")
                .build();

        eventTopicService.ensureTopic(topicNameParameters, 0);
    }

    public void publish(KafkaInstanceActor instanceActor) {
        log.info("TopicNameParameters: {}", topicNameParameters);
        log.info("Publishing instance actor: {}", instanceActor);
        eventProducer.send(
                EventProducerRecord.<KafkaInstanceActor>builder()
                        .key(instanceActor.getAltinnReference())
                        .value(instanceActor)
                        .build()
        );
    }


}
