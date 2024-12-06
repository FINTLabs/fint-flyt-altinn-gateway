package no.fintlabs.flyt;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IncomingInstanceMappingService implements InstanceMapper<KafkaAltinnInstance> {

    public static final String EMPTY_STRING = "";

    @Override
    public Mono<InstanceObject> map(
            Long sourceApplicationId,
            KafkaAltinnInstance incomingInstance,
            Function<File, Mono<UUID>> persistFile
    ) {
        log.info("Mapping incoming instance: {}, sourceApplicationId={}", incomingInstance, sourceApplicationId);

        return Mono.just(InstanceObject.builder()
                .valuePerKey(toValuePerKey(incomingInstance))
                .build());
    }

    private static Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance) {
        Set<Map.Entry<String, String>> entries = new HashSet<>();

        entries.add(Map.entry("virksomhetOrganisasjonsnummer", incomingInstance.getOrganizationNumber()));

        entries.add(Map.entry("virksomhetOrganisasjonsnavn", incomingInstance.getOrganizationName()));
        entries.add(Map.entry("virksomhetEpostadresse", incomingInstance.getCompanyEmail()));
        entries.add(Map.entry("virksomhetTelefonnummer", Optional.ofNullable(incomingInstance.getCompanyPhone()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("virksomhetFylke", incomingInstance.getCountyName()));
        entries.add(Map.entry("virksomhetKommune", Optional.ofNullable(incomingInstance.getMunicipalityName()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("virksomhetGateadresse", incomingInstance.getCompanyAdressStreet()));
        entries.add(Map.entry("virksomhetPostnummer", incomingInstance.getCompanyAdressPostcode()));
        entries.add(Map.entry("virksomhetPoststed", incomingInstance.getCompanyAdressPostplace()));

        entries.add(Map.entry("postadresseGateadresse", Optional.ofNullable(incomingInstance.getPostalAdressStreet()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("postadressePostnummer", Optional.ofNullable(incomingInstance.getPostalAdressPostcode()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("postadressePoststed", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));

        entries.add(Map.entry("dagligLederFødselsnummer", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederFornavn", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederEtternavn", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederEpostadresse", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederTelefonnummer", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));

        return entries.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
