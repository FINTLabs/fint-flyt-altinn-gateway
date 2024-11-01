package no.fintlabs.instance.gateway.example.simple;

import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class IncomingInstanceMappingService implements InstanceMapper<IncomingInstance> {
    @Override
    public Mono<InstanceObject> map(
            Long sourceApplicationId,
            IncomingInstance incomingInstance,
            Function<File, Mono<UUID>> persistFile
    ) {
        Map<String, String> valuePerKey = getStringStringMap(incomingInstance);
        return Mono.just(
                InstanceObject.builder()
                        .valuePerKey(valuePerKey)
                        .build()
        );
    }

    private static Map<String, String> getStringStringMap(IncomingInstance incomingInstance) {
        Map<String, String> valuePerKey = new HashMap<>();
        valuePerKey.put("exampleIntegerValue1Key", String.valueOf(incomingInstance.getIntegerValue1()));
        valuePerKey.put("exampleStringValue1Key", incomingInstance.getStringValue1());
        valuePerKey.put("exampleStringValue2Key", incomingInstance.getStringValue2());
        valuePerKey.put("exampleStringValue3Key", incomingInstance.getStringValue3());
        return valuePerKey;
    }

}
