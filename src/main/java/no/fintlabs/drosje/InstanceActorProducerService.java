package no.fintlabs.drosje;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaEvidenceConsentRequest;
import no.fintlabs.kafka.event.EventProducer;
import no.fintlabs.kafka.event.EventProducerFactory;
import no.fintlabs.kafka.event.EventProducerRecord;
import no.fintlabs.kafka.event.topic.EventTopicNameParameters;
import no.fintlabs.kafka.event.topic.EventTopicService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InstanceActorProducerService {

    private final EventProducer<KafkaEvidenceConsentRequest> eventProducer;
    private final EventTopicNameParameters topicNameParameters;

    public InstanceActorProducerService(EventProducerFactory eventProducerFactory,
                                        EventTopicService eventTopicService){

        eventProducer = eventProducerFactory.createProducer(KafkaEvidenceConsentRequest.class);

        this.topicNameParameters = EventTopicNameParameters.builder()
                .orgId("altinn").domainContext("ebevis").eventName("consent-request")
                .build();

        eventTopicService.ensureTopic(topicNameParameters, 0);
    }

    public void publish(KafkaEvidenceConsentRequest instanceActor) {
        log.info("TopicNameParameters: {}", topicNameParameters);
        log.info("Publishing instance actor: {}", instanceActor);
        eventProducer.send(
                EventProducerRecord.<KafkaEvidenceConsentRequest>builder()
                        .topicNameParameters(topicNameParameters)
                        .key(instanceActor.getAltinnReference())
                        .value(instanceActor)
                        .build()
        );
    }
}
