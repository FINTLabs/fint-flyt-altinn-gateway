package no.fintlabs.flyt;

import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.novari.flyt.gateway.instance.InstanceProcessor;
import no.novari.flyt.gateway.instance.InstanceProcessorFactoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceProcessorConfiguration {

    @Bean
    public InstanceProcessor<KafkaAltinnInstance> incomingInstanceProcessor(
            InstanceProcessorFactoryService instanceProcessorFactoryService,
            IncomingInstanceMappingService incomingInstanceMappingService
    ) {
        return instanceProcessorFactoryService.createInstanceProcessor(
                incomingInstance -> incomingInstance.getAppId().replace("vigo/", "").toUpperCase(),
                KafkaAltinnInstance::getInstanceId,
                incomingInstanceMappingService
        );
    }
}
