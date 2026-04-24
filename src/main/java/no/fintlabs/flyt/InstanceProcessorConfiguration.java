package no.fintlabs.flyt;

import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.InstanceProcessor;
import no.fintlabs.gateway.instance.InstanceProcessorFactoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class InstanceProcessorConfiguration {

    @Bean
    public InstanceProcessor<KafkaAltinnInstance> incomingInstanceProcessor(
            InstanceProcessorFactoryService instanceProcessorFactoryService,
            IncomingInstanceMappingService incomingInstanceMappingService
    ) {
        return instanceProcessorFactoryService.createInstanceProcessor(
                incomingInstance -> Optional.of(incomingInstance.getAppId().replace("vigo/", "").toUpperCase()),
                incomingInstance -> Optional.ofNullable(incomingInstance.getInstanceId()),
                incomingInstanceMappingService
        );
    }
}
