package no.fintlabs.flyt;

import lombok.extern.slf4j.Slf4j;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IncomingInstanceMappingService implements InstanceMapper<KafkaAltinnInstance> {

    private final Map<String, ApplicationInstanceMapper> appMappers;

    public IncomingInstanceMappingService(List<ApplicationInstanceMapper> appMappers) {
        this.appMappers = appMappers.stream()
                .collect(Collectors.toMap(ApplicationInstanceMapper::appId, Function.identity()));
    }

    @Override
    public Mono<InstanceObject> map(Long sourceApplicationId, KafkaAltinnInstance incomingInstance, Function<File, Mono<UUID>> persistFile) {
        log.info("Mapping incoming instance: {}, sourceApplicationId={}, appId={}", incomingInstance, sourceApplicationId, incomingInstance.getAppId());
        ApplicationInstanceMapper mapper = appMappers.get(incomingInstance.getAppId());
        if (mapper == null) {
            return Mono.error(new IllegalArgumentException("Unknown app id: " + incomingInstance.getAppId()));
        }
        return mapper.map(sourceApplicationId, incomingInstance, persistFile);
    }
}
