package no.fintlabs.flyt;

import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

public interface ApplicationInstanceMapper {
    String appId();

    Mono<InstanceObject> map(Long sourceApplicationId,
                             KafkaAltinnInstance incomingInstance,
                             Function<File, Mono<UUID>> persistFile);
}
