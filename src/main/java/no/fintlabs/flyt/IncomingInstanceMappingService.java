package no.fintlabs.flyt;

import kotlin.jvm.functions.Function1;
import lombok.extern.slf4j.Slf4j;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.novari.flyt.gateway.instance.InstanceMapper;
import no.novari.flyt.gateway.instance.model.File;
import no.novari.flyt.gateway.instance.model.instance.InstanceObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    @NotNull
    @Override
    public InstanceObject map(long sourceApplicationId, KafkaAltinnInstance incomingInstance, @NotNull Function1<? super File, UUID> persistFile) {
        log.info("Mapping incoming instance: {}, sourceApplicationId={}, appId={}", incomingInstance, sourceApplicationId, incomingInstance.getAppId());
        ApplicationInstanceMapper mapper = appMappers.get(incomingInstance.getAppId());
        if (mapper == null) {
            throw new IllegalArgumentException("Unknown app id: " + incomingInstance.getAppId());
        }
        // The mappers fetch files in parallel, but the starter collects the file IDs in a list that is not thread safe.
        Object persistLock = new Object();
        return mapper.map(sourceApplicationId, incomingInstance, file -> Mono.fromCallable(() -> {
                    synchronized (persistLock) {
                        return persistFile.invoke(file);
                    }
                }).subscribeOn(Schedulers.boundedElastic()))
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("No instance mapped for app id: " + incomingInstance.getAppId()));
    }
}
